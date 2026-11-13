package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.ConsoleState
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SignatureViewModel(private val deps: SignatureDeps) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val uiState: StateFlow<SignatureUiState> = combine(
        deps.drivingStateProvider.drivingState.catch { emit(DrivingState()) },
        observeAgain(refresh) { deps.signatureRepository.signature },
        deps.vehicleConsole.consoleState.catch { emit(ConsoleState()) },
        observeAgain(refresh) { deps.profileRepository.settings },
    ) { driving, signature, console, settings ->
        SignatureUiState(
            driving = driving,
            signature = if (driving.policy == DrivingPolicy.ALLOWED) signature else LoadState.Loading(),
            consoleAvailable = console.available,
            recordingEnabled = driving.policy == DrivingPolicy.ALLOWED && settings.readyValue()?.recordingEnabled == true,
        )
    }.stateIn(viewModelScope, signatureSharing, SignatureUiState())

    fun retry() {
        viewModelScope.launch { if (deps.allowedNow()) refresh.value += 1 }
    }
}
