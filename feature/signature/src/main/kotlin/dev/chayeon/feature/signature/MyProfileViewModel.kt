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

class MyProfileViewModel(private val deps: SignatureDeps) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val uiState: StateFlow<MyProfileUiState> = combine(
        deps.drivingStateProvider.drivingState.catch { emit(DrivingState()) },
        observeAgain(refresh) { deps.profileRepository.profile },
        observeAgain(refresh) { deps.signatureRepository.signature },
        observeAgain(refresh) { deps.profileRepository.settings },
        deps.vehicleConsole.consoleState.catch { emit(ConsoleState()) },
    ) { driving, profile, signature, settings, console ->
        val allowed = driving.policy == DrivingPolicy.ALLOWED
        MyProfileUiState(
            driving = driving,
            profile = if (allowed) profile else LoadState.Loading(),
            signature = if (allowed) signature else LoadState.Loading(),
            settings = if (allowed) settings else LoadState.Loading(),
            consoleAvailable = console.available,
        )
    }.stateIn(viewModelScope, signatureSharing, MyProfileUiState())

    fun retry() {
        viewModelScope.launch { if (deps.allowedNow()) refresh.value += 1 }
    }
}
