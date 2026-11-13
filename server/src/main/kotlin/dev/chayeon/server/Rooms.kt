package dev.chayeon.server

import dev.chayeon.core.engine.*
import dev.chayeon.core.model.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

data class ServerPolicy(
    val roomCapacity: Int = 50,
    val dataDirectory: java.nio.file.Path? = null,
    val trustProxy: Boolean = false,
    /** Header the trusted proxy overwrites with the client address, for example `CF-Connecting-IP` behind a Cloudflare tunnel. */
    val clientIpHeader: String = "X-Chayeon-Client-IP",
    val maximumBodyBytes: Int = 32_768,
    val maximumRooms: Int = 64,
    val idleRoomMillis: Long = 1_800_000,
    val pollTimeoutMillis: Long = 25_000,
    val tickIntervalMillis: Long = 250,
    val maximumRetiredReceipts: Int = 512,
    val retiredReceiptTtlMillis: Long = 300_000,
    val allowedRooms: Set<String> = setOf("default"),
) {
    init {
        require(roomCapacity > 0)
        require(maximumBodyBytes > 0 && maximumRooms > 0 && idleRoomMillis > 0)
        require(pollTimeoutMillis in 1..25_000 && tickIntervalMillis > 0)
        require(maximumRetiredReceipts > 0 && retiredReceiptTtlMillis > 0)
    }
}

internal class HostedRoom(val engine: ChayeonEngine, var lastAccess: Long)

internal class Authorization(val roomCode: String, val actor: String, val sessionId: String, var lastSeen: Long? = null) {
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
    internal val ipAdmission = Admission(clock::now)
    internal val actorAdmission = Admission(clock::now)
    private val rooms = mutableMapOf<String, HostedRoom>()
    private val tokens = mutableMapOf<String, Authorization>()
    private val retired = linkedMapOf<String, RetiredReceipt>()
    private val voice = VoiceRelay(::voiceIdentity)
    private val stores = mutableMapOf<String, FileEngineStore>()

    init {
        policy.dataDirectory?.let { directory ->
            java.nio.file.Files.createDirectories(directory)
            java.nio.file.Files.newDirectoryStream(directory, "*.json").use { paths ->
                paths.forEach { path ->
                    val code = path.fileName.toString().removeSuffix(".json")
                    require(code.matches(Regex("[A-Za-z0-9_-]{4,32}")))
                    if (roomAllowed(code)) {
                        require(rooms.size < policy.maximumRooms) { "Stored room limit exceeded" }
                        createRoom(code)
                    }
                }
            }
        }
    }

    private fun createRoom(code: String): HostedRoom {
        val store = policy.dataDirectory?.let { FileEngineStore(it.resolve("$code.json")) }
        val engine = ChayeonEngine(rules, clock = clock, policy = enginePolicy, roomId = code,
            demoEnabled = demoEnabled, store = store ?: MemoryEngineStore())
        val room = HostedRoom(engine, clock.now())
        rooms[code] = room
        if (store != null) {
            stores[code] = store
            store.tokens.forEach { (token, identity) -> tokens[token] = Authorization(code, identity.actor, identity.sessionId, identity.lastSeen) }
            engine.exportState().connections.values.filter { it.status != ConnectionStatus.ENDED }.forEach {
                val ended = engine.leaveConnection(it.first, LeaveConnectionRequest(CommandMeta(UUID.randomUUID().toString()), it.id))
                check(ended is AppResult.Success) { "Unable to close restored call" }
            }
        }
        return room
    }

    private fun persistTokens(code: String) {
        stores[code]?.saveTokens(tokens.filterValues { it.roomCode == code }.mapValues { StoredAuthorization(it.value.actor, it.value.sessionId, it.value.lastSeen) })
    }

