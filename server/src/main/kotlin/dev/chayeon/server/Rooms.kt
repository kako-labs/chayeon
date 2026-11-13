package dev.chayeon.server

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

data class ServerPolicy(
    val maximumBodyBytes: Int = 32_768,
    val maximumRooms: Int = 64,
    val idleRoomMillis: Long = 1_800_000,
    val pollTimeoutMillis: Long = 25_000,
    val tickIntervalMillis: Long = 250,
    val maximumRetiredReceipts: Int = 512,
    val retiredReceiptTtlMillis: Long = 300_000,
) {
    init {
        require(maximumBodyBytes > 0 && maximumRooms > 0 && idleRoomMillis > 0)
        require(pollTimeoutMillis in 1..25_000 && tickIntervalMillis > 0)
        require(maximumRetiredReceipts > 0 && retiredReceiptTtlMillis > 0)
    }
}

internal class HostedRoom(val engine: ChayeonEngine, var lastAccess: Long) {
    val actors = mutableMapOf<PersonaCode, String>()
    val claims = mutableMapOf<PersonaCode, ByteArray>()
}

internal class Authorization(val roomCode: String, val actor: String, val sessionId: String) {
    override fun toString() = "Authorization(redacted)"
}

private class RetiredReceipt(val route: String, val digest: List<Byte>, val response: String, val expiresAt: Long) {
    override fun toString() = "RetiredReceipt(redacted)"
}

