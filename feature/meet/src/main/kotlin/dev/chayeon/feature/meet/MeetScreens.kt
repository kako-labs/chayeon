package dev.chayeon.feature.meet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

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
    requestAction: (@Composable (Candidate, (Boolean) -> Unit) -> Unit)? = null,
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        DrivingScreen(state.driving, chrome)
        return
    }
    ScreenScaffold(
        showSourceLabels = chrome.showSourceLabels,
        modifier = Modifier.testTag("meet-screen"),
        scene = if (state.homeZone.suppressed) Scene.HOME else Scene.PARKING,
        contentPadding = meetScenePadding,
        topBar = chrome.topBar,
        bottomBar = { chrome.bottomBar(NavigationItem.MEET) },
        primary = {
            MeetPane {
                MeetDiscoveryHero(state, onToggle)
                state.pendingRequest?.let {
                    OutlineButton(if (it.direction == RequestDirection.INCOMING) "받은 통화 요청 보기" else "보낸 통화 요청 보기",
                        onRequests, Modifier.fillMaxWidth(), compact = true)
                }
                MeetError(state.refresh.error)
                if (state.profileLoaded && state.profile?.signature == null) MeetSignatureInvitation(onCreateSignature)
                val experience = state.discovery.status.experienceState
                when {
                    state.homeZone.suppressed -> Unit
                    state.discovery.mode == DiscoveryMode.OFF -> MeetDiscoveryFacts()
                    state.demoPresence == SyntheticPresence.AWAY && state.discovery.status == DiscoveryStatus.EMPTY -> GlassPanel(Modifier.fillMaxWidth()) {
                        MeetPane {
                            InfoRow(ChayeonIcon.PEOPLE, if (LocalSourceLabels.current) "합성 위치가 멀어진 상태예요" else "지금은 서로 멀어진 상태예요",
                                body = "시연 제어에서 가까이 있어요를 누르면 다시 만날 수 있어요")
                            OutlineButton("시연 제어 열기", onDemo, Modifier.fillMaxWidth())
                        }
                    }
                    experience != null -> StateCard(experience, Modifier.fillMaxWidth(), onAction = when (state.discovery.status) {
                        DiscoveryStatus.LOCATION_PERMISSION_OFF -> onLocationSettings
                        DiscoveryStatus.LOCATION_STALE, DiscoveryStatus.NETWORK_FAILURE -> if (state.refresh.inProgress) null else onRefresh
                        DiscoveryStatus.ALREADY_CONNECTED -> onConnected
                        else -> null
                    })
                    state.discovery.status == DiscoveryStatus.AVAILABLE -> {
                        if (state.discovery.candidates.isEmpty()) StateCard(ExperienceState.NO_CANDIDATES, Modifier.fillMaxWidth())
                        else MeetRecommendation(state.discovery.candidates, onCandidate, requestAction, state.refresh, onRefresh)
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
    MeetSceneSplit(chrome, MascotPose.THREAD, onBack,
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                MeetError(state.accept.error)
                MeetError(state.decline.error)
                MeetError(state.cancel.error)
                OutlineButton("목록으로", onBack, Modifier.fillMaxWidth())
            }
        },
    ) {
        HeroHeadline(if (incoming) "받은 통화 요청" else "보낸 통화 요청", "수락하면 바로 통화가 연결돼요", style = ChayeonType.SceneTitle)
        when {
            state.observationError != null -> {
                StateCard(ExperienceState.NETWORK_FAILURE, Modifier.fillMaxWidth())
                MeetError(state.observationError)
            }
            request == null -> StateCard(if (state.loading) ExperienceState.DISCOVERY_LOADING else ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
            request.status == ConnectionRequestStatus.ACCEPTED -> GlassPanel(Modifier.fillMaxWidth()) {
                SectionTitle("요청이 수락됐어요", subtitle = "이어진 연결을 확인하고 있어요")
            }
            request.status == ConnectionRequestStatus.CANCELLED && !incoming -> GlassPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    StatusChip("취소됨", variant = StatusVariant.PLAIN)
                    SectionTitle("요청을 취소했어요", subtitle = "연결은 만들어지지 않았고 기록에도 남지 않아요")
                }
            }
            else -> StateCard(request.status.experienceState ?: ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ConnectedScreen(
    state: ConnectedUiState,
    chrome: ScreenChrome,
    onLeave: () -> Unit,
    onSafety: () -> Unit,
    onMute: () -> Unit = {},
    onMicrophone: () -> Unit = {},
    elapsedMillis: Long? = null,
) {
    if (state.driving.policy != DrivingPolicy.ALLOWED) {
        MeetConnectedDriving(state, chrome, onMute, onLeave)
        return
    }
    val connection = state.connection
    BoxWithConstraints {
        val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint
        ScreenScaffold(
            showSourceLabels = chrome.showSourceLabels, modifier = Modifier.testTag("meet-screen"),
            scene = Scene.PARKING, sceneScrim = SceneScrim.CALL, contentPadding = meetScenePadding,
            topBar = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (connection != null) MeetCallStatus(state, elapsedMillis)
                    Box(Modifier.weight(1f)) { chrome.topBar() }
                }
            },
            bottomBar = {
                if (!wide) Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                    MeetError(state.leave.error)
                    MeetCallControls(state, onMute, onLeave)
                }
            },
            primary = {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    Box(if (wide) Modifier.weight(1f) else Modifier.fillMaxWidth()) {
                        MeetPane {
                            when {
                                state.observationError != null -> {
                                    StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth())
                                    MeetError(state.observationError)
                                }
                                connection == null -> StateCard(if (state.loading) ExperienceState.DISCOVERY_LOADING else ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                                connection.status == ConnectionStatus.ENDED -> HeroHeadline("통화가 끝났어요", "스친 인연에서 기록을 확인할 수 있어요")
                                else -> {
                                    MeetCallHero(state)
                                    if (state.call.microphone == MicrophoneStatus.DENIED || state.call.microphone == MicrophoneStatus.UNAVAILABLE) {
                                        GlassPanel(Modifier.fillMaxWidth()) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                                Column(Modifier.weight(1f)) {
                                                    ChayeonText("이 차에서는 마이크를 쓸 수 없어요", style = ChayeonType.SceneStatus)
                                                    InfoLine("상대의 목소리는 들을 수 있어요")
                                                }
                                                OutlineButton("마이크 허용하기", onMicrophone, compact = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (wide) MeetTwoColumns(
                        first = { if (connection != null && state.observationError == null) MeetCallDetails(state, onSafety) },
                        second = {
                            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                                MeetError(state.leave.error)
                                MeetCallControls(state, onMute, onLeave)
                            }
                        }, secondWeight = .64f,
                    ) else if (connection != null && state.observationError == null) MeetCallDetails(state, onSafety)
                }
            },
        )
    }
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
    BoxWithConstraints {
        val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint
        ScreenScaffold(showSourceLabels = chrome.showSourceLabels, modifier = Modifier.testTag("meet-screen"),
            scene = Scene.ROAD, contentPadding = meetScenePadding, topBar = chrome.topBar,
            bottomBar = {
                Column(Modifier.fillMaxWidth(if (wide) .55f else 1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    connection?.encounterId?.takeIf { ended }?.let { id ->
                        PrimaryButton("애프터 신청하기", { onEncounter(id) }, Modifier.fillMaxWidth(), icon = ChayeonIcon.HEART)
                    }
                    OutlineButton("홈으로", onHome, Modifier.fillMaxWidth())
                }
            },
            primary = {
                Box(Modifier.fillMaxSize()) {
                    if (wide) Mascot(MascotPose.BYE, Modifier.align(Alignment.CenterEnd).size(ChayeonDimensions.SceneMascot))
                    Column(Modifier.fillMaxWidth(if (wide) .55f else 1f).padding(top = ChayeonSpacing.Touch),
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        when {
                            state.observationError != null -> {
                                StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth())
                                MeetError(state.observationError)
                            }
                            connection == null -> StateCard(if (state.loading) ExperienceState.DISCOVERY_LOADING else ExperienceState.PEER_UNAVAILABLE, Modifier.fillMaxWidth())
                            !ended || endReason == null -> StateCard(ExperienceState.CONNECTION_CHECKING, Modifier.fillMaxWidth())
                            else -> {
                                val presentation = endReason.presentation(connection.peer.nickname)
                                ChayeonText(connection.peer.nickname + "님과" + (connection.endedAtEpochMillis?.let {
                                    " ${((it - connection.startedAtEpochMillis).coerceAtLeast(0) / 60_000)}분"
                                } ?: " 함께했어요"), style = ChayeonType.SceneNavigation, color = ChayeonColors.Accent)
                                HeroHeadline(presentation.title.replace("서로 멀어져서 ", "서로 멀어져서\n"), presentation.body, style = ChayeonType.Headline)
                                if (connection.encounterId != null) ChayeonText("다시 이야기하고 싶다면 애프터를 신청해 보세요. 둘 다 원할 때만 연락처가 오가요.",
                                    style = ChayeonType.SceneBody, color = ChayeonColors.Secondary)
                            }
                        }
                        if (!wide) Mascot(MascotPose.BYE, Modifier.size(ChayeonDimensions.PeerLarge).align(Alignment.CenterHorizontally))
                    }
                }
            },
        )
    }
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
        ScreenScaffold(showSourceLabels = chrome.showSourceLabels, modifier = Modifier.testTag("meet-screen"),
            scene = Scene.ROAD, contentPadding = meetScenePadding, topBar = chrome.topBar,
            bottomBar = {
                if (hasConnection) {
                    MeetError(leave.error)
                    PrimaryButton(if (leave.inProgress) "통화를 마치고 있어요" else "통화 끊기", onLeave,
                        Modifier.fillMaxWidth(), enabled = !leave.inProgress, access = ButtonAccess.SAFE_EXIT, driving = true)
                }
            }, primary = {
                MeetPane(centered = true) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        IconOrb(ChayeonIcon.LOCK, Modifier.size(ChayeonDimensions.Avatar), OrbVariant.GLASS)
                        ChayeonText(if (hasConnection) "새 연결이 있어요" else if (driving.policy == DrivingPolicy.UNKNOWN) "주행 상태를 확인하고 있어요" else "주행 중이에요", style = ChayeonType.CallTitle)
                        ChayeonText(if (hasConnection) "주차하면 볼 수 있어요" else if (driving.policy == DrivingPolicy.UNKNOWN) "주차 상태가 확인되면 다시 볼 수 있어요" else "주차하면 다시 볼 수 있어요",
                            style = ChayeonType.SceneBody, color = ChayeonColors.Secondary)
                    }
                }
            })
    }
}

@Composable
private fun MeetDiscoveryFacts() {
    MeetTwoColumns(
        first = { GlassPanel(Modifier.fillMaxWidth()) { InfoRow(ChayeonIcon.EYE, "내 카드를 직접 공개해요", body = "공개한 시그니처와 관심사로 서로를 알아봐요") } },
        second = { GlassPanel(Modifier.fillMaxWidth()) { InfoRow(ChayeonIcon.LOCK, "대화는 따로 수락해요", body = "수락하거나 주차 중 고른 자동 연결 조건에 맞을 때 이어져요") } },
    )
}

@Composable
private fun MeetRecommendation(candidates: List<Candidate>, onCandidate: (String) -> Unit,
    requestAction: (@Composable (Candidate, (Boolean) -> Unit) -> Unit)?, refresh: MeetCommandState, onRefresh: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val index = candidates.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val candidate = candidates[index]
    var requestBusy by remember(candidate.id) { mutableStateOf(false) }
    val peer = candidate.profile
    GlassPanel(Modifier.fillMaxWidth()) {
        MeetTwoColumns(
            first = {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ChayeonText("오늘의 인연  ${index + 1} / ${candidates.size}", Modifier.weight(1f).testTag("recommendation-page"), style = ChayeonType.SceneNavigation)
                        OutlineButton(if (refresh.inProgress) "확인 중이에요" else "새로 보기", onRefresh, enabled = !refresh.inProgress && !requestBusy, compact = true)
                    }
                    Row(Modifier.fillMaxWidth().semantics { contentDescription = "프로필 보기" }.clickable(enabled = !requestBusy, role = Role.Button, onClickLabel = "프로필 보기") { onCandidate(candidate.id) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.Avatar), decorative = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                            ChayeonText(peer.nickname, style = ChayeonType.CardTitle)
                            ChayeonText(peer.signature?.title ?: "시그니처를 만드는 중이에요", style = ChayeonType.SceneStatus, color = ChayeonColors.Secondary)
                            PublishedScoreChips(peer.signature)
                            candidate.chemistry.lines.firstOrNull()?.let { ChayeonText(it.text, style = ChayeonType.Label, color = ChayeonColors.Pink) }
                            MeetSourceLabel(peer)
                        }
                    }
                }
            },
            second = {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (requestAction != null) requestAction(candidate) { requestBusy = it }
                    else PrimaryButton("통화 요청하기", { onCandidate(candidate.id) }, Modifier.fillMaxWidth(), icon = ChayeonIcon.PHONE)
                    OutlineButton("다음 사람 보기", { selectedId = candidates[(index + 1) % candidates.size].id }, Modifier.fillMaxWidth(), enabled = candidates.size > 1 && !requestBusy)
                    InfoLine("수락하면 바로 통화가 연결돼요")
                }
            }, secondWeight = .62f,
        )
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
    MeetSceneSplit(chrome, MascotPose.THREAD, onBack,
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                if (incoming && state.homeSuppressed) TopBanner("집 근처에서 쉬는 동안에는 수락할 수 없어요")
                else MeetError(state.accept.error)
                MeetError(state.decline.error)
                MeetError(state.cancel.error)
                if (incoming) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    OutlineButton(if (state.decline.inProgress) "거절하고 있어요" else "거절", onDecline,
                        Modifier.weight(.35f).heightIn(min = ChayeonDimensions.ScenePrimary), enabled = !busy)
                    PrimaryButton(if (state.accept.inProgress) "연결을 확인하고 있어요" else "수락하고 통화하기", onAccept,
                        Modifier.weight(.65f), icon = ChayeonIcon.PHONE, enabled = !busy && !state.homeSuppressed)
                } else {
                    PrimaryButton(if (state.cancel.inProgress) "취소하고 있어요" else "요청 취소", onCancel,
                        Modifier.fillMaxWidth(), enabled = !state.cancel.inProgress)
                    OutlineButton("기다리는 동안 목록 보기", onBack, Modifier.fillMaxWidth())
                }
                ResponseCountdown(state.remainingMillis, totalMillis = request.expiresAtEpochMillis - request.createdAtEpochMillis)
            }
        },
    ) {
        InfoLine(if (incoming) "받은 통화 요청" else "보낸 통화 요청")
        HeroHeadline(if (incoming) "${request.peer.nickname}님이\n통화를 원해요" else "${request.peer.nickname}님의\n수락을 기다려요",
            "수락하면 바로 통화가 연결돼요", style = ChayeonType.SceneTitle)
        MeetProfilePanel(request.peer, request.chemistry,
            state.ownProfile?.interests.orEmpty().intersect(request.peer.interests))
        if (incoming) OutlineButton("차단·신고", onSafety, enabled = !busy, compact = true)
        MeetPrivacy()
    }
}
