package dev.chayeon

import dev.chayeon.core.model.*
import dev.chayeon.data.social.*
import dev.chayeon.data.vehicle.EmptySignatureRepository
import dev.chayeon.data.vehicle.EmptyVehicleConsole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class TestGraph {
    val driving = MutableStateFlow(DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK))
    val sessions = MutableStateFlow<SessionState>(SessionState.SignedOut())
    val policy = ParticipationPolicy("policy", 1, 5, 2, 12, null, "2026-11-03")
    val active = Session("session", 1, "account", SessionMode.SOLO, PersonaCode.ACCOUNT_A, null, null, null, policy)
    val publicProfile = PublicProfile("profile", 1, "새벽드라이브", AvatarId.DAWN, emptySet(), ProfileKind.VIRTUAL_PROFILE, null)
    val profiles = MutableStateFlow<LoadState<MyProfile?>>(LoadState.Ready(null, 0))
    val requests = MutableStateFlow<LoadState<List<ConnectionRequest>>>(LoadState.Ready(emptyList(), 0))
    val connection = MutableStateFlow<LoadState<Connection?>>(LoadState.Ready(null, 0))
    val greetings = MutableStateFlow<LoadState<List<ConnectionGreeting>>>(LoadState.Ready(emptyList(), 0))
    val discovery = MutableStateFlow(DiscoveryState())
    val signatures = MutableStateFlow<LoadState<MySignature>>(LoadState.Ready(MySignature(), 0))
    val recaps = MutableStateFlow<LoadState<List<TripRecap>>>(LoadState.Ready(emptyList(), 0))
    val publications = mutableListOf<PublishSignatureRequest>()
    var publicationResult: AppResult<MyProfile> = AppResult.Failure(AppError.Network)
    var discoveryCalls = 0
    val graph = AppGraph(
        object : SessionRepository by EmptySessionRepository() { override val session = sessions },
        object : ProfileRepository by EmptyProfileRepository() {
            override val profile = profiles
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
            override fun observeGreetings(connectionId: String) = greetings
        },
        EmptyEncounterRepository(), EmptyAfterRepository(), EmptySafetyRepository(),
        object : DrivingStateProvider { override val drivingState = driving },
        object : SignatureRepository by EmptySignatureRepository() {
            override val signature = signatures
            override val recaps = this@TestGraph.recaps
            override fun observeRecap(recapId: String) = recaps.map { state -> LoadState.Ready((state as? LoadState.Ready)?.value?.find { it.id == recapId }, state.version) }
        },
        EmptyVehicleConsole(), EmptyServerTimeProvider(), "",
    ).apply { currentPolicy = { driving.value.policy }; demoEnabled = true }
    fun ready() {
        sessions.value = SessionState.Active(active)
        profiles.value = LoadState.Ready(MyProfile("profile", 1, publicProfile, RegistrationState.READY), 1)
    }
}
