package dev.chayeon.feature.history

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first

class RelationshipsViewModel(deps: HistoryDeps, peerId: String? = null) : HistoryViewModel(deps) {
    init {
        content.update { it.copy(targetPeerId = peerId) }
        observeProfile()
        observeRequests()
        observeRelationships()
        observe(deps.encounterRepository.encounters) { state, encounters -> state.copy(encounters = encounters) }
        observe(deps.safetyRepository.blocks) { state, blocks -> state.copy(blocks = blocks) }
        observeCountdowns()
    }

    fun askDelete() {
        val encounter = content.value.person?.encounter ?: return
        confirm(HistoryConfirmation(ConfirmationKind.DELETE_ENCOUNTER, encounter.id,
            "이 기록을 삭제할까요?", "내 기록만 사라져요. 상대의 기록과 차단 상태는 바뀌지 않아요. 삭제한 기록은 되돌릴 수 없어요.", "delete"))
    }

    fun askEnd() {
        val relationship = content.value.person?.relationship?.takeIf { it.status == AfterConnectionStatus.CONNECTED } ?: return
        confirm(HistoryConfirmation(ConfirmationKind.END_RELATIONSHIP, relationship.id, "관계를 끝낼까요?",
            "차연에서 공유한 아이디가 더 이상 보이지 않아요. 이미 전달된 아이디는 되돌릴 수 없어요. 처음 스친 기록은 남아요.", "end:${relationship.id}"))
    }

    fun confirmAction() {
        val confirmation = content.value.confirmation ?: return
        when (confirmation.kind) {
            ConfirmationKind.DELETE_ENCOUNTER -> {
                val request = DeleteEncounterRequest(newMeta(), confirmation.targetId)
                command(confirmation.commandKey, run = { deps.encounterRepository.deleteEncounter(request) })
            }
            ConfirmationKind.END_RELATIONSHIP -> {
                val request = EndAfterConnectionRequest(newMeta(), confirmation.targetId)
                command(confirmation.commandKey, run = { deps.afterRepository.endRelationship(request) })
            }
            else -> Unit
        }
    }
}

class AfterRequestViewModel(deps: HistoryDeps, val encounterId: String) : HistoryViewModel(deps) {
    init {
        content.update { it.copy(targetEncounterId = encounterId) }
        observe(deps.homeZoneRepository.state) { state, home -> state.copy(homeSuppressed = home.suppressed) }
        observe(deps.encounterRepository.observeEncounter(encounterId)) { state, encounter -> state.copy(encounter = encounter) }
        observe(deps.profileRepository.profile) { state, profile ->
            state.copy(profile = profile, contactsLoaded = state.contactsLoaded || profile is LoadState.Ready)
        }
        observeRequests()
        observeCountdowns()
    }

    fun setContacts(contacts: ContactIds) = edit {
        if (!busy("request") && !content.value.requests.valueOrNull().orEmpty().any { busy("respond:${it.id}") }) {
            resetCommand("request")
            content.value.requests.valueOrNull().orEmpty().forEach { resetCommand("respond:${it.id}") }
            content.update { it.copy(shareContacts = contacts, contactError = null) }
        }
    }

    fun setSaveToProfile(save: Boolean) = edit {
        if (!busy("request") && !content.value.requests.valueOrNull().orEmpty().any { busy("respond:${it.id}") }) {
            resetCommand("request")
            content.value.requests.valueOrNull().orEmpty().forEach { resetCommand("respond:${it.id}") }
            content.update { it.copy(saveContactsToProfile = save) }
        }
    }

    private fun validatedContacts(): ContactIds? {
        val contacts = content.value.shareContacts.normalized()
        if (!contacts.isValid(requireAtLeastOne = true)) {
            content.update { it.copy(contactError = "공유할 아이디를 하나 이상 올바르게 입력해 주세요") }
            return null
        }
        return contacts
    }

    fun request() {
        val state = content.value
        val contacts = validatedContacts() ?: return
        val previous = state.requests.valueOrNull()?.filter { it.encounterId == encounterId }?.maxByOrNull { it.createdAtEpochMillis }
        if (previous != null && previous.status != AfterRequestStatus.PENDING && previous.status != AfterRequestStatus.ACCEPTED && state.encounter.valueOrNull()?.afterEligibility == AfterEligibility.AVAILABLE && commandState("request").completed) resetCommand("request")
        val request = RequestAfterRequest(newMeta(), encounterId, contacts, state.saveContactsToProfile)
        command("request", run = { deps.afterRepository.requestAfter(request) })
    }

    fun respond(requestId: String, decision: RequestDecision) {
        val contacts = if (decision == RequestDecision.ACCEPT) validatedContacts() ?: return else ContactIds()
        val request = RespondAfterRequest(newMeta(), requestId, decision, contacts, content.value.saveContactsToProfile)
        command("respond:$requestId", run = {
            if (decision == RequestDecision.ACCEPT && deps.homeZoneRepository.state.first().suppressed) AppResult.Failure(AppError.PeerUnavailable)
            else if (deps.drivingStateProvider.drivingState.first().policy != DrivingPolicy.ALLOWED) AppResult.Failure(AppError.Restricted)
            else deps.afterRepository.respond(request)
        })
    }

    fun askWithdraw(requestId: String) = confirm(HistoryConfirmation(ConfirmationKind.WITHDRAW, requestId,
        "애프터 신청을 철회할까요?", "이 신청은 닫히고 다시 수락할 수 없어요. 스친 기록은 남아요.", "withdraw:$requestId"))

    fun withdraw() {
        val confirmation = content.value.confirmation?.takeIf { it.kind == ConfirmationKind.WITHDRAW } ?: return
        val request = WithdrawAfterRequest(newMeta(), confirmation.targetId)
        command(confirmation.commandKey, run = { deps.afterRepository.withdraw(request) })
    }
}
