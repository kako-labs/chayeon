package dev.chayeon.feature.meet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeUiState,
    chrome: ScreenChrome,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onCandidate: (String) -> Unit,
    onRequests: () -> Unit,
    onConnected: () -> Unit,
    onMySignature: () -> Unit,
    onLocationSettings: () -> Unit,
    onCreateSignature: () -> Unit = onMySignature,
    onDemo: () -> Unit = {},
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        DrivingScreen(state.driving, chrome)
        return
    }
    ScreenScaffold(
        showSourceLabels = chrome.showSourceLabels,
        modifier = Modifier.testTag("meet-screen"),
        topBar = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                chrome.topBar()
                AppHeader(profile = state.profile, onProfileClick = onMySignature)
            }
        },
        bottomBar = { chrome.bottomBar(NavigationItem.MEET) },
        primary = {
            MeetPane {
                MeetDiscoveryHero(state, onToggle)
                state.pendingRequest?.let {
                    JellyButton(if (it.direction == RequestDirection.INCOMING) "받은 연결 요청 보기" else "보낸 연결 요청 보기",
                        onRequests, Modifier.fillMaxWidth(), JellyVariant.WHITE)
                }
                MeetError(state.refresh.error)
                if (state.profileLoaded && state.profile?.signature == null) MeetSignatureInvitation(onCreateSignature)
                val experience = state.discovery.status.experienceState
                when {
                    state.discovery.mode == DiscoveryMode.OFF -> MeetDiscoveryFacts()
                    state.homeZone.suppressed -> GlassCard(Modifier.fillMaxWidth()) {
                        InfoRow(ChayeonIcon.LOCK, "발견을 잠시 쉬고 있어요", body = "집 보호 구역 설정은 내 프로필에서 확인할 수 있어요")
                    }
                    state.demoPresence == SyntheticPresence.AWAY && state.discovery.status == DiscoveryStatus.EMPTY -> GlassCard(Modifier.fillMaxWidth()) {
                        MeetPane {
                            InfoRow(ChayeonIcon.PEOPLE, if (LocalSourceLabels.current) "합성 위치가 멀어진 상태예요" else "지금은 서로 멀어진 상태예요",
                                body = "시연 제어에서 가까이 있어요를 누르면 다시 만날 수 있어요")
                            JellyButton("시연 제어 열기", onDemo, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                        }
                    }
                    experience != null -> StateCard(experience, Modifier.fillMaxWidth(), onAction = when (state.discovery.status) {
                        DiscoveryStatus.LOCATION_PERMISSION_OFF -> onLocationSettings
                        DiscoveryStatus.LOCATION_STALE, DiscoveryStatus.NETWORK_FAILURE -> if (state.refresh.inProgress) null else onRefresh
                        DiscoveryStatus.ALREADY_CONNECTED -> onConnected
                        else -> null
                    })
                    state.discovery.status == DiscoveryStatus.AVAILABLE -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("지금 연결할 수 있는 사람", Modifier.weight(1f))
                            JellyButton(if (state.refresh.inProgress) "확인 중이에요" else "새로 보기", onRefresh,
                                variant = JellyVariant.SECONDARY, enabled = !state.refresh.inProgress)
                        }
                        if (state.discovery.candidates.isEmpty()) StateCard(ExperienceState.NO_CANDIDATES, Modifier.fillMaxWidth())
                        else MeetCandidateList(state.discovery.candidates, onCandidate)
                    }
                }
            }
        },
    )
}

@Composable
fun CandidateScreen(
    state: CandidateUiState,
    chrome: ScreenChrome,
    onRequest: () -> Unit,
    onBack: () -> Unit,
    onSafety: () -> Unit,
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        DrivingScreen(state.driving, chrome)
        return
    }
    MeetCandidateContent(state, chrome, onRequest, onBack, onSafety)
}

