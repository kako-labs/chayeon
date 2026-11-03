package dev.chayeon.core.model

import kotlinx.coroutines.flow.Flow

/** Implementations reject non-increasing revisions and clear private local data before sign-out completes. */
interface SessionRepository {
    val session: Flow<SessionState>
    val personas: Flow<LoadState<List<Persona>>>
    val availableModes: Flow<Set<SessionMode>>
    suspend fun enter(request: EnterSessionRequest): AppResult<Session>
    suspend fun signOut(request: SignOutRequest): AppResult<CommandReceipt>
    suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<CommandReceipt>
}

interface ProfileRepository {
    val profile: Flow<LoadState<MyProfile?>>
    val settings: Flow<LoadState<ParticipationSettings>>
    suspend fun saveProfile(request: SaveProfileRequest): AppResult<MyProfile>
    suspend fun recordConsent(request: RecordConsentRequest): AppResult<ParticipationSettings>
    suspend fun updateSettings(request: UpdateSettingsRequest): AppResult<ParticipationSettings>
    suspend fun publishSignature(request: PublishSignatureRequest): AppResult<MyProfile>
}

interface DiscoveryRepository {
    val discovery: Flow<DiscoveryState>
    suspend fun setDiscovery(request: SetDiscoveryRequest): AppResult<DiscoveryState>
    suspend fun refresh(request: RefreshDiscoveryRequest): AppResult<DiscoveryState>
    suspend fun updateLocation(request: UpdateLocationRequest): AppResult<CommandReceipt>
    suspend fun setSyntheticPresence(request: SetSyntheticPresenceRequest): AppResult<CommandReceipt>
}

interface ConnectionRepository {
    val requests: Flow<LoadState<List<ConnectionRequest>>>
    val connection: Flow<LoadState<Connection?>>
    fun observeGreetings(connectionId: String): Flow<LoadState<List<ConnectionGreeting>>>
    suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest>
    suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest>
    suspend fun cancel(request: CancelConnectionRequest): AppResult<ConnectionRequest>
    suspend fun sendGreeting(request: SendGreetingRequest): AppResult<ConnectionGreeting>
    suspend fun leave(request: LeaveConnectionRequest): AppResult<Connection>
}

interface EncounterRepository {
    val encounters: Flow<LoadState<List<Encounter>>>
    fun observeEncounter(encounterId: String): Flow<LoadState<Encounter?>>
    suspend fun deleteEncounter(request: DeleteEncounterRequest): AppResult<CommandReceipt>
    suspend fun deleteAllEncounters(request: DeleteAllEncountersRequest): AppResult<CommandReceipt>
}

interface AfterRepository {
    val requests: Flow<LoadState<List<AfterRequest>>>
    val connections: Flow<LoadState<List<AfterConnection>>>
    fun observeMessages(afterConnectionId: String): Flow<LoadState<List<AfterMessage>>>
    suspend fun requestAfter(request: RequestAfterRequest): AppResult<AfterRequest>
    suspend fun respond(request: RespondAfterRequest): AppResult<AfterRequest>
    suspend fun withdraw(request: WithdrawAfterRequest): AppResult<AfterRequest>
    suspend fun sendMessage(request: SendAfterMessageRequest): AppResult<AfterMessage>
    suspend fun endRelationship(request: EndAfterConnectionRequest): AppResult<AfterConnection>
}

interface SafetyRepository {
    val blocks: Flow<LoadState<List<Block>>>
    suspend fun block(request: BlockPeerRequest): AppResult<Block>
    suspend fun unblock(request: UnblockPeerRequest): AppResult<CommandReceipt>
    suspend fun report(request: ReportPeerRequest): AppResult<Report>
}

interface DrivingStateProvider {
    val drivingState: Flow<DrivingState>
}

/** Null means no current-session anchor is available; never fall back to the device wall clock. */
interface ServerTimeProvider {
    val serverTime: Flow<ServerTimeAnchor?>
    fun monotonicTimeMillis(): Long
}

/** Local-only evidence must never be inserted into an API DTO or sent to the social backend. */
interface SignatureRepository {
    val signature: Flow<LoadState<MySignature>>
    val recaps: Flow<LoadState<List<TripRecap>>>
    fun observeEvidence(trait: TraitId): Flow<LoadState<TraitEvidence?>>
    fun observeRecap(recapId: String): Flow<LoadState<TripRecap?>>
    suspend fun recordTrip(request: RecordTripRequest): AppResult<MySignature>
    suspend fun updateVisibility(request: UpdateVisibilityRequest): AppResult<VisibilitySettings>
    suspend fun excludeTrip(request: ExcludeTripRequest): AppResult<MySignature>
    suspend fun markRecapSeen(request: MarkRecapSeenRequest): AppResult<TripRecap>
    suspend fun clearLocalData(): AppResult<Unit>
}

interface VehicleConsole {
    val consoleState: Flow<ConsoleState>
    suspend fun setGear(request: SetGearRequest): AppResult<DrivingState>
    suspend fun replay(request: ReplayDriveRequest): AppResult<ConsoleState>
    suspend fun stopReplay(request: StopReplayRequest): AppResult<ConsoleState>
}

enum class DriveScenario { GENTLE, EARLY_BRAKE, SIGNAL_MANNERS, CARE, UNSUPPORTED }

enum class ReplayStatus { IDLE, PLAYING, FINISHED, FAILED }

data class ConsoleState(
    override val id: String = "vehicle-console",
    override val version: Long = 0,
    val available: Boolean = false,
    val status: ReplayStatus = ReplayStatus.IDLE,
    val scenario: DriveScenario? = null,
) : VersionedEntity

data class UpdateVisibilityRequest(val meta: CommandMeta, val visibleTraits: Set<TraitId>, val publishSignature: Boolean)
data class RecordTripRequest(val meta: CommandMeta, val metrics: DerivedTripMetrics)
data class ExcludeTripRequest(val meta: CommandMeta, val tripId: String)
data class MarkRecapSeenRequest(val meta: CommandMeta, val recapId: String)
data class SetGearRequest(val meta: CommandMeta, val gear: Gear)
data class ReplayDriveRequest(val meta: CommandMeta, val scenario: DriveScenario)
data class StopReplayRequest(val meta: CommandMeta)
