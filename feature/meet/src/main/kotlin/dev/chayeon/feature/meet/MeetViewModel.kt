package dev.chayeon.feature.meet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.DrivingState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MeetUiState(val driving: DrivingState = DrivingState())

class MeetViewModel(deps: MeetDeps) : ViewModel() {
    val uiState: StateFlow<MeetUiState> = deps.drivingStateProvider.drivingState
        .map { MeetUiState(driving = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetUiState())
}
