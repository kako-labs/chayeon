package dev.chayeon.feature.meet

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

internal object MeetFixtures {
    val parked = DrivingState(policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)
    val signature = PublicSignature("signature-peer", 1, "여유롭게 멈추는 밤 드라이버",
        listOf(PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.SIGNATURE, 94), PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.STEADY, 82),
            PublicTrait(TraitId.TIRE_CARE, TraitLevel.VISIBLE, 65)), KnotSeed(71), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
    val peer = PublicProfile("peer", 1, "느린오후", AvatarId.SLOW,
        setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW, InterestId.EXHIBITIONS), ProfileKind.DEMO_PEER, signature)
    val own = PublicProfile("self", 1, "새벽드라이브", AvatarId.DAWN,
        setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW), ProfileKind.VIRTUAL_PROFILE,
        signature.copy(id = "signature-self", knotSeed = KnotSeed(12), title = "깜빡이를 잊지 않는 드라이버"))
    val chemistry = Chemistry(listOf(ChemistryLine(ChemistryKind.SHARED, TraitId.SIGNAL_MANNERS, TraitId.SIGNAL_MANNERS, "둘 다 깜빡이를 챙겨요")))
    val candidate = Candidate("candidate", 1, peer, chemistry, setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW))
    private val engine = dev.chayeon.core.signature.DefaultSignatureEngine()
    val tourOwn = own.copy(signature = signature.copy(traits = listOf(
        PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.STEADY, 82), PublicTrait(TraitId.CABIN_CARE, TraitLevel.STEADY, 82))))
    val tourPeer = peer.copy(nickname = "별보는밤", avatar = AvatarId.STAR, interests = setOf(InterestId.NIGHT_VIEW, InterestId.FILM_PHOTOGRAPHY),
        signature = signature.copy(title = "깜빡이를 잊지 않는 밤 드라이버", traits = listOf(
            PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE, 94),
            PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE, null), PublicTrait(TraitId.CABIN_CARE, TraitLevel.STEADY, 82))))
    val tourCandidate = candidate.copy(profile = tourPeer, chemistry = engine.chemistry(tourOwn.signature!!, tourPeer.signature!!), sharedInterests = setOf(InterestId.NIGHT_VIEW))
    val completeCandidate = candidate.copy(profile = peer.copy(
        nickname = "밤하늘따라천천히달려봐요",
        interests = setOf(InterestId.JAZZ, InterestId.NIGHT_VIEW, InterestId.DRIVE_MUSIC, InterestId.FILM_PHOTOGRAPHY, InterestId.EXHIBITIONS),
        signature = signature.copy(traits = TraitId.entries.map { PublicTrait(it, TraitLevel.SIGNATURE, if (it == TraitId.LONG_ROAD || it == TraitId.NIGHT_DRIVE) null else 94) })),
        chemistry = engine.chemistry(own.signature!!, signature.copy(traits = TraitId.entries.map { PublicTrait(it, TraitLevel.SIGNATURE, if (it == TraitId.LONG_ROAD || it == TraitId.NIGHT_DRIVE) null else 94) })))
    val discovery = DiscoveryState(version = 1, mode = DiscoveryMode.ON, status = DiscoveryStatus.AVAILABLE,
        locationAvailability = LocationAvailability.FRESH, candidates = listOf(candidate))
    val request = ConnectionRequest("request", 2, peer, RequestDirection.INCOMING, ConnectionRequestStatus.PENDING,
        100_000, 220_000, chemistry)
    val connection = Connection("connection", 3, "request", peer, ConnectionStatus.CONNECTED, 110_000, chemistry)
    val ended = connection.copy(version = 4, status = ConnectionStatus.ENDED, endReason = ConnectionEndReason.DISTANCE,
        endedAtEpochMillis = 1_190_000, encounterId = "encounter")
}

