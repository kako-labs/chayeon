package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** One lock covers time transitions, validation, mutation, persistence and publication. */
class ChayeonEngine(
    private val rules: SignatureRules,
    private val clock: EngineClock = EngineClock(System::currentTimeMillis),
    private val idGenerator: EngineIdGenerator = EngineIdGenerator { UUID.randomUUID().toString() },
    private val store: EngineStore = MemoryEngineStore(),
    val policy: EnginePolicy = EnginePolicy(),
    private val roomId: String = "local",
    private val demoEnabled: Boolean = true,
) {
    private val lock = Any()
    private val json = EngineJson
    private var state = store.load() ?: EngineState(lastTick = clock.now())
    private var locations = mutableMapOf<String, LocationInput>()
    private val mutableRevision = MutableStateFlow(state.revision)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    init {
        synchronized(lock) {
            val room = working()
            room.seedDemo()
            room.advance(clock.now())
            commit(room)
        }
    }

    fun exportState(): EngineState = synchronized(lock) { state }

    fun tick(now: Long = clock.now()) = synchronized(lock) {
        val room = working()
        room.advance(now)
        commit(room)
    }

    fun snapshotFor(actor: String): AppResult<SyncSnapshot> = synchronized(lock) {
        try {
            tick()
            AppResult.Success(project(working().snapshot(actor), actor))
        } catch (e: Rejected) { AppResult.Failure(e.error) }
        catch (_: kotlinx.serialization.SerializationException) { AppResult.Failure(AppError.Restricted) }
        catch (_: Exception) { AppResult.Failure(AppError.Unknown) }
    }

    private fun working() = Room(state, locations.toMutableMap(), rules, policy, idGenerator, roomId, demoEnabled, state.lastTick)

    private fun commit(room: Room) {
        room.updateViews()
        if (room.state != state) store.save(room.state)
        state = room.state
        locations = room.locations
        mutableRevision.value = state.revision
    }

    private inline fun <reified R, reified T> command(
        actor: String, meta: CommandMeta, request: R, enter: Boolean = false, action: Room.() -> T,
    ): AppResult<T> = synchronized(lock) {
        try {
            tick()
            val room = working()
            room.expectedRevision = state.viewRevisions[actor] ?: 0
            ensure(room.validIdentifier(meta.commandId))
            val digest = MessageDigest.getInstance("SHA-256").digest(
                (kotlinx.serialization.serializer<R>().descriptor.serialName + json.encodeToString(request)).toByteArray()
            ).joinToString("") { "%02x".format(it) }
            val cached = state.commands.firstOrNull { it.actor == actor && it.commandId == meta.commandId }
            if (cached != null) {
                ensure(cached.digest == digest)
                return@synchronized json.decodeFromJsonElement<AppResult<T>>(refreshCached(cached.result, actor, room))
            }
            state.retiredCommands[commandKey(actor, meta.commandId)]?.let { retiredDigest ->
                ensure(retiredDigest == digest)
                reject(AppError.AlreadyClosed)
            }
            if (!enter) room.account(actor)
            val result: AppResult<T> = try { AppResult.Success(room.action()) }
                catch (e: Rejected) { return@synchronized rememberFailure(actor, meta, digest, e.error) }
            room.updateViews()
            val projected = json.decodeFromJsonElement<AppResult<T>>(withVersion(json.encodeToJsonElement(result), room.state.viewRevisions[actor] ?: 0))
            cache(room, CachedCommand(actor, meta.commandId, digest, json.encodeToJsonElement(projected)))
            commit(room)
            projected
        } catch (e: Rejected) { AppResult.Failure(e.error) }
        catch (_: kotlinx.serialization.SerializationException) { AppResult.Failure(AppError.Restricted) }
        catch (_: Exception) { AppResult.Failure(AppError.Unknown) }
    }

    private fun rememberFailure(actor: String, meta: CommandMeta, digest: String, error: AppError): AppResult.Failure {
        val result = AppResult.Failure(error)
        val room = working()
        cache(room, CachedCommand(actor, meta.commandId, digest, json.encodeToJsonElement<AppResult<CommandReceipt>>(result)))
        commit(room)
        return result
    }

    private fun commandKey(actor: String, commandId: String): String = MessageDigest.getInstance("SHA-256")
        .digest((actor.length.toString() + ":" + actor + commandId).toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun cache(room: Room, command: CachedCommand) {
        val all = room.state.commands + command
        val retired = all.dropLast(policy.maximumCachedCommands).associate { commandKey(it.actor, it.commandId) to it.digest }
        room.state = room.state.copy(commands = all.takeLast(policy.maximumCachedCommands),
            retiredCommands = room.state.retiredCommands + retired)
    }

    /** Retry identity is stable; revoked public information is never replayed from the cache. */
    private fun refreshCached(result: JsonElement, actor: String, room: Room): JsonElement {
        val wrapper = result as? JsonObject ?: return result
        val value = wrapper["value"] as? JsonObject ?: return result
        if ("candidates" in value) {
            val discovery = json.encodeToJsonElement(room.discovery(actor)).jsonObject
            return withVersion(JsonObject(wrapper + ("value" to JsonObject(discovery + value.filterKeys { it == "type" }))), state.viewRevisions[actor] ?: 0)
        }
        if ("afterRequestId" in value) {
            val id = value["id"]?.jsonPrimitive?.content
            val connection = room.state.afterConnections[id] ?: return result
            val projection = json.encodeToJsonElement(room.afterConnection(connection, actor)).jsonObject
            val refreshed = JsonObject(projection + value.filterKeys { it == "type" })
            return withVersion(JsonObject(wrapper + ("value" to refreshed)), state.viewRevisions[actor] ?: 0)
        }
        val fields = value.toMutableMap()
        if ("contacts" in value) fields["contacts"] = json.encodeToJsonElement(room.account(actor).profile.contacts)
        val peer = value["peer"] as? JsonObject
        val own = value["publicProfile"] as? JsonObject
        if (peer != null) {
            val owner = state.accounts.values.firstOrNull { it.profile.id == peer["id"]?.jsonPrimitive?.content }?.id ?: return result
            fields["peer"] = withVersion(json.encodeToJsonElement(room.publicProfile(owner, actor)), state.viewRevisions[actor] ?: 0)
            if ("chemistry" in value) fields["chemistry"] = json.encodeToJsonElement(room.chemistry(actor, owner))
        }
        if (own != null && actor in state.accounts) fields["publicProfile"] = withVersion(json.encodeToJsonElement(room.publicProfile(actor, actor)), state.viewRevisions[actor] ?: 0)
        return JsonObject(wrapper + ("value" to JsonObject(fields)))
    }

    private inline fun <reified T> project(value: T, actor: String): T =
        json.decodeFromJsonElement(withVersion(json.encodeToJsonElement(value), state.viewRevisions[actor] ?: 0))

    fun enterSession(actor: String, request: EnterSessionRequest): AppResult<Session> =
        command(actor, request.meta, request, enter = true) { enterSession(actor, request) }

    fun signOut(actor: String, request: SignOutRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { signOut(actor, request) }

    fun deleteAccount(actor: String, request: DeleteAccountRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { deleteAccount(actor, request) }

    fun saveProfile(actor: String, request: SaveProfileRequest): AppResult<MyProfile> =
        command(actor, request.meta, request) { saveProfile(actor, request) }

    fun recordConsent(actor: String, request: RecordConsentRequest): AppResult<ParticipationSettings> =
        command(actor, request.meta, request) { recordConsent(actor, request) }

    fun updateSettings(actor: String, request: UpdateSettingsRequest): AppResult<ParticipationSettings> =
        command(actor, request.meta, request) { updateSettings(actor, request) }

    fun publishSignature(actor: String, request: PublishSignatureRequest): AppResult<MyProfile> =
        command(actor, request.meta, request) { publishSignature(actor, request) }

    fun setDiscovery(actor: String, request: SetDiscoveryRequest): AppResult<DiscoveryState> =
        command(actor, request.meta, request) { setDiscovery(actor, request) }

    fun refreshDiscovery(actor: String, request: RefreshDiscoveryRequest): AppResult<DiscoveryState> =
        command(actor, request.meta, request) { refreshDiscovery(actor, request) }

    fun updateLocation(actor: String, request: UpdateLocationRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { updateLocation(actor, request) }

    fun setSyntheticPresence(actor: String, request: SetSyntheticPresenceRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { setSyntheticPresence(actor, request) }

    fun requestConnection(actor: String, request: RequestConnectionRequest): AppResult<ConnectionRequest> =
        command(actor, request.meta, request) { requestConnection(actor, request) }

    fun respondConnection(actor: String, request: RespondConnectionRequest): AppResult<ConnectionRequest> =
        command(actor, request.meta, request) { respondConnection(actor, request) }

    fun cancelConnection(actor: String, request: CancelConnectionRequest): AppResult<ConnectionRequest> =
        command(actor, request.meta, request) { cancelConnection(actor, request) }


    fun leaveConnection(actor: String, request: LeaveConnectionRequest): AppResult<Connection> =
        command(actor, request.meta, request) { leaveConnection(actor, request) }

    fun deleteEncounter(actor: String, request: DeleteEncounterRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { deleteEncounter(actor, request) }

    fun deleteAllEncounters(actor: String, request: DeleteAllEncountersRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { deleteAllEncounters(actor, request) }

    fun requestAfter(actor: String, request: RequestAfterRequest): AppResult<AfterRequest> =
        command(actor, request.meta, request) { requestAfter(actor, request) }

    fun respondAfter(actor: String, request: RespondAfterRequest): AppResult<AfterRequest> =
        command(actor, request.meta, request) { respondAfter(actor, request) }

    fun withdrawAfter(actor: String, request: WithdrawAfterRequest): AppResult<AfterRequest> =
        command(actor, request.meta, request) { withdrawAfter(actor, request) }


    fun endAfterConnection(actor: String, request: EndAfterConnectionRequest): AppResult<AfterConnection> =
        command(actor, request.meta, request) { endAfterConnection(actor, request) }

    fun blockPeer(actor: String, request: BlockPeerRequest): AppResult<Block> =
        command(actor, request.meta, request) { blockPeer(actor, request) }

    fun unblockPeer(actor: String, request: UnblockPeerRequest): AppResult<CommandReceipt> =
        command(actor, request.meta, request) { unblockPeer(actor, request) }

    fun reportPeer(actor: String, request: ReportPeerRequest): AppResult<Report> =
        command(actor, request.meta, request) { reportPeer(actor, request) }
}
