package dev.chayeon.feature.signature

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class SignatureTestDeps : SignatureDeps {
    override val profileRepository = FakeProfileRepository()
    override val signatureRepository = FakeSignatureRepository()
    override val drivingStateProvider = FakeDrivingStateProvider()
    override val vehicleConsole = FakeVehicleConsole()
}

internal class FakeDrivingStateProvider : DrivingStateProvider {
    override val drivingState = MutableStateFlow(SignatureScreenFixtures.driving)
}

internal class FakeVehicleConsole : VehicleConsole {
    override val consoleState = MutableStateFlow(ConsoleState(available = true))
    override suspend fun setGear(request: SetGearRequest): AppResult<DrivingState> = error("Not used")
    override suspend fun replay(request: ReplayDriveRequest): AppResult<ConsoleState> = error("Not used")
    override suspend fun stopReplay(request: StopReplayRequest): AppResult<ConsoleState> = error("Not used")
}

internal class FakeProfileRepository : ProfileRepository {
    override val profile = MutableStateFlow<LoadState<MyProfile?>>(SignatureScreenFixtures.ready(SignatureScreenFixtures.profile))
    override val settings = MutableStateFlow<LoadState<ParticipationSettings>>(LoadState.Ready(ParticipationSettings("settings", 1), 1))
    val publishCalls = mutableListOf<PublishSignatureRequest>()
    val settingsCalls = mutableListOf<UpdateSettingsRequest>()
    var publishError: AppError? = null
    var beforePublish: suspend (PublishSignatureRequest) -> Unit = {}

    override suspend fun publishSignature(request: PublishSignatureRequest): AppResult<MyProfile> {
        publishCalls += request
        beforePublish(request)
        publishError?.let { return AppResult.Failure(it) }
        val current = (profile.value as? LoadState.Ready)?.value ?: SignatureScreenFixtures.profile
        val next = current.copy(version = current.version + 1,
            publicProfile = current.publicProfile.copy(signature = request.signature, version = current.version + 1))
        profile.value = LoadState.Ready(next, next.version)
        return AppResult.Success(next)
    }

    override suspend fun saveProfile(request: SaveProfileRequest): AppResult<MyProfile> = error("Not used")
    override suspend fun recordConsent(request: RecordConsentRequest): AppResult<ParticipationSettings> = error("Not used")
    override suspend fun updateSettings(request: UpdateSettingsRequest): AppResult<ParticipationSettings> {
        settingsCalls += request
        val current = (settings.value as LoadState.Ready).value
        val next = current.copy(version = current.version + 1, notifyRequests = request.notifyRequests,
            receiveRequestsWhileDriving = request.receiveRequestsWhileDriving)
        settings.value = LoadState.Ready(next, next.version)
        return AppResult.Success(next)
    }
}

internal class FakeSignatureRepository : SignatureRepository {
    override val signature = MutableStateFlow<LoadState<MySignature>>(SignatureScreenFixtures.ready(SignatureScreenFixtures.signature))
    override val recaps = MutableStateFlow<LoadState<List<TripRecap>>>(SignatureScreenFixtures.ready(listOf(SignatureScreenFixtures.recap)))
    val evidence = MutableStateFlow<LoadState<TraitEvidence?>>(SignatureScreenFixtures.ready(SignatureScreenFixtures.evidence))
    val recap = MutableStateFlow<LoadState<TripRecap?>>(SignatureScreenFixtures.ready(SignatureScreenFixtures.recap))
    val visibilityCalls = mutableListOf<UpdateVisibilityRequest>()
    val exclusionCalls = mutableListOf<ExcludeTripRequest>()
    val seenCalls = mutableListOf<MarkRecapSeenRequest>()
    var visibilityError: AppError? = null
    var exclusionError: AppError? = null
    var seenError: AppError? = null
    var beforeVisibility: suspend (UpdateVisibilityRequest) -> Unit = {}
    var beforeExclusion: suspend (ExcludeTripRequest) -> Unit = {}
    var beforeSeen: suspend (MarkRecapSeenRequest) -> Unit = {}

    override fun observeEvidence(trait: TraitId): Flow<LoadState<TraitEvidence?>> = evidence
    override fun observeRecap(recapId: String): Flow<LoadState<TripRecap?>> = recap

    override suspend fun updateVisibility(request: UpdateVisibilityRequest): AppResult<VisibilitySettings> {
        visibilityCalls += request
        beforeVisibility(request)
        visibilityError?.let { return AppResult.Failure(it) }
        val current = (signature.value as LoadState.Ready).value
        val settings = current.visibility.copy(version = current.visibility.version + 1,
            visibleTraits = request.visibleTraits, publishSignature = request.publishSignature)
        val next = current.copy(version = current.version + 1, visibility = settings)
        signature.value = LoadState.Ready(next, next.version)
        return AppResult.Success(settings)
    }

    override suspend fun excludeTrip(request: ExcludeTripRequest): AppResult<MySignature> {
        exclusionCalls += request
        beforeExclusion(request)
        exclusionError?.let { return AppResult.Failure(it) }
        val current = (signature.value as LoadState.Ready).value
        val next = current.copy(version = current.version + 1, evidence = emptyList(), publicSignature = null, knotSeed = KnotSeed(0))
        signature.value = LoadState.Ready(next, next.version)
        val previousRecap = (recap.value as LoadState.Ready).value
        if (previousRecap != null) recap.value = LoadState.Ready(previousRecap.copy(
            version = next.version, included = false, changes = emptyList(), currentKnot = next.knotSeed), next.version)
        return AppResult.Success(next)
    }

    override suspend fun markRecapSeen(request: MarkRecapSeenRequest): AppResult<TripRecap> {
        seenCalls += request
        beforeSeen(request)
        seenError?.let { return AppResult.Failure(it) }
        val current = (recap.value as LoadState.Ready).value ?: return AppResult.Failure(AppError.AlreadyClosed)
        val next = current.copy(version = current.version + 1, seen = true)
        recap.value = LoadState.Ready(next, next.version)
        return AppResult.Success(next)
    }

    override suspend fun recordTrip(request: RecordTripRequest): AppResult<MySignature> = error("Not used")
    override suspend fun clearLocalData(): AppResult<Unit> = AppResult.Success(Unit)
}