internal class FakeMeetDeps : MeetDeps, DiscoveryRepository, ConnectionRepository, ProfileRepository, DrivingStateProvider, ServerTimeProvider {
    val callState = MutableStateFlow(VoiceCallState())
    val muteCommands = mutableListOf<Boolean>()
    var microphoneRequests = 0
    override val voiceCallRepository = object : VoiceCallRepository {
        override val state = callState
        override suspend fun setMuted(muted: Boolean) { muteCommands += muted; callState.value = callState.value.copy(muted = muted) }
        override suspend fun requestMicrophone(): Boolean { microphoneRequests++; return true }
    }
    val homeZone = MutableStateFlow(HomeZoneState())
    override val homeZoneRepository = object : HomeZoneRepository {
        override val state = homeZone
        override suspend fun setFromCurrentPosition(radius: HomeZoneRadius, source: LocationSource) = AppResult.Success(Unit)
        override suspend fun changeRadius(radius: HomeZoneRadius) = AppResult.Success(Unit)
        override suspend fun clear() = AppResult.Success(Unit)
    }
    override val discoveryRepository: DiscoveryRepository get() = this
    override val connectionRepository: ConnectionRepository get() = this
    override val profileRepository: ProfileRepository get() = this
    override val drivingStateProvider: DrivingStateProvider get() = this
    override val serverTimeProvider: ServerTimeProvider get() = this
    override val drivingState = MutableStateFlow(MeetFixtures.parked)
    override val discovery = MutableStateFlow(MeetFixtures.discovery)
    override val profile = MutableStateFlow<LoadState<MyProfile?>>(LoadState.Ready(MyProfile("self", 1, MeetFixtures.own, RegistrationState.READY), 1))
    override val settings = MutableStateFlow<LoadState<ParticipationSettings>>(LoadState.Ready(ParticipationSettings("settings", 1), 1))
    override val requests = MutableStateFlow<LoadState<List<ConnectionRequest>>>(LoadState.Ready(emptyList(), 1))
    override val connection = MutableStateFlow<LoadState<Connection?>>(LoadState.Ready(null, 1))
    override val serverTime = MutableStateFlow<ServerTimeAnchor?>(ServerTimeAnchor(100_000, 10_000))
    var monotonic = 10_000L
    override fun monotonicTimeMillis() = monotonic

    val discoveryCommands = mutableListOf<SetDiscoveryRequest>()
    val refreshCommands = mutableListOf<RefreshDiscoveryRequest>()
    val requestCommands = mutableListOf<RequestConnectionRequest>()
    val respondCommands = mutableListOf<RespondConnectionRequest>()
    val cancelCommands = mutableListOf<CancelConnectionRequest>()
    val leaveCommands = mutableListOf<LeaveConnectionRequest>()
    var setDiscoveryResult: suspend (SetDiscoveryRequest) -> AppResult<DiscoveryState> = { AppResult.Success(discovery.value.copy(mode = it.mode)) }
    var refreshResult: suspend (RefreshDiscoveryRequest) -> AppResult<DiscoveryState> = { AppResult.Success(discovery.value) }
    var requestResult: suspend (RequestConnectionRequest) -> AppResult<ConnectionRequest> = { AppResult.Success(MeetFixtures.request.copy(direction = RequestDirection.OUTGOING)) }
    var respondResult: suspend (RespondConnectionRequest) -> AppResult<ConnectionRequest> = { AppResult.Success(MeetFixtures.request.copy(status = ConnectionRequestStatus.ACCEPTED, connectionId = "connection")) }
    var cancelResult: suspend (CancelConnectionRequest) -> AppResult<ConnectionRequest> = { AppResult.Success(MeetFixtures.request.copy(status = ConnectionRequestStatus.CANCELLED)) }
    var leaveResult: suspend (LeaveConnectionRequest) -> AppResult<Connection> = { AppResult.Success(MeetFixtures.ended) }
    override suspend fun setDiscovery(request: SetDiscoveryRequest): AppResult<DiscoveryState> { discoveryCommands += request; return setDiscoveryResult(request) }
    override suspend fun refresh(request: RefreshDiscoveryRequest): AppResult<DiscoveryState> { refreshCommands += request; return refreshResult(request) }
    override suspend fun requestConnection(request: RequestConnectionRequest): AppResult<ConnectionRequest> { requestCommands += request; return requestResult(request) }
    override suspend fun respond(request: RespondConnectionRequest): AppResult<ConnectionRequest> { respondCommands += request; return respondResult(request) }
    override suspend fun cancel(request: CancelConnectionRequest): AppResult<ConnectionRequest> { cancelCommands += request; return cancelResult(request) }
    override suspend fun leave(request: LeaveConnectionRequest): AppResult<Connection> { leaveCommands += request; return leaveResult(request) }
    override suspend fun updateLocation(request: UpdateLocationRequest) = AppResult.Failure(AppError.Unknown)
    override suspend fun setSyntheticPresence(request: SetSyntheticPresenceRequest) = AppResult.Failure(AppError.Unknown)
    override suspend fun saveProfile(request: SaveProfileRequest) = AppResult.Failure(AppError.Unknown)
    override suspend fun recordConsent(request: RecordConsentRequest) = AppResult.Failure(AppError.Unknown)
    override suspend fun updateSettings(request: UpdateSettingsRequest) = AppResult.Failure(AppError.Unknown)
    override suspend fun publishSignature(request: PublishSignatureRequest) = AppResult.Failure(AppError.Unknown)
}
