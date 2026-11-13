package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
internal fun MeetCallHero(state: ConnectedUiState, driving: Boolean = false) {
    val connection = state.connection ?: return
    val title = state.callStatusTitle()
    if (driving) {
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            AvatarDish(MochiAvatar.from(connection.peer.avatar), Modifier.size(ChayeonDimensions.PeerLarge))
            ChayeonText(connection.peer.nickname, style = ChayeonType.SceneTitle)
            connection.peer.signature?.let { signature ->
                ChayeonText(signature.title, style = ChayeonType.SceneName, color = ChayeonColors.Secondary)
                PublishedScoreChips(signature, limit = 2, large = true)
            }
            ChayeonText(title, style = ChayeonType.SceneBrand, color = ChayeonColors.Pink)
            MeetSourceLabel(connection.peer)
        }
        return
    }
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        ChayeonText("${connection.peer.nickname}님과 이야기하는 중", style = ChayeonType.CallTitle)
        ChayeonText("근처에 있는 동안 목소리로 이어져요", style = ChayeonType.SceneBody, color = ChayeonColors.Secondary)
        val own = state.ownProfile
        if (own != null) CallHero(CallParticipant(MochiAvatar.from(own.avatar), own.nickname, own.signature?.title),
            CallParticipant(MochiAvatar.from(connection.peer.avatar), connection.peer.nickname, connection.peer.signature?.title))
        else CallAvatar(MochiAvatar.from(connection.peer.avatar), mirrored = true)
        MeetSourceLabel(connection.peer)
    }
}

@Composable
internal fun MeetCallDetails(state: ConnectedUiState, onSafety: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            Row(Modifier.fillMaxWidth().testTag("call-safety"), verticalAlignment = Alignment.CenterVertically) {
                ChayeonText("${state.connection?.peer?.nickname.orEmpty()}님의 운전", Modifier.weight(1f), style = ChayeonType.SceneName)
                OutlineButton("차단·신고", onSafety, compact = true)
            }
            PublishedScoreChips(state.connection?.peer?.signature)
            MeetCallTopics(state)
        }
    }
}

@Composable
internal fun MeetCallControls(state: ConnectedUiState, onMute: () -> Unit, onLeave: () -> Unit) {
    val active = state.connection?.status == ConnectionStatus.CONNECTED && !state.awaitingPark
    val muteLabel = if (state.call.muted) "음소거 해제" else "음소거"
    val driving = state.driving.policy != DrivingPolicy.ALLOWED
    MeetTwoColumns(
        first = {
            MeetVoiceControl(muteLabel, onMute,
                enabled = active && state.driving.policy != DrivingPolicy.UNKNOWN && !state.leave.inProgress,
                driving = driving, endCall = false)
        },
        second = {
            MeetVoiceControl(if (state.leave.inProgress) "통화를 마치고 있어요" else "통화 끊기", onLeave,
                enabled = (state.canLeave || state.connection?.status?.let { it != ConnectionStatus.ENDED } == true) && !state.leave.inProgress,
                driving = driving, endCall = true)
        },
    )
}

@Composable
internal fun MeetConnectedDriving(state: ConnectedUiState, chrome: ScreenChrome, onMute: () -> Unit, onLeave: () -> Unit) {
    if (state.awaitingPark) {
        DrivingScreen(state.driving, chrome, state.canLeave, state.leave, onLeave)
        return
    }
    CompositionLocalProvider(LocalDecorativeMotion provides false) {
        ScreenScaffold(showSourceLabels = chrome.showSourceLabels, modifier = Modifier.testTag("meet-screen"),
            topBar = chrome.topBar, scene = Scene.ROAD, contentPadding = meetScenePadding,
            bottomBar = {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                    MeetError(state.leave.error)
                    MeetCallControls(state, onMute, onLeave)
                }
            }, primary = {
                MeetPane(centered = true) {
                    if (state.driving.policy == DrivingPolicy.UNKNOWN) TopBanner("주행 상태를 확인하고 있어요")
                    MeetCallHero(state, driving = true)
                    if (state.call.microphone == MicrophoneStatus.DENIED || state.call.microphone == MicrophoneStatus.UNAVAILABLE) {
                        ChayeonText("이 차에서는 마이크를 쓸 수 없어요", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                    }
                }
            })
    }
}

@Composable
internal fun MeetIncomingDriving(state: RequestUiState, chrome: ScreenChrome, onAccept: () -> Unit, onDecline: () -> Unit) {
    val peer = state.request?.peer ?: return
    val busy = state.accept.inProgress || state.decline.inProgress
    CompositionLocalProvider(LocalDecorativeMotion provides false) {
        ScreenScaffold(showSourceLabels = chrome.showSourceLabels, modifier = Modifier.testTag("meet-screen"),
            topBar = chrome.topBar, scene = Scene.ROAD, contentPadding = meetScenePadding,
            bottomBar = {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    if (state.homeSuppressed) TopBanner("집 근처에서 쉬는 동안에는 수락할 수 없어요")
                    MeetError(state.accept.error)
                    MeetError(state.decline.error)
                    MeetTwoColumns(
                        first = { OutlineButton("거절", onDecline,
                            Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth).semantics { contentDescription = "거절" },
                            enabled = !busy, access = ButtonAccess.RESTRICTED_CALL, driving = true) },
                        second = { PrimaryButton("받기", onAccept,
                            Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth).semantics { contentDescription = "받기" },
                            enabled = !busy && !state.homeSuppressed, access = ButtonAccess.RESTRICTED_CALL, driving = true, icon = ChayeonIcon.PHONE) },
                    )
                }
            }, primary = {
                MeetPane(centered = true) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                            ChayeonText("통화 요청", style = ChayeonType.SceneBrand, color = ChayeonColors.Pink)
                            AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.PeerLarge))
                            ChayeonText(peer.nickname, style = ChayeonType.SceneTitle)
                            ChayeonText(peer.signature?.title ?: "시그니처를 만드는 중이에요", style = ChayeonType.SceneName, color = ChayeonColors.Secondary)
                            PublishedScoreChips(peer.signature, limit = 2, large = true)
                            MeetSourceLabel(peer)
                        }
                    }
                }
            })
    }
}

private fun ConnectedUiState.callStatusTitle(): String {
    val connection = connection
    return when {
        connection?.status == ConnectionStatus.ENDED -> "통화가 끝났어요"
        connection?.status == ConnectionStatus.CHECKING -> "연결 상태를 확인하고 있어요"
        call.status == CallStatus.CONNECTING || call.status == CallStatus.RECONNECTING -> "통화를 연결하고 있어요"
        call.muted -> "음소거 중"
        else -> "통화 중"
    }
}

@Composable
internal fun MeetCallStatus(state: ConnectedUiState, elapsedMillis: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        StatusChip(state.callStatusTitle(), variant = StatusVariant.ACTIVE)
        elapsedMillis?.let { InfoLine("%02d:%02d".format(it / 60_000, (it / 1_000) % 60)) }
    }
}