/** The registry never receives location inputs and never logs credentials or room contents. */
class RoomRegistry(
    private val rules: SignatureRules,
    val policy: ServerPolicy = ServerPolicy(),
    private val clock: EngineClock = EngineClock(System::currentTimeMillis),
    private val enginePolicy: EnginePolicy = EnginePolicy(),
    private val demoEnabled: Boolean = true,
    private val voiceStateReader: (ChayeonEngine) -> EngineState = ChayeonEngine::exportState,
) {
    private val rooms = mutableMapOf<String, HostedRoom>()
    private val tokens = mutableMapOf<String, Authorization>()
    private val random = SecureRandom()
    private val retired = linkedMapOf<String, RetiredReceipt>()
    private val voice = VoiceRelay(::voiceIdentity)

    @Synchronized
    fun enter(request: EnterSessionRequest): AppResult<Session> {
        if (request.mode != SessionMode.SERVER_DEMO || request.roomCode?.matches(Regex("[A-Za-z0-9_-]{4,32}")) != true)
            return AppResult.Failure(AppError.Restricted)
        val secret = request.clientSecret
        if (secret == null || secret.length !in 32..256) return AppResult.Failure(AppError.NotAuthorized)
        val secretHash = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
        expireIdle()
        val code = request.roomCode!!
        val room = rooms[code] ?: run {
            if (rooms.size >= policy.maximumRooms) return AppResult.Failure(AppError.Restricted)
            HostedRoom(ChayeonEngine(rules, clock = clock, policy = enginePolicy, roomId = code, demoEnabled = demoEnabled), clock.now())
                .also { rooms[code] = it }
        }
        val claim = room.claims[request.persona]
        if (claim != null && !MessageDigest.isEqual(claim, secretHash)) return AppResult.Failure(AppError.NotAuthorized)
        val actor = room.actors[request.persona] ?: UUID.randomUUID().toString()
        val result = room.engine.enterSession(actor, request)
        if (result !is AppResult.Success) return result
        room.claims[request.persona] = secretHash
        room.actors[request.persona] = actor
        room.lastAccess = clock.now()
        val session = result.value
        val current = room.engine.snapshotFor(actor)
        if (current !is AppResult.Success || current.value.id != session.id) return AppResult.Failure(AppError.AlreadyClosed)
        val existing = tokens.entries.firstOrNull { it.value.roomCode == code && it.value.actor == actor && it.value.sessionId == session.id }?.key
        val token = existing ?: Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        tokens.entries.removeAll { it.value.roomCode == code && it.value.actor == actor && it.key != token }
        tokens[token] = Authorization(code, actor, session.id)
        voice.prune()
        return AppResult.Success(session.copy(accessToken = token))
    }

    @Synchronized
    internal fun authorize(header: String?): Pair<Authorization, ChayeonEngine>? {
        if (header == null || !header.startsWith("Bearer ")) return null
        val identity = tokens[header.removePrefix("Bearer ")] ?: return null
        val room = rooms[identity.roomCode] ?: return null
        if (clock.now() - room.lastAccess >= policy.idleRoomMillis) {
            expireIdle()
            return null
        }
        val snapshot = room.engine.snapshotFor(identity.actor)
        if (snapshot !is AppResult.Success || snapshot.value.id != identity.sessionId) return null
        room.lastAccess = clock.now()
        return identity to room.engine
    }

    @Synchronized
    internal fun revoke(identity: Authorization, deleted: Boolean) {
        tokens.entries.removeAll { it.value.roomCode == identity.roomCode && it.value.actor == identity.actor }
        if (deleted) rooms[identity.roomCode]?.let { room ->
            val personas = room.actors.filterValues { it == identity.actor }.keys
            personas.forEach { room.actors.remove(it); room.claims.remove(it) }
        }
        voice.prune()
    }

    @Synchronized
    internal fun execute(header: String?, route: String, body: String): String? {
        val auth = authorize(header)
        if (auth == null) {
            val receipt = retired[header?.removePrefix("Bearer ")] ?: return null
            return receipt.response.takeIf { receipt.route == route && receipt.expiresAt > clock.now() && receipt.digest == digest(body) }
        }
        val (identity, engine) = auth
        val response = EngineApi(engine).execute(identity.actor, route, body)
        if (route == ApiRoutes.SIGN_OUT || route == ApiRoutes.DELETE_ACCOUNT) {
            val receipt = EngineJson.decodeFromString(SignOutResponse.serializer(), response)
            if (receipt.result is AppResult.Success && engine.exportState().accounts[identity.actor]?.active == false) {
                retired[header!!.removePrefix("Bearer ")] = RetiredReceipt(route, digest(body), response, clock.now() + policy.retiredReceiptTtlMillis)
                while (retired.size > policy.maximumRetiredReceipts) retired.remove(retired.keys.first())
                revoke(identity, route == ApiRoutes.DELETE_ACCOUNT)
            }
        }
        voice.prune()
        return response
    }

    @Synchronized
    internal fun canExecute(header: String?, route: String): Boolean {
        if (authorize(header) != null) return true
        if (header == null || !header.startsWith("Bearer ")) return false
        val receipt = retired[header.removePrefix("Bearer ")] ?: return false
        return receipt.route == route && receipt.expiresAt > clock.now()
    }

    private fun digest(body: String): List<Byte> = MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).toList()

    @Synchronized
    internal fun snapshot(header: String?): AppResult<SyncSnapshot>? {
        val (identity, engine) = authorize(header) ?: return null
        return engine.snapshotFor(identity.actor)
    }

    @Synchronized
    fun tick() {
        expireIdle()
        rooms.values.forEach { it.engine.tick(clock.now()) }
        voice.prune()
    }

    @Synchronized
    internal fun voiceAllowed(header: String?, connectionId: String): Boolean {
        val (_, engine) = authorize(header) ?: return false
        engine.tick()
        voice.prune()
        return header != null && voiceIdentity(header, connectionId) != null
    }

    @Synchronized
    internal fun joinVoice(header: String?, connectionId: String): VoiceSocket? =
        if (voiceAllowed(header, connectionId)) voice.join(header!!, connectionId) else null

    @Synchronized
    internal fun relayVoice(socket: VoiceSocket, frame: ByteArray): Boolean = voice.relay(socket, frame)

    @Synchronized
    internal fun leaveVoice(socket: VoiceSocket) = voice.leave(socket)

    @Synchronized
    internal fun pruneVoice() = voice.prune()

    private fun voiceIdentity(header: String, connectionId: String): VoiceIdentity? {
        if (!header.startsWith("Bearer ")) return null
        val identity = tokens[header.removePrefix("Bearer ")] ?: return null
        val room = rooms[identity.roomCode] ?: return null
        if (clock.now() - room.lastAccess >= policy.idleRoomMillis) return null
        val state = voiceStateReader(room.engine)
        val account = state.accounts[identity.actor] ?: return null
        if (!account.active || account.deleted || account.session.id != identity.sessionId) return null
        val connection = state.connections[connectionId] ?: return null
        if (connection.status != ConnectionStatus.CONNECTED || identity.actor !in setOf(connection.first, connection.second)) return null
        val participants = setOf(connection.first, connection.second)
        if (participants.any { state.accounts[it]?.let { a -> !a.active || a.deleted } != false }) return null
        if (state.blocks.values.any { it.owner in participants && it.peer in participants }) return null
        room.lastAccess = clock.now()
        return VoiceIdentity(identity.roomCode, connectionId, identity.actor)
    }

    private fun expireIdle() {
        retired.entries.removeAll { it.value.expiresAt <= clock.now() }
        val expired = rooms.filterValues { clock.now() - it.lastAccess >= policy.idleRoomMillis }.keys
        expired.forEach(rooms::remove)
        tokens.entries.removeAll { it.value.roomCode in expired }
    }
}
