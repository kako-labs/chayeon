package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.DrivingState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SignatureUiState(val driving: DrivingState = DrivingState())

class SignatureViewModel(deps: SignatureDeps) : ViewModel() {
    val uiState: StateFlow<SignatureUiState> = deps.drivingStateProvider.drivingState
        .map { SignatureUiState(driving = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignatureUiState())
}