@Composable
fun RequestScreen(
    state: RequestUiState,
    chrome: ScreenChrome,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onSafety: () -> Unit,
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        if (state.driving.policy == DrivingPolicy.RESTRICTED && state.receiveRequestsWhileDriving &&
            state.request?.direction == RequestDirection.INCOMING && state.request.status == ConnectionRequestStatus.PENDING &&
            state.observationError == null) MeetIncomingDriving(state, chrome, onAccept, onDecline)
        else DrivingScreen(state.driving, chrome)
        return
    }
    ViewedContent(chrome, state.driving.policy, requestId = state.request?.id)
    val request = state.request
    val incoming = request?.direction == RequestDirection.INCOMING
    val pending = request?.status == ConnectionRequestStatus.PENDING
    if (request != null && pending && state.observationError == null) {
        MeetPendingRequest(state, request, chrome, onAccept, onDecline, onCancel, onBack, onSafety)
        return
    }
    ScreenScaffold(
        showSourceLabels = chrome.showSourceLabels,
        modifier = Modifier.testTag("meet-screen"),
        topBar = { MeetHeader(chrome, if (incoming) "받은 연결 요청" else "보낸 연결 요청", "수락하면 바로 통화가 연결돼요", onBack) },
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                MeetError(state.accept.error)
                MeetError(state.decline.error)
                MeetError(state.cancel.error)
                JellyButton("목록으로", onBack, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
            }
        },
        primary = {
            MeetPane {
                when {
                    state.observationError != null -> {
                        StateCard(ExperienceState.NETWORK_FAILURE, Modifier.fillMaxWidth())
                        MeetError(state.observationError)
                    }
                    request == null -> StateCard(if (state.loading) ExperienceState.DISCOVERY_LOADING else ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                    request.status == ConnectionRequestStatus.ACCEPTED -> GlassCard(Modifier.fillMaxWidth()) {
                        SectionTitle("요청이 수락됐어요", subtitle = "이어진 연결을 확인하고 있어요")
                    }
                    request.status == ConnectionRequestStatus.CANCELLED && !incoming -> GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                            StatusChip("취소됨", variant = StatusVariant.PLAIN)
                            SectionTitle("요청을 취소했어요", subtitle = "연결은 만들어지지 않았고 기록에도 남지 않아요")
                        }
                    }
                    else -> StateCard(request.status.experienceState ?: ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                }
            }
        },
    )
}

