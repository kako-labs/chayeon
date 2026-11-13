package dev.chayeon

import dev.chayeon.core.model.*
import dev.chayeon.data.social.*
import dev.chayeon.data.vehicle.EmptySignatureRepository
import dev.chayeon.data.vehicle.EmptyVehicleConsole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class TestGraph(vehicleConsole: VehicleConsole = EmptyVehicleConsole()) {
    val driving = MutableStateFlow(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK))
    val sessions = MutableStateFlow<SessionState>(SessionState.SignedOut())
    val policy = ParticipationPolicy("policy", 1, 5, 2, 12, null, "2026-11-03")
    val active = Session("session", 1, "account", SessionMode.SOLO, null, null, null, policy)
    val publicProfile = PublicProfile("profile", 1, "새벽드라이브", AvatarId.DAWN, emptySet(), ProfileKind.VIRTUAL_PROFILE, null)
    val profiles = MutableStateFlow<LoadState<MyProfile?>>(LoadState.Ready(null, 0))
    val settings = MutableStateFlow<LoadState<ParticipationSettings>>(LoadState.Ready(ParticipationSettings("settings", 0), 0))
    val requests = MutableStateFlow<LoadState<List<ConnectionRequest>>>(LoadState.Ready(emptyList(), 0))
    val connection = MutableStateFlow<LoadState<Connection?>>(LoadState.Ready(null, 0))
    val afterRequests = MutableStateFlow<LoadState<List<AfterRequest>>>(LoadState.Ready(emptyList(), 0))
    val afterConnections = MutableStateFlow<LoadState<List<AfterConnection>>>(LoadState.Ready(emptyList(), 0))
    val discovery = MutableStateFlow(DiscoveryState())
    val signatures = MutableStateFlow<LoadState<MySignature>>(LoadState.Ready(MySignature(), 0))
    val recaps = MutableStateFlow<LoadState<List<TripRecap>>>(LoadState.Ready(emptyList(), 0))
    val seenCalls = mutableListOf<MarkRecapSeenRequest>()
    val excludedTrips = mutableListOf<String>()
    val publications = mutableListOf<PublishSignatureRequest>()
    var publicationResult: AppResult<MyProfile> = AppResult.Failure(AppError.Network)
    var discoveryCalls = 0
    val homeZone = MutableStateFlow(HomeZoneState())
    val homeRepository = object : HomeZoneRepository {
        override val state = homeZone
        override suspend fun setFromCurrentPosition(radius: HomeZoneRadius, source: LocationSource) = AppResult.Success(Unit)
        override suspend fun changeRadius(radius: HomeZoneRadius) = AppResult.Success(Unit)
        override suspend fun clear() = AppResult.Success(Unit)
    }
    private val homeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
    val homeCoordinator = HomeZoneCoordinator(homeRepository, {}, homeScope)
    val graph = AppGraph(
        object : SessionRepository by EmptySessionRepository() { override val session = sessions },
        object : ProfileRepository by EmptyProfileRepository() {
            override val profile = profiles
            override val settings = this@TestGraph.settings
            override suspend fun publishSignature(request: PublishSignatureRequest): AppResult<MyProfile> {
                publications += request
                return publicationResult
            }
        },
        object : DiscoveryRepository by EmptyDiscoveryRepository() {
            override val discovery = this@TestGraph.discovery
            override suspend fun setDiscovery(request: SetDiscoveryRequest): AppResult<DiscoveryState> {
                discoveryCalls++
                discovery.value = discovery.value.copy(mode = request.mode)
                return AppResult.Success(discovery.value)
            }
        },
        object : ConnectionRepository by EmptyConnectionRepository() {
            override val requests = this@TestGraph.requests
            override val connection = this@TestGraph.connection
        },
        EmptyEncounterRepository(), object : AfterRepository by EmptyAfterRepository() {
            override val connections = afterConnections
            override val requests = afterRequests

        }, EmptySafetyRepository(),
        object : DrivingStateProvider { override val drivingState = driving },
        object : SignatureRepository by EmptySignatureRepository() {
            override val signature = signatures
            override val recaps = this@TestGraph.recaps
            override suspend fun markRecapSeen(request: MarkRecapSeenRequest): AppResult<TripRecap> {
                seenCalls += request
                val values = (recaps.value as LoadState.Ready).value
                val seen = values.single { it.id == request.recapId }.copy(seen = true)
                recaps.value = LoadState.Ready(values.map { if (it.id == seen.id) seen else it }, recaps.value.version + 1)
                return AppResult.Success(seen)
            }
            override suspend fun excludeTrip(request: ExcludeTripRequest): AppResult<MySignature> {
                excludedTrips += request.tripId
                val values = (recaps.value as LoadState.Ready).value
                recaps.value = LoadState.Ready(values.map { if (it.tripId == request.tripId) it.copy(included = false, firstSignature = false) else it }, recaps.value.version + 1)
                return AppResult.Success((signatures.value as LoadState.Ready).value)
            }
            override fun observeRecap(recapId: String) = recaps.map { state -> LoadState.Ready((state as? LoadState.Ready)?.value?.find { it.id == recapId }, state.version) }
        },
        vehicleConsole, EmptyServerTimeProvider(), "", homeRepository, homeCoordinator,
    ).apply { currentPolicy = { driving.value.policy }; demoEnabled = true }
    fun ready() {
        sessions.value = SessionState.Active(active)
        profiles.value = LoadState.Ready(MyProfile("profile", 1, publicProfile, RegistrationState.READY), 1)
    }
}
