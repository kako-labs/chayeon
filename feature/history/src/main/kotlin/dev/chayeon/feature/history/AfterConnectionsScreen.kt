package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun AfterConnectionsScreen(state: HistoryUiState, chrome: ScreenChrome, onSelect: (String) -> Unit,
    onIncoming: (String) -> Unit, onText: (String) -> Unit, onSend: () -> Unit, onPreset: (PresetCode) -> Unit,
    onRetryMessage: (String) -> Unit, onRetry: (String) -> Unit, onEnd: () -> Unit, onSafety: (String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val relationship = state.selectedRelationship
    HistoryFrame(state, chrome, "이어진 인연", "서로 수락해서 이어진 사람들이에요", tab = NavigationItem.AFTER, ratio = .42f,
        bottom = {
            if (relationship != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                if (relationship.status == AfterConnectionStatus.CONNECTED) JellyButton("관계 끝내기", onEnd, Modifier.weight(1f), JellyVariant.SECONDARY, enabled = !state.command("end:${relationship.id}").running)
                JellyButton("차단·신고", { onSafety(relationship.peer.id) }, Modifier.weight(1f), JellyVariant.DANGER)
            }
        },
        primary = {
            ContentColumn {
                Loaded(state.requests, "기다리는 신청이 없어요") { requests ->
                    requests.filter { it.direction == RequestDirection.INCOMING && it.status == AfterRequestStatus.PENDING }.forEach {
                        SettingsRow(ChayeonIcon.KNOT, "${it.peer.nickname} 님의 애프터", it.peer.kind.label, { onIncoming(it.encounterId) })
                    }
                }
                Loaded(state.relationships, "아직 이어진 인연이 없어요") { relationships ->
                    relationships.forEach { connection ->
                        GlassCard { ContentColumn {
                            PeerHeader(connection.peer, size = PeerHeaderSize.SMALL)
                            ChayeonText(if (connection.status == AfterConnectionStatus.CONNECTED) "서로 이어졌어요" else "관계가 끝났어요", style = ChayeonType.Secondary)
                            if (connection.id == state.selectedRelationshipId) StatusChip(if (connection.status == AfterConnectionStatus.CONNECTED) "대화 중인 인연" else "선택한 인연")
                            else JellyButton("대화 보기", { onSelect(connection.id) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                        } }
                    }
                }
            }
        }, secondary = {
            if (relationship == null) EmptyCard("마음을 다시 이어봐요", "스친 인연에서 애프터를 신청할 수 있어요")
            else ContentColumn {
                HeroPanel(contentPadding = PaddingValues(ChayeonSpacing.Large)) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                        GradientText(if (relationship.status == AfterConnectionStatus.CONNECTED) "${relationship.peer.nickname} 님과 이어졌어요" else "관계를 마쳤어요", style = ChayeonType.Title)
                        ChayeonText(relationship.peer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                        relationship.peer.signature?.let { ChayeonText("${it.title} · ${it.source.label}", style = ChayeonType.Secondary) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                            state.myProfile?.publicProfile?.let { AvatarDish(MochiAvatar.from(it.avatar), Modifier.size(ChayeonDimensions.PeerSmall)) }
                            key(relationship.id, relationship.status) {
                                ThreadView(if (relationship.status == AfterConnectionStatus.CONNECTED) ThreadStage.AFTER_CONNECTED else ThreadStage.ENDED,
                                    Modifier.weight(1f).height(ChayeonDimensions.MiniKnot), animateTransition = relationship.status == AfterConnectionStatus.CONNECTED)
                            }
                            AvatarDish(MochiAvatar.from(relationship.peer.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
                        }
                    }
                }
                if (relationship.status == AfterConnectionStatus.CONNECTED) {
                    val conversation: @Composable () -> Unit = {
                        GlassCard { Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                        SectionTitle("이어진 대화")
                        val messageScroll = rememberScrollState()
                        Column(Modifier.heightIn(max = ChayeonDimensions.Avatar).scrollEdgeFade(messageScroll).verticalScroll(messageScroll), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                        Loaded(state.messages, "첫 인사를 건네보세요") { messages ->
                            messages.forEach { message ->
                                if (state.outbox.none { it.request.clientMessageId == message.clientMessageId }) {
                                    MessageRow(message.content, message.author == MessageAuthor.SELF, message.delivery)
                                    if (message.author == MessageAuthor.SELF && message.delivery == DeliveryState.FAILED) JellyButton("이 메시지 다시 보내기", { onRetryMessage(message.clientMessageId) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                                }
                            }
                        }
                        state.outbox.filter { it.request.afterConnectionId == relationship.id }.forEach { pending ->
                            MessageRow(pending.request.content, true, pending.delivery)
                            if (pending.delivery == DeliveryState.FAILED) JellyButton("이 메시지 다시 보내기", { onRetryMessage(pending.request.clientMessageId) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                        }
                        }
                        GelTextField(state.messageDrafts[relationship.id].orEmpty(), onText, "메시지", placeholder = "편하게 인사를 건네보세요",
                            maxLength = AfterConnectionsViewModel.AFTER_TEXT_LIMIT, singleLine = false, enabled = !state.command("compose:${relationship.id}").running)
                        JellyButton("메시지 보내기", onSend, Modifier.fillMaxWidth(), enabled = state.messageDrafts[relationship.id].orEmpty().isNotBlank() && !state.command("compose:${relationship.id}").running)
                        CommandFeedback(state.command("compose:${relationship.id}"), { onRetry("compose:${relationship.id}") })
                    } }
                    }
                    val presets: @Composable () -> Unit = {
                        GlassCard(contentPadding = PaddingValues(horizontal = ChayeonSpacing.CardHorizontal, vertical = ChayeonSpacing.Medium)) {
                            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                            SectionTitle("인사 보내기")
                            relationship.availablePresets.sortedBy { it.ordinal }.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                                    row.forEach { code ->
                                        Column(Modifier.weight(1f)) {
                                            PresetButton(code.label, { onPreset(code) }, Modifier.fillMaxWidth(), enabled = !state.command("preset:${relationship.id}:$code").running)
                                            CommandFeedback(state.command("preset:${relationship.id}:$code"), { onRetry("preset:${relationship.id}:$code") })
                                        }
                                    }
                                }
                            }
                        } }
                    }
                    BoxWithConstraints {
                        if (maxWidth >= ChayeonSpacing.NarrowBreakpoint - ChayeonSpacing.Screen * 2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                                Box(Modifier.weight(1f)) { conversation() }
                                Box(Modifier.weight(1.2f)) { presets() }
                            }
                        } else ContentColumn { conversation(); presets() }
                    }
                    CommandFeedback(state.command("end:${relationship.id}"), { onRetry("end:${relationship.id}") })
                } else EmptyCard("이 대화는 닫혔어요", "끝난 관계는 다시 열리지 않아요. 처음 스친 기록은 남아요.")
            }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}

@Composable
private fun MessageRow(content: AfterMessageContent, mine: Boolean, delivery: DeliveryState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        ChatBubble(when (content) { is AfterMessageContent.Preset -> content.code.label; is AfterMessageContent.Text -> content.text }, mine)
        if (mine) ChayeonText(when (delivery) {
            DeliveryState.SENDING -> "보내고 있어요"
            DeliveryState.SENT -> "보냈어요"
            DeliveryState.FAILED -> "보내지 못했어요"
        }, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
    }
}
