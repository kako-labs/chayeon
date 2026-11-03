package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.ExcludeTripRequest
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.MarkRecapSeenRequest
import dev.chayeon.core.model.MySignature
import dev.chayeon.core.model.PublishSignatureRequest
import dev.chayeon.core.model.TripRecap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class RecapCommandState(
    val excluding: Boolean = false,
    val markingSeen: Boolean = false,
    val error: AppError? = null,
    val excluded: TripRecap? = null,
    val openSignature: Boolean = false,
    val openVisibility: Boolean = false,
    val pendingExclusion: Boolean = false,
)

private class ExclusionCommand(
    val request: ExcludeTripRequest,
    val recap: TripRecap,
    var signature: MySignature? = null,
    var publish: PublishSignatureRequest? = null,
)

class TripRecapViewModel(private val deps: SignatureDeps, private val recapId: String) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val command = MutableStateFlow(RecapCommandState())
    private var exclusion: ExclusionCommand? = null
    private var seenRequest: MarkRecapSeenRequest? = null
    private var excluding = false
    private var markingSeen = false
    private var visibilityDestination = false

    val uiState: StateFlow<TripRecapUiState> = combine(
        deps.drivingStateProvider.drivingState.catch { emit(DrivingState()) },
        observeAgain(refresh) { deps.signatureRepository.observeRecap(recapId) },
        command,
    ) { driving, recap, action ->
        val observed = (recap as? LoadState.Ready)?.value
        val shown = if (observed != null && action.excluded != null && observed.included) {
            LoadState.Ready(action.excluded, action.excluded.version)
        } else recap
        TripRecapUiState(
            driving = driving,
            recap = if (driving.policy == DrivingPolicy.ALLOWED) shown else LoadState.Loading(),
            excluding = action.excluding,
            markingSeen = action.markingSeen,
            error = action.error,
            openSignature = action.openSignature && driving.policy == DrivingPolicy.ALLOWED,
            openVisibility = action.openVisibility && driving.policy == DrivingPolicy.ALLOWED,
            pendingChanges = action.pendingExclusion,
        )
    }.stateIn(viewModelScope, signatureSharing, TripRecapUiState())

    fun excludeTrip() {
        if (excluding || markingSeen || (command.value.openSignature || command.value.openVisibility)) return
        excluding = true
        viewModelScope.launch {
            try {
                if (!deps.allowedNow()) { fail(AppError.Restricted); return@launch }
                if (exclusion == null) {
                    val recap = currentRecap() ?: return@launch
                    if (!recap.included || command.value.excluded != null) return@launch
                    exclusion = ExclusionCommand(ExcludeTripRequest(commandMeta(), recap.tripId), recap)
                }
                executeExclusion()
            } finally {
                excluding = false
            }
        }
    }

    private suspend fun executeExclusion() {
        val active = exclusion ?: return
        command.value = command.value.copy(excluding = true, error = null, pendingExclusion = true)
        if (active.signature == null) {
            if (!deps.allowedNow()) { fail(AppError.Restricted); return }
            when (val result = commandResult { deps.signatureRepository.excludeTrip(active.request) }) {
                is AppResult.Failure -> { fail(result.error); return }
                is AppResult.Success -> {
                    active.signature = result.value
                    command.value = command.value.copy(excluded = active.recap.copy(
                        included = false, changes = emptyList(), currentKnot = result.value.knotSeed, firstSignature = false,
                    ))
                }
            }
        }
        while (exclusion === active) {
            val observation = deps.signatureRepository.signature.currentObservation()
            if (observation is LoadState.Failed) { fail(observation.error); return }
            if (observation is LoadState.Loading) { fail(AppError.Unknown); return }
            val latest = (observation as? LoadState.Ready)?.value
            val local = active.signature ?: return
            val current = latest?.takeIf { it.version > local.version } ?: local
            val projection = signaturePreview(current, current.visibility)
            if (active.publish == null || !samePublicContent(active.publish?.signature, projection)) {
                active.publish = PublishSignatureRequest(commandMeta(), projection)
            }
            if (!deps.allowedNow()) { fail(AppError.Restricted); return }
            val request = active.publish ?: return
            when (val result = commandResult { deps.profileRepository.publishSignature(request) }) {
                is AppResult.Failure -> { fail(result.error); return }
                is AppResult.Success -> {
                    val observationAfter = deps.signatureRepository.signature.currentObservation()
                    if (observationAfter is LoadState.Failed) { fail(observationAfter.error); return }
                    if (observationAfter is LoadState.Loading) { fail(AppError.Unknown); return }
                    val after = (observationAfter as? LoadState.Ready)?.value
                    if (after != null && after.version > current.version &&
                        !samePublicContent(signaturePreview(after, after.visibility), request.signature)) continue
                    exclusion = null
                    command.value = command.value.copy(excluding = false, error = null, pendingExclusion = false)
                }
            }
        }
    }

    fun markSeen(openVisibility: Boolean = false) {
        if (excluding || markingSeen || exclusion != null || (command.value.openSignature || command.value.openVisibility)) return
        markingSeen = true
        visibilityDestination = openVisibility
        viewModelScope.launch {
            try {
                if (!deps.allowedNow()) { fail(AppError.Restricted); return@launch }
                val recap = currentRecap() ?: return@launch
                if (recap.seen) {
                    val allowed = deps.allowedNow()
                    command.value = command.value.copy(openSignature = allowed && !visibilityDestination, openVisibility = allowed && visibilityDestination,
                        error = if (allowed) null else AppError.Restricted)
                    return@launch
                }
                val request = seenRequest ?: MarkRecapSeenRequest(commandMeta(), recapId).also { seenRequest = it }
                command.value = command.value.copy(markingSeen = true, error = null)
                if (!deps.allowedNow()) { fail(AppError.Restricted); return@launch }
                when (val result = commandResult { deps.signatureRepository.markRecapSeen(request) }) {
                    is AppResult.Failure -> fail(result.error)
                    is AppResult.Success -> {
                        val allowed = deps.allowedNow()
                        if (allowed) seenRequest = null
                        command.value = command.value.copy(markingSeen = false,
                            error = if (allowed) null else AppError.Restricted, openSignature = allowed && !visibilityDestination, openVisibility = allowed && visibilityDestination)
                    }
                }
            } finally {
                markingSeen = false
            }
        }
    }

    fun retry() {
        when {
            exclusion != null -> excludeTrip()
            seenRequest != null -> markSeen(visibilityDestination)
            else -> viewModelScope.launch { if (deps.allowedNow()) refresh.value += 1 }
        }
    }

    fun consumeNavigation() {
        command.value = command.value.copy(openSignature = false, openVisibility = false)
    }

    private suspend fun currentRecap(): TripRecap? = when (val state = deps.signatureRepository.observeRecap(recapId).currentObservation()) {
        is LoadState.Ready -> state.value.also { if (it == null) fail(AppError.AlreadyClosed) }
        is LoadState.Failed -> { fail(state.error); null }
        is LoadState.Loading -> { fail(AppError.Unknown); null }
    }

    private fun fail(error: AppError) {
        command.value = command.value.copy(excluding = false, markingSeen = false, error = error)
    }
}
