package dev.chayeon.feature.history

import androidx.lifecycle.viewModelScope
import dev.chayeon.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EncountersViewModel(deps: HistoryDeps) : HistoryViewModel(deps) {
    init {
        observe(deps.encounterRepository.encounters) { state, encounters -> state.copy(encounters = encounters) }
        observeRequests()
    }
}

class EncounterDetailViewModel(deps: HistoryDeps, val encounterId: String) : HistoryViewModel(deps) {
    init {
        observe(deps.encounterRepository.observeEncounter(encounterId)) { state, encounter -> state.copy(encounter = encounter) }
        observeProfile()
        observeRequests()
        observeCountdowns()
    }
    fun askDelete() = confirm(HistoryConfirmation(ConfirmationKind.DELETE_ENCOUNTER, encounterId,
        "이 기록을 삭제할까요?", "내 기록만 사라져요. 상대의 기록과 차단 상태는 바뀌지 않아요. 삭제한 기록은 되돌릴 수 없어요.", "delete"))
    fun delete() {
        if (content.value.confirmation?.kind != ConfirmationKind.DELETE_ENCOUNTER) return
        val request = DeleteEncounterRequest(newMeta(), encounterId)
        command("delete", run = { deps.encounterRepository.deleteEncounter(request) })
    }
}

class AfterRequestViewModel(deps: HistoryDeps, val encounterId: String) : HistoryViewModel(deps) {
    init {
        content.update { it.copy(targetEncounterId = encounterId) }
        observe(deps.encounterRepository.observeEncounter(encounterId)) { state, encounter -> state.copy(encounter = encounter) }
        observeProfile()
        observeRequests()
        observeCountdowns()
    }
    fun request() {
        val state = content.value
        val previous = state.requests.valueOrNull()?.filter { it.encounterId == encounterId }?.maxByOrNull { it.version }
        if (previous != null && previous.status != AfterRequestStatus.PENDING && previous.status != AfterRequestStatus.ACCEPTED && state.encounter.valueOrNull()?.afterEligibility == AfterEligibility.AVAILABLE && commandState("request").completed) resetCommand("request")
        val request = RequestAfterRequest(newMeta(), encounterId)
        command("request", run = { deps.afterRepository.requestAfter(request) })
    }
    fun respond(requestId: String, decision: RequestDecision) {
        val request = RespondAfterRequest(newMeta(), requestId, decision)
        command("respond:$requestId", run = { deps.afterRepository.respond(request) })
    }
    fun askWithdraw(requestId: String) = confirm(HistoryConfirmation(ConfirmationKind.WITHDRAW, requestId,
        "애프터 신청을 철회할까요?", "이 신청은 닫히고 다시 수락할 수 없어요. 스친 기록은 남아요.", "withdraw:$requestId"))
    fun withdraw() {
        val confirmation = content.value.confirmation?.takeIf { it.kind == ConfirmationKind.WITHDRAW } ?: return
        val request = WithdrawAfterRequest(newMeta(), confirmation.targetId)
        command(confirmation.commandKey, run = { deps.afterRepository.withdraw(request) })
    }
}

class AfterConnectionsViewModel(deps: HistoryDeps) : HistoryViewModel(deps) {
    private var messagesJob: Job? = null
    private var observedRelationship: String? = null

    init {
        observeProfile()
        observeRequests()
        viewModelScope.launch {
            deps.afterRepository.connections.collect { relationships ->
                val selected = relationships.valueOrNull()?.let { list ->
                    list.firstOrNull { it.id == content.value.selectedRelationshipId }?.id
                        ?: list.firstOrNull { it.status == AfterConnectionStatus.CONNECTED }?.id
                }
                content.update { state -> state.copy(relationships = relationships, selectedRelationshipId = selected,
                    messages = if (selected == state.selectedRelationshipId) state.messages else LoadState.Loading()) }
                if (selected != observedRelationship) observeMessages(selected)
            }
        }
    }

    private fun observeMessages(id: String?) {
        messagesJob?.cancel()
        observedRelationship = id
        if (id != null) messagesJob = viewModelScope.launch {
            deps.afterRepository.observeMessages(id).collect { messages ->
                content.update { state ->
                    if (state.selectedRelationshipId != id) state else state.copy(messages = messages,
                        outbox = state.outbox.filterNot { pending -> messages.valueOrNull().orEmpty().any {
                            it.clientMessageId == pending.request.clientMessageId && it.delivery == DeliveryState.SENT
                        } })
                }
            }
        }
    }

    fun select(id: String) = edit {
        if (content.value.relationships.valueOrNull().orEmpty().any { it.id == id }) {
            content.update { it.copy(selectedRelationshipId = id, messages = LoadState.Loading()) }
            observeMessages(id)
        }
    }