    @Synchronized
    fun enter(request: EnterSessionRequest): AppResult<Session> {
        val code = request.roomCode ?: "default"
        if (request.mode != SessionMode.SERVER_DEMO || !code.matches(Regex("[A-Za-z0-9_-]{4,32}")) || !roomAllowed(code))
            return AppResult.Failure(AppError.Restricted)
        val secret = request.clientSecret
        if (secret == null || secret.length !in 32..256) return AppResult.Failure(AppError.NotAuthorized)
        val secretHash = MessageDigest.getInstance("SHA-256").digest(((request.roomCode ?: "default") + ":" + secret).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        expireIdle()
        val room = rooms[code] ?: run {
            if (rooms.size >= policy.maximumRooms) return AppResult.Failure(AppError.Restricted)
            createRoom(code)
        }
        val accounts = room.engine.exportState().accounts
        val actor = accounts.values.firstOrNull { !it.deleted && (it.id == secretHash || it.id.startsWith("$secretHash-")) }?.id
            ?: if (secretHash !in accounts) secretHash else "$secretHash-${UUID.randomUUID()}"
        if (!hasSeat(code, actor, accounts))
            return AppResult.Failure(AppError.Restricted)
        val result = room.engine.enterSession(actor, request.copy(roomCode = code))
        if (result !is AppResult.Success) return result
        room.lastAccess = clock.now()
        val session = result.value
        val current = room.engine.snapshotFor(actor)
        if (current !is AppResult.Success || current.value.id != session.id) return AppResult.Failure(AppError.AlreadyClosed)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$code:${session.id}".toByteArray(Charsets.UTF_8)))
        tokens.entries.removeAll { it.value.roomCode == code && it.value.actor == actor }
        tokens[tokenKey(token)] = Authorization(code, actor, session.id, clock.now())
        persistTokens(code)
        voice.prune()
        return AppResult.Success(session.copy(accessToken = token))
    }

    private fun roomAllowed(code: String) = "*" in policy.allowedRooms || code in policy.allowedRooms

    private fun hasSeat(code: String, actor: String, accounts: Map<String, EngineAccount>): Boolean {
        val now = clock.now()
        val seen = tokens.values.filter { it.roomCode == code && it.lastSeen?.let { time -> now - time < policy.idleRoomMillis } == true }
            .map { it.actor }.toSet()
        val active = accounts.values.filter { !it.deleted && it.active && !it.id.startsWith("demo-") && it.id in seen }
        return active.any { it.id == actor } || active.size < policy.roomCapacity
    }

    @Synchronized
    internal fun recognized(header: String?): Authorization? {
        if (header == null || !header.startsWith("Bearer ")) return null
        val identity = tokens[tokenKey(header.removePrefix("Bearer "))] ?: return null
        val account = rooms[identity.roomCode]?.engine?.exportState()?.accounts?.get(identity.actor) ?: return null
        return identity.takeIf { account.active && !account.deleted && account.session.id == identity.sessionId }
    }

    @Synchronized
    internal fun authorize(header: String?): Pair<Authorization, ChayeonEngine>? {
        val identity = recognized(header) ?: return null
        val room = rooms[identity.roomCode] ?: return null
        if (clock.now() - room.lastAccess >= policy.idleRoomMillis) {
            expireIdle()
            return null
        }
        if (!hasSeat(identity.roomCode, identity.actor, room.engine.exportState().accounts)) return null
        val snapshot = room.engine.snapshotFor(identity.actor)
        if (snapshot !is AppResult.Success || snapshot.value.id != identity.sessionId) return null
        room.lastAccess = clock.now()
        if (identity.lastSeen != room.lastAccess) {
            identity.lastSeen = room.lastAccess
            persistTokens(identity.roomCode)
        }
        return identity to room.engine
    }

    @Synchronized
    internal fun revoke(identity: Authorization) {
        tokens.entries.removeAll { it.value.roomCode == identity.roomCode && it.value.actor == identity.actor }
        persistTokens(identity.roomCode)
        voice.prune()
    }

    @Synchronized
    internal fun execute(header: String?, route: String, body: String): String? {
        val auth = authorize(header)
        if (auth == null) {
            val receipt = retired[header?.removePrefix("Bearer ")?.let(::tokenKey)] ?: return null
            return receipt.response.takeIf { receipt.route == route && receipt.expiresAt > clock.now() && receipt.digest == digest(body) }
        }
        val (identity, engine) = auth
        val response = EngineApi(engine).execute(identity.actor, route, body)
        if (route == ApiRoutes.SIGN_OUT || route == ApiRoutes.DELETE_ACCOUNT) {
            val receipt = EngineJson.decodeFromString(SignOutResponse.serializer(), response)
            if (receipt.result is AppResult.Success && engine.exportState().accounts[identity.actor]?.active == false) {
                retired[tokenKey(header!!.removePrefix("Bearer "))] = RetiredReceipt(route, digest(body), response, clock.now() + policy.retiredReceiptTtlMillis)
                while (retired.size > policy.maximumRetiredReceipts) retired.remove(retired.keys.first())
                revoke(identity)
            }
        }
        voice.prune()
        return response
    }

    @Synchronized
    internal fun canExecute(header: String?, route: String): Boolean {
        if (authorize(header) != null) return true
        if (header == null || !header.startsWith("Bearer ")) return false
        val receipt = retired[tokenKey(header.removePrefix("Bearer "))] ?: return false
        return receipt.route == route && receipt.expiresAt > clock.now()
    }

    private fun tokenKey(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

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
        val identity = tokens[tokenKey(header.removePrefix("Bearer "))] ?: return null
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
        val changed = tokens.values.map { it.roomCode }.filter { it in expired }.toSet()
        tokens.entries.removeAll { it.value.roomCode in expired }
        changed.forEach(::persistTokens)
    }
}
