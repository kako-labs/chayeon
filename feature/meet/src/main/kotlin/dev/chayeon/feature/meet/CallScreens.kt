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
internal fun MeetCallHero(state: ConnectedUiState, driving: Boolean = false, onSafety: (() -> Unit)? = null) {
    val connection = state.connection ?: return
    val title = when {
        connection.status == ConnectionStatus.ENDED -> "통화가 끝났어요"
        connection.status == ConnectionStatus.CHECKING -> "연결 상태를 확인하고 있어요"
        state.call.status == CallStatus.CONNECTING || state.call.status == CallStatus.RECONNECTING -> "통화를 연결하고 있어요"
        state.call.muted -> "음소거 중"
        else -> "통화 중"
    }
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        MeetRelationStage(state.ownProfile, connection.peer,
            if (connection.status == ConnectionStatus.ENDED) ThreadStage.ENDED else ThreadStage.CONNECTED,
            title, if (driving) "" else "근처에 있는 동안 목소리로 이야기해요",
            animate = !driving, centered = true, largeAvatars = true, largeNames = !driving,
            footer = if (!driving && state.callTopics().isEmpty && onSafety != null) ({
                Row(Modifier.fillMaxWidth().testTag("call-safety"), horizontalArrangement = Arrangement.End) {
                    JellyButton("차단·신고", onSafety, variant = JellyVariant.SECONDARY)
                }
            }) else null)
    }
}

@Composable
internal fun MeetCallControls(state: ConnectedUiState, onMute: () -> Unit, onLeave: () -> Unit) {
    val active = state.connection?.status == ConnectionStatus.CONNECTED && !state.awaitingPark
    val muteLabel = if (state.call.muted) "음소거 해제" else "음소거"
    MeetTwoColumns(
        first = {
            JellyButton(muteLabel, onMute,
                Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth).semantics { contentDescription = muteLabel },
                if (state.call.muted) JellyVariant.WHITE else JellyVariant.SECONDARY,
                enabled = active && state.driving.policy != DrivingPolicy.UNKNOWN && !state.leave.inProgress,
                allowWhenRestricted = state.driving.policy == DrivingPolicy.RESTRICTED)
        },
        second = {
            JellyButton(if (state.leave.inProgress) "통화를 마치고 있어요" else "통화 끊기", onLeave,
                Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth).semantics { contentDescription = "통화 끊기" },
                JellyVariant.PRIMARY,
                enabled = (state.canLeave || state.connection?.status?.let { it != ConnectionStatus.ENDED } == true) && !state.leave.inProgress,
                allowWhenRestricted = true)
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
            topBar = chrome.topBar,
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
            topBar = chrome.topBar,
            bottomBar = {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    if (state.homeSuppressed) TopBanner("집 근처에서 쉬는 동안에는 수락할 수 없어요")
                    MeetError(state.accept.error)
                    MeetError(state.decline.error)
                    MeetTwoColumns(
                        first = { JellyButton("거절", onDecline,
                            Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth).semantics { contentDescription = "거절" },
                            JellyVariant.SECONDARY, enabled = !busy, allowWhenRestricted = true) },
                        second = { JellyButton("받기", onAccept,
                            Modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.ToggleWidth).semantics { contentDescription = "받기" },
                            enabled = !busy && !state.homeSuppressed, allowWhenRestricted = true) },
                    )
                }
            }, primary = {
                MeetPane(centered = true) {
                    HeroPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                            ChayeonText("통화 요청", style = ChayeonType.Section, color = ChayeonColors.Pink)
                            AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.PeerLarge))
                            GradientText(peer.nickname, style = ChayeonType.Hero)
                            ChayeonText(peer.signature?.title ?: "시그니처를 만드는 중이에요", style = ChayeonType.Section)
                            if (LocalSourceLabels.current) ChayeonText(peer.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                        }
                    }
                }
            })
    }
}
