package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.*

class HomeViewModel(private val deps: MeetDeps) : ViewModel() {
    private val toggle = MeetCommand<SetDiscoveryRequest>()
    private val refresh = MeetCommand<RefreshDiscoveryRequest>()
    private var toggleTarget: DiscoveryMode? = null

    private val content = combine(
        deps.discoveryRepository.discovery,
        deps.profileRepository.profile,
        deps.connectionRepository.requests,
        deps.homeZoneRepository.state,
        deps.demoPresence,
    ) { discovery, profile, requests, home, presence ->
        HomeUiState(
            homeZone = home,
            demoPresence = presence,
            discovery = discovery.copy(candidates = if (!home.suppressed && discovery.status == DiscoveryStatus.AVAILABLE && discovery.mode == DiscoveryMode.ON) discovery.candidates else emptyList()),
            profile = profile.publicProfile(),
            profileLoaded = profile is LoadState.Ready,
            pendingRequest = requests.readyValue()?.filter { !home.suppressed && it.status == ConnectionRequestStatus.PENDING }
                ?.maxByOrNull { it.version },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(content, deps.drivingStateProvider.drivingState, toggle.state, refresh.state) { state, driving, toggling, refreshing ->
        state.copy(driving = driving, toggle = toggling, refresh = refreshing)
    }.stateIn(viewModelScope, meetSharing, HomeUiState())

    fun setDiscovery(enabled: Boolean) {
        if (toggle.state.value.inProgress) return
        val mode = if (enabled) DiscoveryMode.ON else DiscoveryMode.OFF
        if (toggleTarget != mode) toggle.discardRetry()
        toggleTarget = mode
        toggle.launch(viewModelScope,
            prepare = { SetDiscoveryRequest(freshMeta(deps.discoveryRepository.discovery.first().version), mode) },
            permitted = { deps.allowsInteraction() },
            execute = deps.discoveryRepository::setDiscovery,
        )
    }

    fun refresh() = refresh.launch(viewModelScope,
        prepare = { RefreshDiscoveryRequest(freshMeta()) },
        permitted = { deps.allowsInteraction() },
        execute = deps.discoveryRepository::refresh,
    )
}
