package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyProfileViewModel(private val deps: SignatureDeps) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val updatingCallSetting = MutableStateFlow(false)
    private val callSettingError = MutableStateFlow<AppError?>(null)
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
    }.combine(updatingCallSetting) { state, busy -> state.copy(updatingCallSetting = busy) }
        .combine(callSettingError) { state, error -> state.copy(callSettingError = error) }
        .stateIn(viewModelScope, signatureSharing, MyProfileUiState())

    fun setReceiveRequestsWhileDriving(enabled: Boolean) = setDrivingConnectMode(if (enabled) DrivingConnectMode.RING else DrivingConnectMode.OFF)

    fun setDrivingConnectMode(mode: DrivingConnectMode) = changeCallSettings { it.copy(drivingConnectMode = mode) }

    fun setMinimumScore(trait: TraitId, minimum: Int?) {
        if (!trait.isScored || (minimum != null && (minimum !in 60..100 || minimum % 5 != 0))) return
        changeCallSettings { current ->
            current.copy(autoConnectConditions = current.autoConnectConditions.copy(minimumScores =
                if (minimum == null) current.autoConnectConditions.minimumScores - trait
                else current.autoConnectConditions.minimumScores + (trait to minimum)))
        }
    }

    fun setRequireSharedInterest(required: Boolean) = changeCallSettings {
        it.copy(autoConnectConditions = it.autoConnectConditions.copy(requireSharedInterest = required))
    }

    private fun changeCallSettings(transform: (ParticipationSettings) -> ParticipationSettings) {
        if (updatingCallSetting.value) return
        updatingCallSetting.value = true
        viewModelScope.launch {
            try {
                if (!deps.allowedNow()) return@launch
                val current = (deps.profileRepository.settings.first() as? LoadState.Ready)?.value ?: return@launch
                val desired = transform(current)
                val result = commandResult {
                    if (!deps.allowedNow()) AppResult.Failure(AppError.Restricted)
                    else deps.profileRepository.updateSettings(UpdateSettingsRequest(commandMeta(current.version),
                        current.notifyRequests, desired.drivingConnectMode != DrivingConnectMode.OFF,
                        desired.drivingConnectMode, desired.autoConnectConditions))
                }
                callSettingError.value = (result as? AppResult.Failure)?.error
            } finally { updatingCallSetting.value = false }
        }
    }

    fun retry() {
        viewModelScope.launch { if (deps.allowedNow()) refresh.value += 1 }
    }
}
