package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun AfterRequestScreen(state: HistoryUiState, chrome: ScreenChrome, onRequest: () -> Unit,
    onRespond: (String, RequestDecision) -> Unit, onWithdraw: (String) -> Unit, onConnected: () -> Unit,
    onSafety: (String) -> Unit, onRetry: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onBack: () -> Unit,
    onContacts: (ContactIds) -> Unit = {}, onSaveToProfile: (Boolean) -> Unit = {}) {
    val encounter = state.encounter.valueOrNull()
    val request = state.requests.valueOrNull()?.filter { it.encounterId == (encounter?.id ?: state.targetEncounterId) }?.maxByOrNull { it.createdAtEpochMillis }
    val peer = request?.peer ?: encounter?.peer
    val incoming = request?.direction == RequestDirection.INCOMING
    val actions: @Composable () -> Unit = {
        AdaptiveActions(
            first = {
                if (incoming && peer != null) HistoryButton("차단·신고", { onSafety(peer.id) }, Modifier.fillMaxWidth(), JellyVariant.DANGER)
                else HistoryButton("돌아가기", onBack, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
            },
            second = {
                when (request?.status) {
                    AfterRequestStatus.ACCEPTED -> HistoryButton("이어진 인연 보기", onConnected, Modifier.fillMaxWidth())
                    AfterRequestStatus.PENDING -> {
                        if (incoming) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            HistoryButton("거절", { onRespond(request.id, RequestDecision.DECLINE) }, Modifier.weight(1f), JellyVariant.SECONDARY, enabled = !state.command("respond:${request.id}").running)
                            HistoryButton("수락하고 이어가기", { onRespond(request.id, RequestDecision.ACCEPT) }, Modifier.weight(2f), enabled = !state.homeSuppressed && state.shareContacts.normalized().isValid(requireAtLeastOne = true) && !state.command("respond:${request.id}").running)
                        } else HistoryButton("신청 철회", { onWithdraw(request.id) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.command("withdraw:${request.id}").running)
                    }
                    else -> if (state.requests is LoadState.Ready && encounter?.afterEligibility == AfterEligibility.AVAILABLE) {
                        HistoryButton(if (request == null) "애프터 신청 보내기" else "새 애프터 신청 보내기", onRequest, Modifier.fillMaxWidth(), enabled = state.shareContacts.normalized().isValid(requireAtLeastOne = true) && !state.command("request").running)
                    }
                }
            },
        )
    }
    HistoryFrame(state, chrome, if (incoming) "받은 애프터 신청" else "애프터 신청", "서로 고른 아이디로 마음을 이어가요", onBack, bottom = actions, ratio = 1f, secondaryFirstWhenNarrow = true,
        primary = {
            ContentColumn {
                if (state.requests is LoadState.Failed) Loaded(state.requests) {}
                else if (peer == null) Loaded(state.encounter, "이 기록은 더 이상 없어요") {}
                else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                    GradientText(when {
                        request?.status == AfterRequestStatus.ACCEPTED -> "서로의 마음이 이어졌어요"
                        request?.status == AfterRequestStatus.PENDING && incoming -> "${peer.nickname} 님이 애프터를 신청했어요"
                        request?.status == AfterRequestStatus.PENDING -> "답을 기다리고 있어요"
                        request != null -> "이번 신청을 마쳤어요"
                        else -> "${peer.nickname} 님과 다시 이어갈까요?"
                    }, style = ChayeonType.Hero)
                    ChayeonText(when {
                        request?.status == AfterRequestStatus.ACCEPTED -> "서로 수락해서 고른 아이디를 나눴어요."
                        request != null && request.status != AfterRequestStatus.PENDING -> "이번 신청은 마쳤어요. 상대의 아이디는 공유되지 않아요."
                        incoming -> "수락하면 내가 고른 아이디도 상대에게 보여요. 거절 이유는 전달되지 않아요."
                        else -> "상대도 아이디를 골라 수락하면 서로에게 보여요. 거절 이유는 전달되지 않아요."
                    }, style = ChayeonType.Body)
                    Mascot(MascotPose.KNOT, Modifier.fillMaxWidth().height(ChayeonDimensions.HeroKnot))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        state.myProfile?.publicProfile?.let { mine -> AfterAvatar(mine, Modifier.weight(.5f)) }
                        ThreadView(if (request?.status == AfterRequestStatus.ACCEPTED) ThreadStage.AFTER_CONNECTED else ThreadStage.AFTER_PENDING,
                            Modifier.weight(1f), animateTransition = request?.status == AfterRequestStatus.ACCEPTED)
                        AfterAvatar(peer, Modifier.weight(.5f))
                    }
                    peer.signature?.let { SignatureMini(it, Modifier.widthIn(max = ChayeonDimensions.ConfirmWidth)) }
                    InterestRow(peer.interests)
                    encounter?.let { StatusChip("${encounterDuration(it)} 함께했어요") }
                    if (request?.status == AfterRequestStatus.PENDING) ResponseCountdown(state.remainingMillis[request.id], totalMillis = request.expiresAtEpochMillis - request.createdAtEpochMillis)
                }
                when (request?.status) {
                    AfterRequestStatus.DECLINED -> StateCard(ExperienceState.AFTER_DECLINED)
                    AfterRequestStatus.EXPIRED -> StateCard(ExperienceState.AFTER_EXPIRED)
                    AfterRequestStatus.WITHDRAWN -> StateCard(ExperienceState.AFTER_WITHDRAWN)
                    AfterRequestStatus.UNAVAILABLE -> StateCard(ExperienceState.PEER_UNAVAILABLE)
                    else -> Unit
                }
                CommandFeedback(state.command("request"), { onRetry("request") })
                request?.let {
                    CommandFeedback(state.command("respond:${it.id}"), { onRetry("respond:${it.id}") })
                    CommandFeedback(state.command("withdraw:${it.id}"), { onRetry("withdraw:${it.id}") })
                }
                if (request?.status != AfterRequestStatus.PENDING && request?.retryAvailableAtEpochMillis != null) {
                    ChayeonText("다시 신청할 수 있을 때까지 기다려 주세요", style = ChayeonType.Secondary)
                    ResponseCountdown(state.remainingMillis["retry:${request.id}"])
                }
            }
        }, secondary = {
            ContentColumn {
                if (incoming && request?.status == AfterRequestStatus.PENDING && state.homeSuppressed)
                    TopBanner("집 근처에서 쉬는 동안에는 수락할 수 없어요", icon = ChayeonIcon.LOCK)
                if (peer != null && ((incoming && request?.status == AfterRequestStatus.PENDING) ||
                    (request?.status != AfterRequestStatus.PENDING && request?.status != AfterRequestStatus.ACCEPTED && encounter?.afterEligibility == AfterEligibility.AVAILABLE))) {
                    GlassPanel { ContactSelection(state, onContacts, onSaveToProfile) }
                }
                if (peer != null) {
                    PeerChemistry(request?.chemistry ?: encounter?.chemistry ?: Chemistry(), state.myProfile?.publicProfile, peer)
                    if (!incoming) HistoryButton("차단·신고", { onSafety(peer.id) }, Modifier.fillMaxWidth(), JellyVariant.DANGER)
                }
                GlassPanel { ContentColumn {
                    InfoRow(ChayeonIcon.LOCK, "수락 전에는 아이디가 보이지 않아요")
                    InfoRow(ChayeonIcon.CLOCK, "거절되거나 만료되면 잠시 기다려요")
                } }
            }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}

@Composable
private fun AfterAvatar(profile: PublicProfile, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        AvatarDish(MochiAvatar.from(profile.avatar), Modifier.size(ChayeonDimensions.Avatar))
        ChayeonText(profile.nickname, style = ChayeonType.Section)
        if (LocalSourceLabels.current) ChayeonText(profile.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
    }
}

@Composable
private fun AdaptiveActions(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(3f)) { second() }
    }
}
