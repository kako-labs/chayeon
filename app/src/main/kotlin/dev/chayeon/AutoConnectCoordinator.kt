package dev.chayeon

import dev.chayeon.core.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
internal data class AutomaticAttempt(val peerId: String, val commandId: String)

@Serializable
internal data class AutoConnectMemory(
    val blockedDays: Map<String, String> = emptyMap(),
    val observedTerminals: Set<String> = emptySet(),
    val cooldownUntil: Long = 0,
    val pending: AutomaticAttempt? = null,
    val acceptedRequests: Set<String> = emptySet(),
    val announcedConnections: Set<String> = emptySet(),
)

internal interface AutoConnectMemoryStore {
    fun read(accountId: String): AutoConnectMemory
    fun write(accountId: String, memory: AutoConnectMemory)
}

internal class MemoryAutoConnectStore : AutoConnectMemoryStore {
    private val values = mutableMapOf<String, AutoConnectMemory>()
    override fun read(accountId: String) = values[accountId] ?: AutoConnectMemory()
    override fun write(accountId: String, memory: AutoConnectMemory) { values[accountId] = memory }
}

/** Application-owned consent automation; no discovery UI is unlocked by this service. */
internal class AutoConnectCoordinator(
    private val snapshots: Flow<AutoConnectSnapshot?>,
    private val driving: DrivingStateProvider,
    private val home: HomeZoneRepository,
    private val connections: ConnectionRepository,
    private val scope: CoroutineScope,
    private val memoryStore: AutoConnectMemoryStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private var current: AutoConnectSnapshot? = null
    private var account: String? = null
    private var memory = AutoConnectMemory()
    private var storageHealthy = true
    private var started = false
    private var lastAttemptAt = Long.MIN_VALUE

    private fun day(time: Long) = Instant.ofEpochMilli(time).atZone(zone()).toLocalDate().toString()

    private fun save(next: AutoConnectMemory): Boolean {
        return try {
            memoryStore.write(checkNotNull(account), next)
            memory = next
            true
        } catch (_: Exception) { storageHealthy = false; false }
    }

    fun canAct(peerId: String): Boolean = storageHealthy && current?.let { snapshot ->
        snapshot.settings.drivingConnectMode == DrivingConnectMode.AUTO &&
            snapshot.discovery.mode == DiscoveryMode.ON && peerId !in snapshot.blockedProfileIds &&
            now() >= memory.cooldownUntil && memory.blockedDays[peerId] != day(now())
    } == true

    fun shouldAnnounce(connection: Connection, snapshot: AutoConnectSnapshot? = current): Boolean {
        if (snapshot == null || !bindAccount(snapshot)) return false
        if (connection.status != ConnectionStatus.CONNECTED || connection.id in memory.announcedConnections) return false
        return connection.requestId in memory.acceptedRequests || snapshot.requests.any {
            it.id == connection.requestId && it.automatic && it.direction == RequestDirection.OUTGOING
        }
    }

    fun markAnnounced(connectionId: String): Boolean = save(memory.copy(announcedConnections = memory.announcedConnections + connectionId))

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val ticks = flow { while (true) { emit(now()); delay(1_000) } }
            combine(snapshots, driving.drivingState, home.state, ticks) { snapshot, policy, protection, _ ->
                Triple(snapshot, policy.policy, protection.suppressed)
            }.collect { (snapshot, policy, suppressed) ->
                update(snapshot, policy, suppressed)
            }
        }
    }

    internal suspend fun update(snapshot: AutoConnectSnapshot?, policy: DrivingPolicy, suppressed: Boolean) {
        current = snapshot
        if (snapshot == null) return
        if (!bindAccount(snapshot)) return
        if (!storageHealthy) return
        observeTerminalStates(snapshot)
        if (policy != DrivingPolicy.RESTRICTED || suppressed || snapshot.settings.drivingConnectMode != DrivingConnectMode.AUTO ||
            snapshot.discovery.mode != DiscoveryMode.ON || snapshot.connections.any { it.status != ConnectionStatus.ENDED } ||
            now() < memory.cooldownUntil) return
        val conditions = snapshot.settings.autoConnectConditions
        val incoming = snapshot.requests.filter { it.direction == RequestDirection.INCOMING && it.status == ConnectionRequestStatus.PENDING &&
            it.expiresAtEpochMillis > now() && canAct(it.peer.id) && conditions.matches(it.peer, snapshot.ownInterests) }
            .sortedWith(compareByDescending<ConnectionRequest> { conditions.exceededCount(it.peer) }
                .thenByDescending { (it.peer.interests intersect snapshot.ownInterests).size }.thenBy { it.peer.id }).firstOrNull()
        if (incoming != null) {
            if (lastAttemptAt != Long.MIN_VALUE && now() - lastAttemptAt < 5_000) return
            lastAttemptAt = now()
            if (!save(memory.copy(acceptedRequests = memory.acceptedRequests + incoming.id))) return
            val result = connections.respond(RespondConnectionRequest(CommandMeta("auto-accept-${incoming.id}"), incoming.id, RequestDecision.ACCEPT, automatic = true))
            if (result is AppResult.Failure && result.error !in setOf(AppError.Network, AppError.Unknown)) {
                save(memory.copy(acceptedRequests = memory.acceptedRequests - incoming.id,
                    blockedDays = memory.blockedDays + (incoming.peer.id to day(now()))))
            }
            return
        }
        if (snapshot.requests.any { it.direction == RequestDirection.OUTGOING && it.status == ConnectionRequestStatus.PENDING }) return
        val pending = memory.pending
        val candidate = snapshot.discovery.candidates.filter {
            (pending == null || it.id == pending.peerId) && canAct(it.profile.id) &&
                (it.requestAvailableAtEpochMillis ?: 0) <= now() && conditions.matches(it.profile, snapshot.ownInterests)
        }.sortedWith(compareByDescending<Candidate> { conditions.exceededCount(it.profile) }
            .thenByDescending { (it.profile.interests intersect snapshot.ownInterests).size }.thenBy { it.profile.id }).firstOrNull() ?: return
        if (lastAttemptAt != Long.MIN_VALUE && now() - lastAttemptAt < 5_000) return
        val attempt = pending ?: AutomaticAttempt(candidate.id, UUID.randomUUID().toString())
        if (!save(memory.copy(pending = attempt))) return
        lastAttemptAt = now()
        val result = connections.requestConnection(RequestConnectionRequest(CommandMeta(attempt.commandId), candidate.id, automatic = true))
        if (result is AppResult.Failure && result.error !in setOf(AppError.Network, AppError.Unknown)) {
            save(memory.copy(pending = null, blockedDays = memory.blockedDays + (candidate.profile.id to day(now()))))
        }
    }

    private fun bindAccount(snapshot: AutoConnectSnapshot): Boolean {
        if (account != snapshot.accountId) {
            account = snapshot.accountId
            lastAttemptAt = Long.MIN_VALUE
            storageHealthy = true
            memory = try { memoryStore.read(snapshot.accountId) } catch (_: Exception) {
                storageHealthy = false; AutoConnectMemory()
            }
        }
        return storageHealthy
    }

    private fun blockedOn(peerId: String, date: String, days: Map<String, String>): Map<String, String> =
        days + (peerId to maxOf(days[peerId] ?: date, date))

    private fun observeTerminalStates(snapshot: AutoConnectSnapshot) {
        var next = memory
        snapshot.requests.filter { it.status in setOf(ConnectionRequestStatus.DECLINED, ConnectionRequestStatus.CANCELLED,
            ConnectionRequestStatus.EXPIRED, ConnectionRequestStatus.UNAVAILABLE) && it.id !in next.observedTerminals }.forEach {
            next = next.copy(blockedDays = blockedOn(it.peer.id, day(now()), next.blockedDays),
                observedTerminals = next.observedTerminals + it.id,
                pending = next.pending.takeUnless { pending -> pending?.peerId == it.peer.id })
        }
        snapshot.connections.filter { it.status == ConnectionStatus.ENDED && it.id !in next.observedTerminals }.forEach {
            val endedAt = it.endedAtEpochMillis ?: now()
            next = next.copy(blockedDays = blockedOn(it.peer.id, day(endedAt), next.blockedDays),
                observedTerminals = next.observedTerminals + it.id,
                cooldownUntil = maxOf(next.cooldownUntil, endedAt + 10 * 60_000),
                pending = next.pending.takeUnless { pending -> pending?.peerId == it.peer.id })
        }
        if (next != memory) save(next)
    }
}
