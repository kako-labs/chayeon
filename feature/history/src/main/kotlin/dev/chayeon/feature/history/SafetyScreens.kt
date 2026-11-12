package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun SafetyScreen(state: HistoryUiState, chrome: ScreenChrome, peerProfileId: String,
    onReason: (ReportReason) -> Unit, onReport: () -> Unit, onBlock: () -> Unit, onBlocks: () -> Unit,
    onRetry: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onBack: () -> Unit) {
    val blocked = state.blocks.valueOrNull()?.any { it.peer.id == peerProfileId } == true
    val peer = state.safetyPeer?.takeIf { it.id == peerProfileId }
    val target = peer.safetyName
    HistoryFrame(state, chrome, "차단·신고", "${target}에 대한 조치예요", onBack, headerPeer = peer, equalPanelHeights = true,
        primary = {
            GlassCard(Modifier.fillMaxWidth().fillMaxHeight()) { ContentColumn {
                if (peer == null || target == "이 상대") ChayeonText("상대의 공개 프로필을 확인할 수 없어요. 이 화면을 연 상대에게 조치해요.", style = ChayeonType.Secondary)
                GradientText("차단하면", style = ChayeonType.Title)
                InfoRow(ChayeonIcon.CHECK, "서로의 카드와 요청이 사라져요")
                InfoRow(ChayeonIcon.CHECK, "일시 연결과 이어진 대화도 닫혀요")
                InfoRow(ChayeonIcon.CHECK, "상대에게 이유를 알리지 않아요")
                InfoRow(ChayeonIcon.LOCK, "해제해도 이전 대화와 관계는 돌아오지 않아요")
                JellyButton(if (blocked || state.command("block").completed) "차단했어요" else "$target 차단", onBlock,
                    Modifier.fillMaxWidth(), JellyVariant.WHITE, enabled = !blocked && !state.command("block").completed && !state.command("block").running)
                CommandFeedback(state.command("block"), { onRetry("block") })
                JellyButton("차단한 사람 보기", onBlocks, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
            } }
        }, secondary = {
            GlassCard(Modifier.fillMaxWidth().fillMaxHeight()) { ContentColumn {
                GradientText("신고 이유", style = ChayeonType.Title)
                ReportReason.entries.forEach { reason ->
                    GelRadioRow(reason.label, reason == state.selectedReason, { onReason(reason) }, enabled = !state.command("report").running)
                }
                JellyButton("$target 신고 보내기", onReport, Modifier.fillMaxWidth(), enabled = !state.command("report").running && !state.command("report").completed)
                CommandFeedback(state.command("report"), { onRetry("report") }, "신고를 접수했어요")
                ChayeonText("시연에서는 접수 내용만 저장해요. 운영 검토와 조치는 아직 제공하지 않아요.", style = ChayeonType.Secondary)
                state.policy?.reportRetentionDays?.let { ChayeonText("신고 기록은 ${it}일 동안 제한 보관해요.", style = ChayeonType.Secondary) }
            } }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}

@Composable
fun BlocksScreen(state: HistoryUiState, chrome: ScreenChrome, onUnblock: (Block) -> Unit,
    onRetry: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onBack: () -> Unit) {
    HistoryFrame(state, chrome, "차단한 사람", "해제해도 이전 대화와 관계는 돌아오지 않아요", onBack,
        primary = {
            Loaded(state.blocks, "차단한 사람이 없어요") { blocks ->
                ContentColumn {
                    blocks.forEach { block ->
                        GlassCard { ContentColumn {
                            PeerHeader(block.peer, size = PeerHeaderSize.SMALL)
                            JellyButton("${block.peer.nickname} 님 차단 해제", { onUnblock(block) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY,
                                enabled = !state.command("unblock:${block.id}").running)
                            CommandFeedback(state.command("unblock:${block.id}"), { onRetry("unblock:${block.id}") })
                        } }
                    }
                }
            }
        }, secondary = {
            HeroPanel { ContentColumn {
                IconOrb(ChayeonIcon.LOCK)
                SectionTitle("만남의 범위는 내가 정해요", subtitle = "차단한 사람에게 이유는 전달되지 않아요. 차단을 해제하면 서로 다시 발견될 수 있어요.")
            } }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}

@Composable
fun CleanupScreen(state: HistoryUiState, chrome: ScreenChrome, onAsk: (ConfirmationKind, String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit, onRetry: (String) -> Unit, onLeave: () -> Unit, onBack: () -> Unit) {
    val connection = state.connection.valueOrNull()?.takeIf { it.status != ConnectionStatus.ENDED }
    HistoryFrame(state, chrome, "기록과 계정 정리", "무엇이 멈추고 무엇이 남는지 확인해 주세요", onBack,
        onLeave = if (connection != null) onLeave else null,
        primary = {
            ContentColumn {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = ChayeonSpacing.CardHorizontal)) {
                    if (maxWidth >= ChayeonSpacing.NarrowBreakpoint) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        ChayeonText("동작", Modifier.weight(.8f), ChayeonType.Label, ChayeonColors.Secondary)
                        ChayeonText("멈추거나 지워져요", Modifier.weight(1.3f), ChayeonType.Label, ChayeonColors.Secondary)
                        ChayeonText("그대로 남아요", Modifier.weight(1.3f), ChayeonType.Label, ChayeonColors.Secondary)
                        Spacer(Modifier.weight(.7f))
                    }
                }
                CleanupRow(state, ConfirmationKind.DISCOVERY_OFF, "", state.discovery.mode == DiscoveryMode.ON, onAsk, onRetry)
                CleanupRow(state, ConfirmationKind.LEAVE, connection?.id.orEmpty(), connection != null, onAsk, onRetry)
                Loaded(state.relationships, "이어진 관계가 없어요") { relationships ->
                    relationships.filter { it.status == AfterConnectionStatus.CONNECTED }.forEach { relationship ->
                        CleanupRow(state, ConfirmationKind.END_RELATIONSHIP, relationship.id, true, onAsk, onRetry, relationship.peer)
                    }
                }
                CleanupRow(state, ConfirmationKind.DELETE_RECORDS, "", state.encounters.valueOrNull()?.isNotEmpty() == true, onAsk, onRetry)
                CleanupRow(state, ConfirmationKind.SIGN_OUT, "", state.session is SessionState.Active, onAsk, onRetry)
                CleanupRow(state, ConfirmationKind.DELETE_ACCOUNT, "", state.session is SessionState.Active, onAsk, onRetry)
            }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}

@Composable
private fun CleanupRow(state: HistoryUiState, kind: ConfirmationKind, id: String, enabled: Boolean,
    onAsk: (ConfirmationKind, String) -> Unit, onRetry: (String) -> Unit, peer: PublicProfile? = null) {
    val copy = cleanupCopy(kind, state.policy?.reportRetentionDays)
    val key = "cleanup:$kind:$id"
    GlassCard(Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            val narrow = maxWidth < ChayeonSpacing.NarrowBreakpoint
            if (narrow) ContentColumn {
                SectionTitle(if (peer == null) copy.title else "${peer.nickname} 님 · ${copy.title}")
                if (LocalSourceLabels.current && peer != null) ChayeonText(peer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                ChayeonText("멈추거나 지워져요 · ${copy.removes}", style = ChayeonType.Secondary)
                ChayeonText("남는 내용 · ${copy.keeps}", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                CleanupButton(copy.title, state.command(key), enabled, kind) { onAsk(kind, id) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                Column(Modifier.weight(.8f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    ChayeonText(if (peer == null) copy.title else "${peer.nickname} 님\n${copy.title}", style = ChayeonType.Section)
                    if (LocalSourceLabels.current && peer != null) ChayeonText(peer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                }
                ChayeonText(copy.removes, Modifier.weight(1.3f), ChayeonType.Secondary)
                ChayeonText(copy.keeps, Modifier.weight(1.3f), ChayeonType.Secondary, ChayeonColors.Secondary)
                Box(Modifier.weight(.7f)) { CleanupButton(copy.title, state.command(key), enabled, kind) { onAsk(kind, id) } }
            }
        }
        CommandFeedback(state.command(key), { onRetry(key) }, "정리했어요")
    }
}

@Composable
private fun CleanupButton(label: String, state: CommandState, enabled: Boolean, kind: ConfirmationKind, onClick: () -> Unit) {
    val destructive = kind in setOf(ConfirmationKind.END_RELATIONSHIP, ConfirmationKind.DELETE_RECORDS, ConfirmationKind.DELETE_ACCOUNT)
    JellyButton(if (state.running) "처리 중" else label, onClick, Modifier.fillMaxWidth(), if (destructive) JellyVariant.DANGER else JellyVariant.SECONDARY, enabled = enabled && !state.running)
}
