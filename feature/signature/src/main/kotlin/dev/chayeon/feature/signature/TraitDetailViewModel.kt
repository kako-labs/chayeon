package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.TraitId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TraitDetailViewModel(private val deps: SignatureDeps, private val trait: TraitId) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val uiState: StateFlow<TraitDetailUiState> = combine(
        deps.drivingStateProvider.drivingState.catch { emit(DrivingState()) },
        observeAgain(refresh) { deps.signatureRepository.observeEvidence(trait) },
    ) { driving, evidence ->
        TraitDetailUiState(
            driving = driving,
            trait = trait,
            evidence = if (driving.policy == DrivingPolicy.ALLOWED) evidence else LoadState.Loading(),
        )
    }.stateIn(viewModelScope, signatureSharing, TraitDetailUiState(trait = trait))

    fun retry() {
        viewModelScope.launch { if (deps.allowedNow()) refresh.value += 1 }
    }
}
