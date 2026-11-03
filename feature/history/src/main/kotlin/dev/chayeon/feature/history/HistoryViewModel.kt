package dev.chayeon.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.DrivingState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(val driving: DrivingState = DrivingState())

class HistoryViewModel(deps: HistoryDeps) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = deps.drivingStateProvider.drivingState
        .map { HistoryUiState(driving = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0), HistoryUiState())
}
