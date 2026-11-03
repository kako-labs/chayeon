package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class CandidateViewModel(private val deps: MeetDeps, private val candidateId: String) : ViewModel() {
    private val request = MeetCommand<RequestConnectionRequest>()
    private val navigation = Channel<String>(Channel.BUFFERED)
    val requestSent = navigation.receiveAsFlow()
    private var completed = false

    private val content = combine(
        deps.discoveryRepository.discovery,
        deps.profileRepository.profile,
        deps.serverTimeProvider.serverTime,
        flow { while (true) { emit(Unit); delay(1_000) } },
    ) { discovery, profile, anchor, _ ->
        val candidate = discovery.candidates.firstOrNull { it.id == candidateId }
            ?.takeIf { discovery.mode == DiscoveryMode.ON && discovery.status == DiscoveryStatus.AVAILABLE }
        val remaining = candidate?.requestAvailableAtEpochMillis?.let {
            anchor?.remainingMillisUntil(it, deps.serverTimeProvider.monotonicTimeMillis())
        }
        CandidateUiState(
            candidate = candidate,
            ownProfile = profile.publicProfile(),
            unavailableState = if (candidate != null) null else discovery.status.experienceState ?: ExperienceState.PEER_UNAVAILABLE,
            canRequest = candidate != null && (candidate.requestAvailableAtEpochMillis == null || remaining == 0L),
        )
    }
    val uiState: StateFlow<CandidateUiState> = combine(content, deps.drivingStateProvider.drivingState, request.state) { state, driving, sending ->
        state.copy(driving = driving, request = sending)
    }.stateIn(viewModelScope, meetSharing, CandidateUiState())

    fun requestConnection() {
        if (completed) return
        request.launch(viewModelScope,
            prepare = { RequestConnectionRequest(freshMeta(), candidateId) },
            permitted = { deps.allowsInteraction() },
            execute = { payload ->
                val discovery = deps.discoveryRepository.discovery.first()
                val candidate = discovery.candidates.firstOrNull { it.id == candidateId }
                val availableAt = candidate?.requestAvailableAtEpochMillis
                val cooldown = availableAt?.let {
                    deps.serverTimeProvider.serverTime.first()?.remainingMillisUntil(it, deps.serverTimeProvider.monotonicTimeMillis())
                }
                when {
                    discovery.status == DiscoveryStatus.ALREADY_CONNECTED -> AppResult.Failure(AppError.AlreadyClosed)
                    discovery.mode != DiscoveryMode.ON || discovery.status != DiscoveryStatus.AVAILABLE || candidate == null -> AppResult.Failure(AppError.PeerUnavailable)
                    availableAt != null && cooldown != 0L -> AppResult.Failure(AppError.PeerUnavailable)
                    !deps.allowsInteraction() -> AppResult.Failure(AppError.Restricted)
                    else -> deps.connectionRepository.requestConnection(payload)
                }
            },
            onSuccess = { result ->
                completed = true
                (result as? ConnectionRequest)?.let { navigation.trySend(it.id) }
            },
        )
    }
}