    fun setText(text: String) = edit {
        val connection = content.value.selectedRelationship ?: return@edit
        if (connection.status == AfterConnectionStatus.CONNECTED && text.characterCount() <= AFTER_TEXT_LIMIT) {
            content.update { it.copy(messageDrafts = it.messageDrafts + (connection.id to text)) }
        }
    }

    fun sendText() {
        val state = content.value
        val id = state.selectedRelationshipId ?: return
        val text = state.messageDrafts[id].orEmpty().trim()
        if (text.isBlank() || text.characterCount() > AFTER_TEXT_LIMIT || busy("compose:$id")) return
        send(id, AfterMessageContent.Text(text), "compose:$id")
    }

    fun sendPreset(code: PresetCode) {
        val relationship = content.value.selectedRelationship ?: return
        if (code !in relationship.availablePresets || busy("preset:${relationship.id}:$code")) return
        send(relationship.id, AfterMessageContent.Preset(code), "preset:${relationship.id}:$code")
    }

    private fun send(id: String, message: AfterMessageContent, key: String) {
        val existing = content.value.outbox.firstOrNull { it.request.afterConnectionId == id && it.request.content == message && it.delivery != DeliveryState.SENT }
        if (existing != null) {
            if (existing.delivery == DeliveryState.FAILED) retryMessage(existing.request.clientMessageId)
            return
        }
        val meta = newMeta()
        val request = SendAfterMessageRequest(meta, id, meta.commandId, message)
        sendPrepared(request, key)
    }

    private fun sendPrepared(request: SendAfterMessageRequest, key: String) {
        val id = request.afterConnectionId
        val message = request.content
        val messageKey = "message:${request.clientMessageId}"
        content.update { it.copy(sendCommands = it.sendCommands + (key to messageKey)) }
        command(messageKey, run = {
            val current = deps.afterRepository.connections.first().valueOrNull()?.firstOrNull { it.id == id }
            if (current?.status != AfterConnectionStatus.CONNECTED) return@command AppResult.Failure(AppError.AlreadyClosed)
            if (deps.drivingStateProvider.drivingState.first().policy != DrivingPolicy.ALLOWED) return@command AppResult.Failure(AppError.Restricted)
            content.update { state -> state.copy(outbox = state.outbox.filterNot { it.request.clientMessageId == request.clientMessageId } + PendingAfterMessage(request)) }
            val result = deps.afterRepository.sendMessage(request)
            content.update { state -> state.copy(outbox = state.outbox.map {
                if (it.request.clientMessageId == request.clientMessageId) it.copy(delivery = if (result is AppResult.Success) DeliveryState.SENT else DeliveryState.FAILED) else it
            }) }
            result
        }, success = {
            content.update { state ->
                val unchanged = (message as? AfterMessageContent.Text)?.text == state.messageDrafts[id]?.trim()
                state.copy(messageDrafts = if (unchanged) state.messageDrafts - id else state.messageDrafts)
            }
        })
    }

    fun retryMessage(clientMessageId: String) {
        val state = content.value
        val pending = state.outbox.firstOrNull { it.request.clientMessageId == clientMessageId }?.request
        val observed = state.messages.valueOrNull()?.firstOrNull {
            it.clientMessageId == clientMessageId && it.author == MessageAuthor.SELF && it.delivery == DeliveryState.FAILED
        }
        val request = pending ?: observed?.let {
            SendAfterMessageRequest(CommandMeta(it.clientMessageId), it.afterConnectionId, it.clientMessageId, it.content)
        } ?: return
        val key = when (val payload = request.content) {
            is AfterMessageContent.Text -> "compose:${request.afterConnectionId}"
            is AfterMessageContent.Preset -> "preset:${request.afterConnectionId}:${payload.code}"
        }
        if (pending == null) sendPrepared(request, key) else retry("message:${request.clientMessageId}")
    }

    fun askEnd() {
        val relationship = content.value.selectedRelationship ?: return
        confirm(HistoryConfirmation(ConfirmationKind.END_RELATIONSHIP, relationship.id, "관계를 끝낼까요?",
            "이어진 대화가 닫히고 다시 열리지 않아요. 처음 스친 기록은 남아요.", "end:${relationship.id}"))
    }
    fun endRelationship() {
        val confirmation = content.value.confirmation?.takeIf { it.kind == ConfirmationKind.END_RELATIONSHIP } ?: return
        val request = EndAfterConnectionRequest(newMeta(), confirmation.targetId)
        command(confirmation.commandKey, run = { deps.afterRepository.endRelationship(request) })
    }

    companion object { const val AFTER_TEXT_LIMIT = 500 }
}
