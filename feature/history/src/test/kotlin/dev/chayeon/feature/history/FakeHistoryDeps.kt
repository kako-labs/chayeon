package dev.chayeon.feature.history

import dev.chayeon.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*

internal class FakeHistoryDeps : HistoryDeps {
    val homeZone = MutableStateFlow(HomeZoneState())
    override val homeZoneRepository = object : HomeZoneRepository {
        override val state = homeZone
        override suspend fun setFromCurrentPosition(radius: HomeZoneRadius, source: LocationSource) = result("home-set", Unit)
        override suspend fun changeRadius(radius: HomeZoneRadius) = result("home-radius", Unit)
        override suspend fun clear() = result("home-clear", Unit)
    }
    val driving = MutableStateFlow(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK))
    val sessionState = MutableStateFlow<SessionState>(SessionState.Active(HistoryFixtures.session))
    val personasState = MutableStateFlow<LoadState<List<Persona>>>(LoadState.Ready(HistoryFixtures.personas, 1))
    val modes = MutableStateFlow(setOf(SessionMode.SOLO, SessionMode.SERVER_DEMO))
    val profileState = MutableStateFlow<LoadState<MyProfile?>>(LoadState.Ready(HistoryFixtures.profile, 1))
    val settingsState = MutableStateFlow<LoadState<ParticipationSettings>>(LoadState.Ready(HistoryFixtures.settings, 1))
    val discoveryState = MutableStateFlow(DiscoveryState(mode = DiscoveryMode.ON))
    val connectionRequests = MutableStateFlow<LoadState<List<ConnectionRequest>>>(LoadState.Ready(emptyList(), 1))
    val connectionState = MutableStateFlow<LoadState<Connection?>>(LoadState.Ready(HistoryFixtures.connection, 1))
    val encountersState = MutableStateFlow<LoadState<List<Encounter>>>(LoadState.Ready(listOf(HistoryFixtures.encounter), 1))
    val encounterState = MutableStateFlow<LoadState<Encounter?>>(LoadState.Ready(HistoryFixtures.encounter, 1))
    val requestsState = MutableStateFlow<LoadState<List<AfterRequest>>>(LoadState.Ready(listOf(HistoryFixtures.request), 1))
    val relationshipsState = MutableStateFlow<LoadState<List<AfterConnection>>>(LoadState.Ready(listOf(HistoryFixtures.relationship), 1))
    val messagesState = MutableStateFlow<LoadState<List<AfterMessage>>>(LoadState.Ready(emptyList(), 1))
    val blocksState = MutableStateFlow<LoadState<List<Block>>>(LoadState.Ready(emptyList(), 1))
    val signatureState = MutableStateFlow<LoadState<MySignature>>(LoadState.Ready(MySignature(), 1))
    val recapsState = MutableStateFlow<LoadState<List<TripRecap>>>(LoadState.Ready(emptyList(), 1))
    val clock = MutableStateFlow<ServerTimeAnchor?>(ServerTimeAnchor(1_800_000_080_000, 100))
    var monotonic = 100L
    val calls = mutableListOf<Any>()
    var error: AppError? = null
    var gate: CompletableDeferred<Unit>? = null
    private suspend fun <T> result(request: Any, value: T): AppResult<T> {
        calls += request
        gate?.await()
        return error?.let { AppResult.Failure(it) } ?: AppResult.Success(value)
    }
    override val drivingStateProvider = object : DrivingStateProvider { override val drivingState = driving }
    override val serverTimeProvider = object : ServerTimeProvider {
        override val serverTime = clock
        override fun monotonicTimeMillis() = monotonic
    }
    override val sessionRepository = object : SessionRepository {
        override val session = sessionState
        override val personas = personasState
        override val availableModes = modes
        override suspend fun enter(request: EnterSessionRequest): AppResult<Session> = result(request, HistoryFixtures.session)
        override suspend fun signOut(request: SignOutRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
        override suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
    }
    override val profileRepository = object : ProfileRepository {
        override val profile = profileState
        override val settings = settingsState
        override suspend fun saveProfile(request: SaveProfileRequest): AppResult<MyProfile> = result(request, HistoryFixtures.profile)
        override suspend fun recordConsent(request: RecordConsentRequest): AppResult<ParticipationSettings> = result(request, HistoryFixtures.settings)
        override suspend fun updateSettings(request: UpdateSettingsRequest): AppResult<ParticipationSettings> = result(request, HistoryFixtures.settings)
        override suspend fun publishSignature(request: PublishSignatureRequest): AppResult<MyProfile> = result(request, HistoryFixtures.profile)
    }
    override val discoveryRepository = object : DiscoveryRepository {
        override val discovery = discoveryState
        override suspend fun setDiscovery(request: SetDiscoveryRequest): AppResult<DiscoveryState> = result(request, discoveryState.value)
        override suspend fun refresh(request: RefreshDiscoveryRequest): AppResult<DiscoveryState> = result(request, discoveryState.value)
        override suspend fun updateLocation(request: UpdateLocationRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
        override suspend fun setSyntheticPresence(request: SetSyntheticPresenceRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
    }
    override val connectionRepository = object : ConnectionRepository {
        override val requests = connectionRequests
        override val connection = connectionState
        override fun observeGreetings(connectionId: String): Flow<LoadState<List<ConnectionGreeting>>> = flowOf(LoadState.Ready(emptyList(), 1))
        override suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest> = AppResult.Failure(AppError.Unknown)
        override suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest> = AppResult.Failure(AppError.Unknown)
        override suspend fun cancel(request: CancelConnectionRequest): AppResult<ConnectionRequest> = AppResult.Failure(AppError.Unknown)
        override suspend fun sendGreeting(request: SendGreetingRequest): AppResult<ConnectionGreeting> = AppResult.Failure(AppError.Unknown)
        override suspend fun leave(request: LeaveConnectionRequest): AppResult<Connection> = result(request, HistoryFixtures.connection)
    }
    override val encounterRepository = object : EncounterRepository {
        override val encounters = encountersState
        override fun observeEncounter(encounterId: String) = encounterState
        override suspend fun deleteEncounter(request: DeleteEncounterRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
        override suspend fun deleteAllEncounters(request: DeleteAllEncountersRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
    }
    override val afterRepository = object : AfterRepository {
        override val requests = requestsState
        override val connections = relationshipsState
        override fun observeMessages(afterConnectionId: String) = messagesState
        override suspend fun requestAfter(request: RequestAfterRequest): AppResult<AfterRequest> = result(request, HistoryFixtures.request)
        override suspend fun respond(request: RespondAfterRequest): AppResult<AfterRequest> = result(request, HistoryFixtures.request)
        override suspend fun withdraw(request: WithdrawAfterRequest): AppResult<AfterRequest> = result(request, HistoryFixtures.request)
        override suspend fun sendMessage(request: SendAfterMessageRequest): AppResult<AfterMessage> = result(request, HistoryFixtures.message)
        override suspend fun endRelationship(request: EndAfterConnectionRequest): AppResult<AfterConnection> = result(request, HistoryFixtures.relationship)
    }
    override val safetyRepository = object : SafetyRepository {
        override val blocks = blocksState
        override suspend fun block(request: BlockPeerRequest): AppResult<Block> = result(request, HistoryFixtures.block)
        override suspend fun unblock(request: UnblockPeerRequest): AppResult<CommandReceipt> = result(request, CommandReceipt("receipt", 2))
        override suspend fun report(request: ReportPeerRequest): AppResult<Report> = result(request, Report("report", 1, ReportReason.OTHER, ReportStatus.RECEIVED, 1))
    }
    override val signatureRepository = object : SignatureRepository {
        override val signature = signatureState
        override val recaps = recapsState
        override fun observeEvidence(trait: TraitId): Flow<LoadState<TraitEvidence?>> = flowOf(LoadState.Ready(null, 1))
        override fun observeRecap(recapId: String): Flow<LoadState<TripRecap?>> = flowOf(LoadState.Ready(null, 1))
        override suspend fun recordTrip(request: RecordTripRequest): AppResult<MySignature> = result(request, MySignature())
        override suspend fun updateVisibility(request: UpdateVisibilityRequest): AppResult<VisibilitySettings> = result(request, VisibilitySettings())
        override suspend fun excludeTrip(request: ExcludeTripRequest): AppResult<MySignature> = result(request, MySignature())
        override suspend fun markRecapSeen(request: MarkRecapSeenRequest): AppResult<TripRecap> = AppResult.Failure(AppError.Unknown)
        override suspend fun clearLocalData(): AppResult<Unit> = result(Unit, Unit)
    }
}
