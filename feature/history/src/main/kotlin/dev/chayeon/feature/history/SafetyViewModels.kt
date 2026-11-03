package dev.chayeon.feature.history

import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.update

class SafetyViewModel(deps: HistoryDeps, val peerProfileId: String? = null) : HistoryViewModel(deps) {
    init {
        observeSession()
        observe(deps.safetyRepository.blocks) { state, blocks ->
            val wasBlocked = state.blocks.valueOrNull()?.any { it.peer.id == peerProfileId } == true
            val nowUnblocked = blocks.valueOrNull()?.none { it.peer.id == peerProfileId } == true
            if (wasBlocked && nowUnblocked) resetCommand("block")
            state.copy(blocks = blocks)
        }
    }
    fun selectReason(reason: ReportReason) = edit {
        if (!busy("report") && reason != content.value.selectedReason) { resetCommand("report"); content.update { it.copy(selectedReason = reason) } }
    }
    fun askBlock() {
        val id = peerProfileId ?: return
        confirm(HistoryConfirmation(ConfirmationKind.BLOCK, id, "이 사람을 차단할까요?",
            "서로의 카드와 요청이 사라지고, 일시 연결과 이어진 대화가 닫혀요. 상대에게 이유를 알리지 않아요. 해제해도 이전 대화와 관계는 돌아오지 않아요.", "block"))
    }
    fun askUnblock(block: Block) = confirm(HistoryConfirmation(ConfirmationKind.UNBLOCK, block.id,
        "차단을 해제할까요?", "서로 다시 발견될 수 있어요. 이전 대화와 관계는 돌아오지 않아요.", "unblock:${block.id}"))
    fun confirmAction() {
        val confirmation = content.value.confirmation ?: return
        when (confirmation.kind) {
            ConfirmationKind.BLOCK -> {
                val request = BlockPeerRequest(newMeta(), confirmation.targetId)
                command(confirmation.commandKey, run = { deps.safetyRepository.block(request) })
            }
            ConfirmationKind.UNBLOCK -> {
                val request = UnblockPeerRequest(newMeta(), confirmation.targetId)
                command(confirmation.commandKey, run = { deps.safetyRepository.unblock(request) })
            }
            else -> Unit
        }
    }
    fun report() {
        val id = peerProfileId ?: return
        val request = ReportPeerRequest(newMeta(), id, content.value.selectedReason)
        command("report", run = { deps.safetyRepository.report(request) })
    }
}

class CleanupViewModel(deps: HistoryDeps) : HistoryViewModel(deps) {
    init {
        observeSession()
        observeRelationships()
        observe(deps.connectionRepository.connection) { state, connection -> state.copy(connection = connection) }
        observe(deps.discoveryRepository.discovery) { state, discovery -> state.copy(discovery = discovery) }
        observe(deps.encounterRepository.encounters) { state, encounters -> state.copy(encounters = encounters) }
    }
    fun ask(kind: ConfirmationKind, targetId: String = "") {
        val copy = cleanupCopy(kind, content.value.policy?.reportRetentionDays)
        val key = "cleanup:$kind:$targetId"
        if (commandState(key).completed) resetCommand(key)
        confirm(HistoryConfirmation(kind, targetId, when (kind) {
            ConfirmationKind.DISCOVERY_OFF -> "발견을 끌까요?"
            ConfirmationKind.LEAVE -> "일시 연결에서 나갈까요?"
            ConfirmationKind.END_RELATIONSHIP -> "관계를 끝낼까요?"
            ConfirmationKind.DELETE_RECORDS -> "내 기록을 모두 삭제할까요?"
            ConfirmationKind.SIGN_OUT -> "로그아웃할까요?"
            ConfirmationKind.DELETE_ACCOUNT -> "계정을 삭제할까요?"
            else -> "정리할까요?"
        }, "${copy.removes}\n${copy.keeps}", "cleanup:$kind:$targetId"))
    }
    fun execute() {
        val confirmation = content.value.confirmation ?: return
        val meta = newMeta()
        val id = confirmation.targetId
        command(confirmation.commandKey, allowExit = confirmation.kind == ConfirmationKind.LEAVE, run = {
            when (confirmation.kind) {
                ConfirmationKind.DISCOVERY_OFF -> deps.discoveryRepository.setDiscovery(SetDiscoveryRequest(meta, DiscoveryMode.OFF))
                ConfirmationKind.LEAVE -> deps.connectionRepository.leave(LeaveConnectionRequest(meta, id))
                ConfirmationKind.END_RELATIONSHIP -> deps.afterRepository.endRelationship(EndAfterConnectionRequest(meta, id))
                ConfirmationKind.DELETE_RECORDS -> deps.encounterRepository.deleteAllEncounters(DeleteAllEncountersRequest(meta))
                ConfirmationKind.SIGN_OUT -> deps.sessionRepository.signOut(SignOutRequest(meta))
                ConfirmationKind.DELETE_ACCOUNT -> deps.sessionRepository.deleteAccount(DeleteAccountRequest(meta))
                else -> AppResult.Failure(AppError.Unknown)
            }
        })
    }

    fun leaveWhileLocked() {
        val connection = content.value.connection.valueOrNull()?.takeIf { it.status != ConnectionStatus.ENDED } ?: return
        val request = LeaveConnectionRequest(newMeta(), connection.id)
        command("leave", allowExit = true, run = { deps.connectionRepository.leave(request) })
    }
}

data class CleanupCopy(val title: String, val removes: String, val keeps: String)
fun cleanupCopy(kind: ConfirmationKind, retentionDays: Int?): CleanupCopy = when (kind) {
    ConfirmationKind.DISCOVERY_OFF -> CleanupCopy("발견 끄기", "새 노출과 요청이 멈추고, 진행 중인 일시 연결도 끝나요.", "스친 인연과 이어진 인연은 남아요.")
    ConfirmationKind.LEAVE -> CleanupCopy("일시 연결 나가기", "지금 연결과 인사가 끝나요.", "발견 상태는 그대로이고, 스친 기록이 남아요.")
    ConfirmationKind.END_RELATIONSHIP -> CleanupCopy("관계 끝내기", "이어진 대화가 닫히고 다시 열리지 않아요.", "처음 스친 기록은 남아요.")
    ConfirmationKind.DELETE_RECORDS -> CleanupCopy("내 기록 삭제", "내 스친 기록이 모두 사라져요. 되돌릴 수 없어요.", "상대의 기록과 차단 상태, 이어진 인연은 그대로예요.")
    ConfirmationKind.SIGN_OUT -> CleanupCopy("로그아웃", "이 차량의 프로필, 대화, 로그인 정보와 주행 지표를 지워요.", "계정과 서버의 기록은 남아요.")
    ConfirmationKind.DELETE_ACCOUNT -> CleanupCopy("계정 삭제", "프로필, 내 기록과 이어진 관계를 지워요. 이 차량의 주행 지표도 지우며 되돌릴 수 없어요.",
        if (retentionDays == null) "신고 접수 기록은 별도로 남을 수 있어요." else "신고 접수 기록은 ${retentionDays}일 동안 제한 보관해요.")
    else -> CleanupCopy("정리", "선택한 항목을 정리해요.", "다른 기록은 그대로예요.")
}