@Composable
fun ConnectedScreen(
    state: ConnectedUiState,
    chrome: ScreenChrome,
    onLeave: () -> Unit,
    onSafety: () -> Unit,
    onMute: () -> Unit = {},
    onMicrophone: () -> Unit = {},
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        MeetConnectedDriving(state, chrome, onMute, onLeave)
        return
    }
    val connection = state.connection
    ScreenScaffold(
        showSourceLabels = chrome.showSourceLabels,
        modifier = Modifier.testTag("meet-screen"),
        topBar = chrome.topBar,
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                MeetError(state.leave.error)
                MeetCallControls(state, onMute, onLeave)
            }
        },
        primary = {
            MeetPane {
                when {
                    state.observationError != null -> {
                        StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth())
                        MeetError(state.observationError)
                    }
                    connection == null -> StateCard(if (state.loading) ExperienceState.DISCOVERY_LOADING else ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                    connection.status == ConnectionStatus.ENDED -> GlassCard(Modifier.fillMaxWidth()) {
                        SectionTitle("통화가 끝났어요", subtitle = "스친 인연에서 기록을 확인할 수 있어요")
                    }
                    else -> {
                        MeetCallHero(state, onSafety = onSafety)
                        if (state.call.microphone == MicrophoneStatus.DENIED || state.call.microphone == MicrophoneStatus.UNAVAILABLE) {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    ChayeonText("이 차에서는 마이크를 쓸 수 없어요", style = ChayeonType.Section)
                                    ChayeonText("상대의 목소리는 들을 수 있어요", style = ChayeonType.Secondary)
                                    JellyButton("마이크 허용하기", onMicrophone, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                                }
                            }
                        }
                        if (topPublishedScoredTraits(connection.peer.signature).isNotEmpty()) {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    SectionTitle("상대의 운전")
                                    PublishedScoreChips(connection.peer.signature)
                                }
                            }
                        }
                        MeetCallTopics(state, onSafety)
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EndedScreen(
    state: EndedUiState,
    chrome: ScreenChrome,
    onHome: () -> Unit,
    onEncounter: (String) -> Unit,
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        DrivingScreen(state.driving, chrome)
        return
    }
    val connection = state.connection
    val ended = connection?.status == ConnectionStatus.ENDED
    val endReason = connection?.endReason
    ScreenScaffold(
        showSourceLabels = chrome.showSourceLabels,
        modifier = Modifier.testTag("meet-screen"),
        topBar = chrome.topBar,
        bottomBar = {
            val encounterId = connection?.encounterId?.takeIf { ended }
            if (encounterId == null) JellyButton("오늘의 인연으로", onHome, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
            else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                ChayeonText("이 인연의 기록을 이어서 확인해요", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                MeetTwoColumns(
                    first = { JellyButton("오늘의 인연으로", onHome, Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth), JellyVariant.SECONDARY) },
                    second = { JellyButton("기록 보기", { onEncounter(encounterId) }, Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth)) },
                    secondWeight = 2f,
                )
            }
        },
        primary = {
            MeetPane {
                when {
                    state.observationError != null -> {
                        StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth())
                        MeetError(state.observationError)
                    }
                    connection == null -> StateCard(if (state.loading) ExperienceState.DISCOVERY_LOADING else ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                    !ended || endReason == null -> StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth())
                    else -> {
                        val presentation = endReason.presentation(connection.peer.nickname)
                        StatusChip(presentation.tag, variant = StatusVariant.PLAIN)
                        MeetRelationStage(state.ownProfile, connection.peer, ThreadStage.ENDED, presentation.title, presentation.body,
                            modifier = Modifier.heightIn(min = ChayeonDimensions.ChemistryKnot + ChayeonDimensions.PeerSmall), centered = true)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                            connection.endedAtEpochMillis?.let { endedAt ->
                                MeetEndFact("함께한 시간", "${((endedAt - connection.startedAtEpochMillis).coerceAtLeast(0) / 60_000)}분", Modifier.weight(1f))
                                MeetEndFact("끝난 시각", DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(endedAt)), Modifier.weight(1f))
                            }
                            MeetEndFact("끝난 이유", presentation.label, Modifier.weight(1f))
                        }
                        connection.peer.signature?.let { signature -> GlassCard(Modifier.fillMaxWidth()) { SignatureMini(signature) } }
                    }
                }
            }
        },
    )
}

@Composable
fun DrivingScreen(
    driving: DrivingState,
    chrome: ScreenChrome,
    hasConnection: Boolean = false,
    leave: MeetCommandState = MeetCommandState(),
    onLeave: () -> Unit = {},
) {
    CompositionLocalProvider(LocalDecorativeMotion provides false) {
        Column(Modifier.fillMaxSize().background(ChayeonColors.Background).testTag("meet-screen")
            .padding(ChayeonSpacing.Screen), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            chrome.topBar()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    ChayeonText(if (hasConnection) "새 연결이 있어요" else if (driving.policy == DrivingPolicy.UNKNOWN) "주행 상태를 확인하고 있어요" else "주행 중이에요", style = ChayeonType.Hero)
                    ChayeonText(if (hasConnection) "주차하면 볼 수 있어요" else if (driving.policy == DrivingPolicy.UNKNOWN) "주차 상태가 확인되면 다시 볼 수 있어요" else "주차하면 다시 볼 수 있어요",
                        style = ChayeonType.Body, color = ChayeonColors.Secondary)
                }
            }
            if (hasConnection) {
                MeetError(leave.error)
                JellyButton(if (leave.inProgress) "통화를 마치고 있어요" else "통화 끊기", onLeave,
                    Modifier.fillMaxWidth(), JellyVariant.SECONDARY, enabled = !leave.inProgress, allowWhenRestricted = true)
            }
        }
    }
}

