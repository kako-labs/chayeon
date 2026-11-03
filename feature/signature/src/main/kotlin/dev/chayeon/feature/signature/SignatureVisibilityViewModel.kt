package dev.chayeon.feature.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.LoadState
import dev.chayeon.core.model.MySignature
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.PublishSignatureRequest
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.UpdateVisibilityRequest
import dev.chayeon.core.model.VisibilitySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class VisibilityCommandState(
    val draft: VisibilitySettings? = null,
    val preview: PublicSignature? = null,
    val saving: Boolean = false,
    val publishing: Boolean = false,
    val pending: Boolean = false,
    val error: AppError? = null,
)

private class VisibilityCommand(
    val update: UpdateVisibilityRequest,
    var desired: VisibilitySettings,
    var publish: PublishSignatureRequest,
    var saved: Boolean = false,
)

class SignatureVisibilityViewModel(private val deps: SignatureDeps) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val command = MutableStateFlow(VisibilityCommandState())
    private var pending: VisibilityCommand? = null
    private var executing = false

    val uiState: StateFlow<VisibilityUiState> = combine(
        deps.drivingStateProvider.drivingState.catch { emit(DrivingState()) },
        observeAgain(refresh) { deps.signatureRepository.signature },
        observeAgain(refresh) { deps.profileRepository.profile },
        command,
    ) { driving, signature, profile, action ->
        val ready = (signature as? LoadState.Ready)?.value
        val authoritative = ready?.visibility
        val superseded = authoritative != null && action.draft != null &&
            authoritative.version > action.draft.version && !authoritative.sameSelection(action.draft)
        val visible = if (superseded) authoritative else action.draft ?: authoritative
        val preview = when {
            ready == null || visible == null -> null
            action.pending && !superseded -> action.preview
            else -> signaturePreview(ready, visible)
        }
        val remote = (profile as? LoadState.Ready)?.value?.publicProfile?.signature
        val allowed = driving.policy == DrivingPolicy.ALLOWED
        VisibilityUiState(
            driving = driving,
            signature = if (allowed) signature else LoadState.Loading(),
            visibility = visible.takeIf { allowed },
            preview = preview.takeIf { allowed },
            savingVisibility = action.saving,
            publishing = action.publishing,
            error = action.error ?: (profile as? LoadState.Failed)?.error,
            published = remote != null,
            pendingChanges = action.pending || profile !is LoadState.Ready || !samePublicContent(remote, preview),
            publicationKnown = profile is LoadState.Ready,
        )
    }.stateIn(viewModelScope, signatureSharing, VisibilityUiState())

    fun setTraitVisible(trait: TraitId, visible: Boolean) = changeVisibility { current ->
        current.copy(visibleTraits = if (visible) current.visibleTraits + trait else current.visibleTraits - trait)
    }

    fun setPublished(published: Boolean) = changeVisibility { it.copy(publishSignature = published) }

    private fun changeVisibility(transform: (VisibilitySettings) -> VisibilitySettings) {
        if (executing) return
        executing = true
        viewModelScope.launch {
            try {
                if (!deps.allowedNow()) {
                    fail(AppError.Restricted)
                    return@launch
                }
                val signature = currentSignature() ?: return@launch
                val current = command.value.draft?.takeIf { it.version >= signature.visibility.version }
                    ?: signature.visibility
                val desired = transform(current)
                val remote = (deps.profileRepository.profile.currentObservation() as? LoadState.Ready)?.value?.publicProfile?.signature
                if (desired.sameSelection(current) && pending == null &&
                    samePublicContent(remote, signaturePreview(signature, desired))) return@launch
                val projection = signaturePreview(signature, desired)
                pending = VisibilityCommand(
                    update = UpdateVisibilityRequest(commandMeta(signature.version), desired.visibleTraits, desired.publishSignature),
                    desired = desired,
                    publish = PublishSignatureRequest(commandMeta(), projection),
                )
                command.value = VisibilityCommandState(draft = desired, preview = projection, saving = true, pending = true)
                executePending()
            } finally {
                executing = false
            }
        }
    }

    fun retry() {
        if (executing) return
        executing = true
        viewModelScope.launch {
            try {
                if (!deps.allowedNow()) {
                    fail(AppError.Restricted)
                    return@launch
                }
                command.value = command.value.copy(error = null)
                refresh.value += 1
                if (pending == null) {
                    reconcileCurrent()
                } else executePending()
            } finally {
                executing = false
            }
        }
    }

    private suspend fun reconcileCurrent() {
        val current = when (val observation = deps.signatureRepository.signature.currentObservation()) {
            is LoadState.Ready -> observation.value
            is LoadState.Failed -> { fail(observation.error); return }
            is LoadState.Loading -> return
        }
        val remote = when (val observation = deps.profileRepository.profile.currentObservation()) {
            is LoadState.Ready -> observation.value?.publicProfile?.signature
            is LoadState.Failed -> { fail(observation.error); return }
            is LoadState.Loading -> { fail(AppError.Unknown); return }
        }
        val projection = signaturePreview(current, current.visibility)
        if (samePublicContent(remote, projection)) {
            command.value = VisibilityCommandState()
            return
        }
        val desired = current.visibility
        pending = VisibilityCommand(
            UpdateVisibilityRequest(commandMeta(current.version), desired.visibleTraits, desired.publishSignature),
            desired, PublishSignatureRequest(commandMeta(), projection), saved = true,
        )
        command.value = VisibilityCommandState(draft = desired, preview = projection, publishing = true, pending = true)
        executePending()
    }

    private suspend fun currentSignature(): MySignature? = when (val state = deps.signatureRepository.signature.currentObservation()) {
        is LoadState.Ready -> state.value
        is LoadState.Failed -> { fail(state.error); null }
        is LoadState.Loading -> { fail(AppError.Unknown); null }
    }

    private suspend fun executePending() {
        val active = pending ?: return
        val latest = currentSignature() ?: return
        if (superseded(active, latest.visibility)) {
            replaceWithCurrent(active, latest)
        }
        if (!active.saved) {
            if (!deps.allowedNow()) { fail(AppError.Restricted); return }
            command.value = command.value.copy(saving = true, publishing = false, error = null)
            when (val result = commandResult { deps.signatureRepository.updateVisibility(active.update) }) {
                is AppResult.Failure -> { fail(result.error); return }
                is AppResult.Success -> {
                    active.saved = true
                    active.desired = result.value
                    command.value = command.value.copy(draft = result.value, saving = false)
                }
            }
        }
        publishCurrent(active)
    }

    private suspend fun publishCurrent(active: VisibilityCommand) {
        while (pending === active) {
            val latest = currentSignature() ?: return
            if (superseded(active, latest.visibility)) {
                replaceWithCurrent(active, latest)
            }
            val projection = signaturePreview(latest, active.desired)
            if (!samePublicContent(projection, active.publish.signature)) {
                active.publish = PublishSignatureRequest(commandMeta(), projection)
            }
            command.value = command.value.copy(preview = active.publish.signature, saving = false, publishing = true, error = null)
            if (!deps.allowedNow()) { fail(AppError.Restricted); return }
            when (val result = commandResult { deps.profileRepository.publishSignature(active.publish) }) {
                is AppResult.Failure -> { fail(result.error); return }
                is AppResult.Success -> {
                    val after = currentSignature() ?: return
                    if (superseded(active, after.visibility)) {
                        active.desired = after.visibility
                        active.publish = PublishSignatureRequest(commandMeta(), signaturePreview(after, after.visibility))
                        command.value = command.value.copy(draft = after.visibility, preview = active.publish.signature)
                        continue
                    }
                    val currentProjection = signaturePreview(after, active.desired)
                    if (!samePublicContent(currentProjection, active.publish.signature)) continue
                    pending = null
                    command.value = VisibilityCommandState()
                }
            }
        }
    }

    private fun superseded(active: VisibilityCommand, current: VisibilitySettings): Boolean =
        current.version > active.desired.version && !current.sameSelection(active.desired)

    private fun replaceWithCurrent(active: VisibilityCommand, signature: MySignature) {
        active.saved = true
        active.desired = signature.visibility
        active.publish = PublishSignatureRequest(commandMeta(), signaturePreview(signature, signature.visibility))
        command.value = command.value.copy(draft = signature.visibility, preview = active.publish.signature)
    }

    private fun fail(error: AppError) {
        command.value = command.value.copy(saving = false, publishing = false, error = error)
    }
}

internal fun samePublicContent(first: PublicSignature?, second: PublicSignature?): Boolean {
    if (first == null || second == null) return first == second
    return first.title == second.title && first.traits == second.traits && first.knotSeed == second.knotSeed &&
        first.source == second.source && first.evidenceBucket == second.evidenceBucket
}
