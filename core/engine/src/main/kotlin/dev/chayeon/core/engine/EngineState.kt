package dev.chayeon.core.engine

import dev.chayeon.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

fun interface EngineClock { fun now(): Long }
fun interface EngineIdGenerator { fun next(): String }

/** Implementations must replace the complete state atomically or throw without modifying storage. */
interface EngineStore {
    fun load(): EngineState?
    fun save(state: EngineState)
}

class MemoryEngineStore(initial: EngineState? = null) : EngineStore {
    private var value = initial
    @Synchronized override fun load(): EngineState? = value
    @Synchronized override fun save(state: EngineState) { value = state }
}

@Serializable
data class EngineState(
    val revision: Long = 0,
    val viewRevisions: Map<String, Long> = emptyMap(),
    val viewDigests: Map<String, String> = emptyMap(),
    val lastTick: Long = 0,
    val accounts: Map<String, EngineAccount> = emptyMap(),
    val requests: Map<String, RequestRecord> = emptyMap(),
    val connections: Map<String, ConnectionRecord> = emptyMap(),
    val encounters: Map<String, EncounterRecord> = emptyMap(),
    val afterRequests: Map<String, AfterRequestRecord> = emptyMap(),
    val afterConnections: Map<String, AfterConnectionRecord> = emptyMap(),
    val greetings: Map<String, GreetingRecord> = emptyMap(),
    val messages: Map<String, MessageRecord> = emptyMap(),
    val blocks: Map<String, BlockRecord> = emptyMap(),
    val reports: Map<String, ReportRecord> = emptyMap(),
    val commands: List<CachedCommand> = emptyList(),
    val retiredCommands: Map<String, String> = emptyMap(),
    val demoEvents: Set<String> = emptySet(),
)

@Serializable
data class EngineAccount(
    val id: String,
    val session: Session,
    val profile: MyProfile,
    val settings: ParticipationSettings,
    val active: Boolean = true,
    val deleted: Boolean = false,
    val discovery: DiscoveryMode = DiscoveryMode.OFF,
    val discoverySince: Long? = null,
    val discoveryRefreshedAt: Long? = null,
    val locationSource: LocationSource = LocationSource.SYNTHETIC,
    val syntheticPresence: SyntheticPresence = SyntheticPresence.NEARBY,
    val locationAvailability: LocationAvailability = LocationAvailability.FRESH,
    val locationObservedAt: Long? = null,
)

@Serializable
data class RequestRecord(
    val id: String, val version: Long, val sender: String, val recipient: String,
    val status: ConnectionRequestStatus, val createdAt: Long, val expiresAt: Long,
    val connectionId: String? = null, val retryAt: Long? = null,
)

@Serializable
data class ConnectionRecord(
    val id: String, val version: Long, val requestId: String, val first: String, val second: String,
    val startedAt: Long, val status: ConnectionStatus = ConnectionStatus.CONNECTED,
    val checkingSince: Long? = null, val checkingReason: ConnectionEndReason? = null,
    val endedAt: Long? = null, val firstReason: ConnectionEndReason? = null,
    val secondReason: ConnectionEndReason? = null,
    val firstEncounter: String? = null, val secondEncounter: String? = null,
)

@Serializable
data class EncounterRecord(val id: String, val version: Long, val owner: String, val connectionId: String)

@Serializable
data class AfterRequestRecord(
    val id: String, val version: Long, val sender: String, val recipient: String,
    val connectionId: String, val status: AfterRequestStatus, val createdAt: Long, val expiresAt: Long,
    val afterConnectionId: String? = null, val retryAt: Long? = null,
)

@Serializable
data class AfterConnectionRecord(
    val id: String, val version: Long, val requestId: String, val connectionId: String,
    val first: String, val second: String, val connectedAt: Long,
    val status: AfterConnectionStatus = AfterConnectionStatus.CONNECTED,
    val closedAt: Long? = null, val firstReason: AfterCloseReason? = null, val secondReason: AfterCloseReason? = null,
)

@Serializable
data class GreetingRecord(val author: String, val greeting: ConnectionGreeting)
@Serializable
data class MessageRecord(val author: String, val message: AfterMessage)
@Serializable
data class BlockRecord(val id: String, val version: Long, val owner: String, val peer: String, val createdAt: Long)
@Serializable
data class ReportRecord(val owner: String, val peer: String, val report: Report, val context: ReportContext?)
@Serializable
data class CachedCommand(val actor: String, val commandId: String, val digest: String, val result: JsonElement)