@Composable
private fun MeetDiscoveryFacts() {
    MeetTwoColumns(
        first = { GlassCard(Modifier.fillMaxWidth()) { InfoRow(ChayeonIcon.EYE, "내 카드를 직접 공개해요", body = "공개한 시그니처와 관심사로 서로를 알아봐요") } },
        second = { GlassCard(Modifier.fillMaxWidth()) { InfoRow(ChayeonIcon.LOCK, "대화는 따로 수락해요", body = "수락하거나 주차 중 고른 자동 연결 조건에 맞을 때 이어져요") } },
    )
}

@Composable
private fun MeetCandidateList(candidates: List<Candidate>, onCandidate: (String) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= ChayeonSpacing.NarrowBreakpoint) {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                candidates.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        group.forEach { candidate ->
                            MeetHomeCandidate(candidate, { onCandidate(candidate.id) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            candidates.forEach { candidate -> MeetHomeCandidate(candidate, { onCandidate(candidate.id) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun MeetEndFact(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        ChayeonText(label, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        ChayeonText(value, style = ChayeonType.Section)
    }
}

@Composable
private fun MeetPendingRequest(
    state: RequestUiState,
    request: ConnectionRequest,
    chrome: ScreenChrome,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onSafety: () -> Unit,
) {
    val incoming = request.direction == RequestDirection.INCOMING
    val busy = state.accept.inProgress || state.decline.inProgress || state.cancel.inProgress
    ScreenScaffold(
        showSourceLabels = chrome.showSourceLabels,
        modifier = Modifier.testTag("meet-screen"),
        topBar = { MeetHeader(chrome, if (incoming) "받은 연결 요청" else "보낸 연결 요청", "수락하면 바로 통화가 연결돼요", onBack) },
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                if (incoming && state.homeSuppressed) TopBanner("집 근처에서 쉬는 동안에는 수락할 수 없어요")
                else MeetError(state.accept.error)
                MeetError(state.decline.error)
                MeetError(state.cancel.error)
                if (incoming) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        JellyButton("차단·신고", onSafety, Modifier.weight(1f).heightIn(min = ChayeonDimensions.ToggleWidth), JellyVariant.DANGER, enabled = !busy)
                        JellyButton(if (state.decline.inProgress) "거절하고 있어요" else "거절", onDecline,
                            Modifier.weight(1f).heightIn(min = ChayeonDimensions.ToggleWidth), JellyVariant.SECONDARY, enabled = !busy)
                        JellyButton(if (state.accept.inProgress) "연결을 확인하고 있어요" else "수락하고 연결", onAccept,
                            Modifier.weight(2f).heightIn(min = ChayeonDimensions.ToggleWidth), enabled = !busy && !state.homeSuppressed)
                    }
                } else MeetTwoColumns(
                    first = { JellyButton(if (state.cancel.inProgress) "취소하고 있어요" else "요청 취소", onCancel, Modifier.fillMaxWidth(), JellyVariant.DANGER, enabled = !state.cancel.inProgress) },
                    second = { JellyButton("기다리는 동안 목록 보기", onBack, Modifier.fillMaxWidth(), JellyVariant.SECONDARY) },
                )
            }
        },
        primary = {
            MeetPane {
                ResponseCountdown(state.remainingMillis, totalMillis = request.expiresAtEpochMillis - request.createdAtEpochMillis)
                HeroPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        SectionTitle(if (incoming) "${request.peer.nickname} 님이 연결을 요청했어요" else "${request.peer.nickname} 님의 수락을 기다려요",
                            subtitle = "근처에 있는 동안 목소리로 이야기해요")
                        PeerHeader(request.peer, size = PeerHeaderSize.SMALL)
                        request.peer.signature?.let { signature ->
                            SignatureMini(signature)
                            PublishedScoreChips(signature)
                        }
                        request.chemistry.lines.forEach { line ->
                            ChayeonText(line.text, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                        }
                    }
                }
                MeetPrivacy()
            }
        },
    )
}
