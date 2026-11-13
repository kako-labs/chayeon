package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.*

class EndedViewModel(deps: MeetDeps, connectionId: String) : ViewModel() {
    val uiState: StateFlow<EndedUiState> = combine(
        deps.connectionRepository.connection, deps.profileRepository.profile, deps.drivingStateProvider.drivingState,
    ) { observation, profile, driving ->
        EndedUiState(
            driving = driving,
            connection = observation.readyValue()?.takeIf { it.id == connectionId && it.status == ConnectionStatus.ENDED },
            ownProfile = profile.publicProfile(),
            loading = observation is LoadState.Loading,
            observationError = (observation as? LoadState.Failed)?.error,
        )
    }.stateIn(viewModelScope, meetSharing, EndedUiState())
}
