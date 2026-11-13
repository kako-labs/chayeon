package dev.chayeon.feature.history

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class FirstRunViewModel(deps: HistoryDeps) : HistoryViewModel(deps) {
    init {
        observeSession()
        observeProfile()
        observe(deps.sessionRepository.availableModes) { state, modes -> state.copy(modes = modes) }
    }

    fun markResumed() { content.update { it.copy(resumeHandled = true) } }

    fun selectMode(mode: SessionMode) = edit {
        if (mode in content.value.modes && !busy("enter")) {
            resetCommand("enter")
            content.update { it.copy(selectedMode = mode) }
        }
    }
    fun enterSoloIfNeeded() {
        val state = content.value
        if (state.modes == setOf(SessionMode.SOLO) && state.session is SessionState.SignedOut &&
            commandState("enter").error == null && !busy("enter") &&
            uiState.value.driving.policy == DrivingPolicy.ALLOWED) enter()
    }

    fun enter() {
        val state = content.value
        if (busy("enter")) return
        val request = EnterSessionRequest(newMeta(), state.selectedMode)
        command("enter", run = {
            if (request.mode !in deps.sessionRepository.availableModes.first()) AppResult.Failure(AppError.PeerUnavailable)
            else if (deps.drivingStateProvider.drivingState.first().policy != DrivingPolicy.ALLOWED) AppResult.Failure(AppError.Restricted)
            else deps.sessionRepository.enter(request)
        }, success = { content.update { it.copy(resumeHandled = false) } })
    }
}

class ProfileSetupViewModel(deps: HistoryDeps) : HistoryViewModel(deps) {
    init { observe(deps.sessionRepository.availableModes) { state, modes -> state.copy(modes = modes) } }
    fun consumeSavedNavigation() { resetCommand("save") }

    init {
        observeSession()
        observe(deps.profileRepository.profile) { state, profile ->
            val existing = profile.valueOrNull()?.publicProfile
            state.copy(profile = profile,
                draftLoaded = state.draftLoaded || profile is LoadState.Ready,
                draft = if (!state.draftLoaded && existing != null) ProfileDraft(existing.nickname, existing.avatar, existing.interests, profile.valueOrNull()?.contacts ?: ContactIds()) else state.draft)
        }
    }
    fun updateDraft(draft: ProfileDraft) = edit {
        val policy = content.value.policy ?: return@edit
        if (!busy("save") && draft.nickname.characterCount() <= policy.nicknameMaxLength && draft.interests.size <= policy.maximumInterests) {
            resetCommand("save")
            content.update { it.copy(draft = draft, draftError = null) }
        }
    }
    fun save() {
        val state = content.value
        val policy = state.policy ?: return
        val draft = state.draft.copy(nickname = state.draft.nickname.trim(), contacts = state.draft.contacts.normalized())
        if (!draft.contacts.isValid()) {
            content.update { it.copy(draftError = "아이디 형식을 확인해 주세요") }; return
        }
        if (draft.nickname.characterCount() !in policy.nicknameMinLength..policy.nicknameMaxLength || draft.interests.size > policy.maximumInterests) {
            content.update { it.copy(draftError = "닉네임은 ${policy.nicknameMinLength}~${policy.nicknameMaxLength}자로 입력해 주세요") }
            return
        }
        val request = SaveProfileRequest(newMeta(state.myProfile?.version), draft)
        command("save", run = { deps.profileRepository.saveProfile(request) })
    }
}

class ConsentViewModel(deps: HistoryDeps) : HistoryViewModel(deps) {
    init {
        observe(deps.voiceCallRepository.microphonePermission) { state, granted -> state.copy(microphoneGranted = granted) }
    }
    fun requestMicrophone() {
        viewModelScope.launch {
            if (deps.drivingStateProvider.drivingState.first().policy != DrivingPolicy.ALLOWED) return@launch
            val granted = deps.voiceCallRepository.requestMicrophone()
            content.update { it.copy(microphoneGranted = granted) }
        }
    }
    fun openHomeZone(action: () -> Unit) = edit {
        if (ConsentScope.entries.none { busy("consent:$it") }) action()
    }
    init {
        observeSession()
        observeProfile()
        observe(deps.profileRepository.settings) { state, settings -> state.copy(settings = settings) }
        observe(deps.discoveryRepository.discovery) { state, discovery -> state.copy(discovery = discovery) }
    }
    fun record(scope: ConsentScope, granted: Boolean) {
        val policy = content.value.policy ?: return
        val key = "consent:$scope"
        if (busy(key)) return
        val current = content.value.settings.valueOrNull()?.consents?.firstOrNull { it.scope == scope }
        if ((current?.status == ConsentStatus.GRANTED && current.documentVersion == policy.consentDocumentVersion) == granted) return
        if (commandState(key).error == null) resetCommand(key)
        val request = RecordConsentRequest(newMeta(), scope, if (granted) ConsentAction.GRANT else ConsentAction.REVOKE, policy.consentDocumentVersion)
        command(key, run = { deps.profileRepository.recordConsent(request) })
    }
    fun setDiscovery(enabled: Boolean) {
        val request = SetDiscoveryRequest(newMeta(), if (enabled) DiscoveryMode.ON else DiscoveryMode.OFF)
        if (!busy("discovery") && commandState("discovery").error == null) resetCommand("discovery")
        command("discovery", run = {
            val policy = (deps.sessionRepository.session.first() as? SessionState.Active)?.session?.policy
            if (enabled && (policy == null || !deps.profileRepository.settings.first().hasRequiredConsents(policy.consentDocumentVersion))) {
                AppResult.Failure(AppError.Restricted)
            } else if (deps.drivingStateProvider.drivingState.first().policy != DrivingPolicy.ALLOWED) {
                AppResult.Failure(AppError.Restricted)
            } else deps.discoveryRepository.setDiscovery(request)
        })
    }
}
