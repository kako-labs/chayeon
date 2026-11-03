package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun EncountersScreen(state: HistoryUiState, chrome: ScreenChrome, onEncounter: (String) -> Unit, onAfter: (String) -> Unit, onMeet: () -> Unit) {
    HistoryFrame(state, chrome, "스친 인연", "실제로 연결된 사람만 남아요", tab = NavigationItem.HISTORY, ratio = 2.3f,
        primary = {
            Loaded(state.encounters, "아직 스친 인연이 없어요") { encounters ->
                ContentColumn {
                    encounters.sortedByDescending { it.startedAtEpochMillis }.forEach { encounter ->
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
                                AvatarDish(MochiAvatar.from(encounter.peer.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                    ChayeonText(encounter.peer.nickname, style = ChayeonType.Section)
                                    ChayeonText(encounter.peer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                    encounter.peer.signature?.let { ChayeonText("${it.title} · ${it.source.label}", style = ChayeonType.Secondary) }
                                    ChayeonText("${encounterTime(encounter.startedAtEpochMillis)} · ${encounterDuration(encounter)} · ${encounter.endReason.presentation(encounter.peer.nickname).label}", style = ChayeonType.Secondary)
                                    JellyButton(encounter.afterEligibility.label, { onEncounter(encounter.id) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                                }
                            }
                        }
                    }
                }
            }
            if (state.encounters.valueOrNull()?.isEmpty() == true) JellyButton("오늘의 인연 보기", onMeet, Modifier.fillMaxWidth())
        }, secondary = {
            ContentColumn {
                Loaded(state.requests, "기다리는 애프터가 없어요") { requests ->
                    requests.filter { it.status == AfterRequestStatus.PENDING && it.direction == RequestDirection.INCOMING }.forEach { request ->
                        HeroPanel { ContentColumn {
                            SectionTitle("답을 기다리는 애프터")
                            PeerHeader(request.peer, size = PeerHeaderSize.SMALL)
                            JellyButton("응답하기", { onAfter(request.encounterId) }, Modifier.fillMaxWidth(), JellyVariant.WHITE)
                        } }
                    }
                }
                GlassCard { ContentColumn {
                    SectionTitle("기록에 남아요")
                    InfoRow(ChayeonIcon.CHECK, "상대 닉네임과 공개 프로필")
                    InfoRow(ChayeonIcon.CLOCK, "연결 시각과 기간")
                    InfoRow(ChayeonIcon.THREAD, "끝난 이유와 애프터 상태")
                    SectionTitle("기록에 남지 않아요")
                    InfoRow(ChayeonIcon.LOCK, "위치, 경로, 집과 목적지")
                    InfoRow(ChayeonIcon.LOCK, "연결되지 않은 요청")
                } }
            }
        })
}

@Composable
fun EncounterDetailScreen(state: HistoryUiState, chrome: ScreenChrome, onAfter: () -> Unit, onSafety: (String) -> Unit,
    onDelete: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onRetry: (String) -> Unit, onBack: () -> Unit) {
    val encounter = state.encounter.valueOrNull()
    HistoryFrame(state, chrome, encounter?.peer?.nickname ?: "기록 상세", "서로 이어졌던 순간이에요", onBack,
        tab = NavigationItem.HISTORY, ratio = 1.45f,
        primary = {
            Loaded(state.encounter, "이 기록은 더 이상 없어요") { value -> value?.let {
                GlassCard { ContentColumn {
                    PeerHeader(it.peer, size = PeerHeaderSize.SMALL)
                    it.peer.signature?.let { signature -> SignatureMini(signature) }
                    ThreadView(ThreadStage.ENDED, Modifier.fillMaxWidth(), animateTransition = false)
                    SectionTitle("함께했던 순간")
                    Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        Column(Modifier.weight(1f)) { SectionTitle("연결 시작"); ChayeonText(encounterTime(it.startedAtEpochMillis), style = ChayeonType.Secondary) }
                        Column(Modifier.weight(1f)) { SectionTitle("연결 종료"); ChayeonText(encounterTime(it.endedAtEpochMillis), style = ChayeonType.Secondary) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        Column(Modifier.weight(1f)) { SectionTitle("함께한 시간"); ChayeonText(encounterDuration(it)) }
                        Column(Modifier.weight(1f)) { SectionTitle("끝난 이유"); ChayeonText(it.endReason.presentation(it.peer.nickname).label) }
                    }
                    SectionTitle("겹친 관심사")
                    InterestRow(it.sharedInterests)
                    ChayeonText("이 기록에는 만난 위치와 이동 경로가 없어요.", style = ChayeonType.Secondary)
                } }
            } }
        }, secondary = {
            if (encounter != null) ContentColumn {
                HeroPanel { ContentColumn {
                    StatusChip(encounter.afterEligibility.label)
                    GradientText("다시 이어가고 싶다면", style = ChayeonType.Title)
                    ChayeonText("상대도 수락하면 새 대화가 열려요. 수락 전에는 대화도 연락처도 열리지 않아요.")
                    ThreadView(ThreadStage.AFTER_CONNECTED, Modifier.fillMaxWidth(), animateTransition = false)
                    if (encounter.afterEligibility == AfterEligibility.COOLDOWN) {
                        ChayeonText("신청 가능 시간을 확인해 주세요", style = ChayeonType.Secondary)
                        ResponseCountdown(state.remainingMillis["retry:${encounter.id}"])
                    }
                    JellyButton(if (encounter.afterEligibility == AfterEligibility.AVAILABLE) "애프터 신청" else "애프터 상태 보기", onAfter,
                        Modifier.fillMaxWidth(), enabled = encounter.afterEligibility != AfterEligibility.UNAVAILABLE)
                } }
                GlassCard { ContentColumn {
                    JellyButton("이 기록 삭제", onDelete, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.command("delete").running)
                    JellyButton("차단·신고", { onSafety(encounter.peer.id) }, Modifier.fillMaxWidth(), JellyVariant.DANGER)
                    ChayeonText("삭제해도 상대의 기록은 그대로예요. 삭제만으로 상대가 차단되지는 않아요.", style = ChayeonType.Secondary)
                    CommandFeedback(state.command("delete"), { onRetry("delete") })
                } }
                PeerChemistry(encounter.chemistry, state.myProfile?.publicProfile, encounter.peer)
            }
        })
    HistoryConfirm(state, onConfirm, onDismiss)
}

@Composable
fun AfterRequestScreen(state: HistoryUiState, chrome: ScreenChrome, onRequest: () -> Unit,
    onRespond: (String, RequestDecision) -> Unit, onWithdraw: (String) -> Unit, onConnected: () -> Unit,
    onSafety: (String) -> Unit, onRetry: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onBack: () -> Unit) {
    val encounter = state.encounter.valueOrNull()
    val request = state.requests.valueOrNull()?.filter { it.encounterId == (encounter?.id ?: state.targetEncounterId) }?.maxByOrNull { it.version }
    val peer = request?.peer ?: encounter?.peer
    val incoming = request?.direction == RequestDirection.INCOMING
    HistoryFrame(state, chrome, if (incoming) "받은 애프터 신청" else "애프터 신청", "서로 원할 때, 새로운 대화를 열어요", onBack,
        primary = {
            ContentColumn {
                if (state.requests is LoadState.Failed) Loaded(state.requests) {}
                else if (peer == null) Loaded(state.encounter, "이 기록은 더 이상 없어요") {}
                else HeroPanel { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                    GradientText(when {
                        request?.status == AfterRequestStatus.ACCEPTED -> "서로의 마음이 이어졌어요"
                        request?.status == AfterRequestStatus.PENDING && incoming -> "${peer.nickname} 님이 애프터를 신청했어요"
                        request?.status == AfterRequestStatus.PENDING -> "답을 기다리고 있어요"
                        request != null -> "이번 신청을 마쳤어요"
                        else -> "${peer.nickname} 님과 다시 이어갈까요?"
                    }, style = ChayeonType.Hero)
                    ChayeonText("상대도 수락하면 새로운 대화가 열려요. 거절해도 이유는 전달되지 않아요.", style = ChayeonType.Body)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        state.myProfile?.publicProfile?.let { mine -> AfterAvatar(mine, Modifier.weight(.3f)) }
                        ThreadView(if (request?.status == AfterRequestStatus.ACCEPTED) ThreadStage.AFTER_CONNECTED else ThreadStage.AFTER_PENDING,
                            Modifier.weight(1f), animateTransition = request?.status == AfterRequestStatus.ACCEPTED)
                        AfterAvatar(peer, Modifier.weight(.3f))
                    }
                    peer.signature?.let { SignatureMini(it, Modifier.widthIn(max = ChayeonDimensions.ConfirmWidth)) }
                    InterestRow(peer.interests)
                    encounter?.let { StatusChip("${encounterDuration(it)} 함께했어요") }
                    if (request?.status == AfterRequestStatus.PENDING) ResponseCountdown(state.remainingMillis[request.id], totalMillis = request.expiresAtEpochMillis - request.createdAtEpochMillis)
                } }
                when (request?.status) {
                    AfterRequestStatus.DECLINED -> StateCard(ExperienceState.AFTER_DECLINED)
                    AfterRequestStatus.EXPIRED -> StateCard(ExperienceState.AFTER_EXPIRED)
                    AfterRequestStatus.WITHDRAWN -> StateCard(ExperienceState.AFTER_WITHDRAWN)
                    AfterRequestStatus.UNAVAILABLE -> StateCard(ExperienceState.PEER_UNAVAILABLE)
                    else -> Unit
                }
                AdaptiveActions(
                    first = { JellyButton("기록으로 돌아가기", onBack, Modifier.fillMaxWidth(), JellyVariant.SECONDARY) },
                    second = {
                        when (request?.status) {
                            AfterRequestStatus.ACCEPTED -> JellyButton("이어진 인연 보기", onConnected, Modifier.fillMaxWidth())
                            AfterRequestStatus.PENDING -> {
                                if (incoming) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                                    JellyButton("거절", { onRespond(request.id, RequestDecision.DECLINE) }, Modifier.weight(1f), JellyVariant.SECONDARY, enabled = !state.command("respond:${request.id}").running)
                                    JellyButton("수락하고 이어가기", { onRespond(request.id, RequestDecision.ACCEPT) }, Modifier.weight(2f), enabled = !state.command("respond:${request.id}").running)
                                } else JellyButton("신청 철회", { onWithdraw(request.id) }, Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !state.command("withdraw:${request.id}").running)
                            }
                            else -> if (state.requests is LoadState.Ready && encounter?.afterEligibility == AfterEligibility.AVAILABLE) {
                                JellyButton(if (request == null) "애프터 신청 보내기" else "새 애프터 신청 보내기", onRequest, Modifier.fillMaxWidth(), enabled = !state.command("request").running)
                            }
                        }
                    },
                )
                CommandFeedback(state.command("request"), { onRetry("request") })
                request?.let {
                    CommandFeedback(state.command("respond:${it.id}"), { onRetry("respond:${it.id}") })
                    CommandFeedback(state.command("withdraw:${it.id}"), { onRetry("withdraw:${it.id}") })
                }
                if (request?.status != AfterRequestStatus.PENDING && request?.retryAvailableAtEpochMillis != null) {
                    ChayeonText("다시 신청할 수 있을 때까지 기다려 주세요", style = ChayeonType.Secondary)
                    ResponseCountdown(state.remainingMillis["retry:${request.id}"])
                }
                if (peer != null) {
                    PeerChemistry(request?.chemistry ?: encounter?.chemistry ?: Chemistry(), state.myProfile?.publicProfile, peer)
                    JellyButton("차단·신고", { onSafety(peer.id) }, Modifier.fillMaxWidth(), JellyVariant.DANGER)
                }
                GlassCard { ContentColumn {
                    InfoRow(ChayeonIcon.LOCK, "수락 전에는 대화가 열리지 않아요")
                    InfoRow(ChayeonIcon.KNOT, "끝난 연결을 되살리지 않고 새로 이어요")
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
        ChayeonText(profile.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
    }
}

@Composable
private fun AdaptiveActions(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth < ChayeonSpacing.NarrowBreakpoint) ContentColumn { second(); first() }
        else Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(2f)) { second() }
        }
    }
}
