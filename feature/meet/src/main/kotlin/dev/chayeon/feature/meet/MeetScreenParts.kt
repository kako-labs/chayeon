package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
internal fun MeetPane(centered: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(if (centered) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        val scroll = rememberScrollState()
        val viewport = if (constraints.hasBoundedHeight) Modifier.scrollEdgeFade(scroll).verticalScroll(scroll) else Modifier
        val minimum = if (centered && constraints.hasBoundedHeight) Modifier.heightIn(min = maxHeight) else Modifier
        Column(Modifier.fillMaxWidth().then(viewport).then(minimum),
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap, if (centered) Alignment.CenterVertically else Alignment.Top), content = content)
    }
}

@Composable
internal fun MeetHeader(chrome: ScreenChrome, title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        chrome.topBar()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.ChipHorizontal)) {
            if (onBack != null) BackButton(onBack)
            Column(Modifier.weight(1f)) {
                GradientText(title, style = ChayeonType.Title)
                ChayeonText(subtitle, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
internal fun MeetError(error: AppError?) {
    if (error != null) {
        ChayeonText(error.userMessage, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ChayeonType.Secondary, ChayeonColors.Pink)
    }
}

@Composable
internal fun MeetPrivacy() {
    ChayeonText("위치와 차량 정보는 서로에게 보이지 않아요", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
}

@Composable
internal fun MeetDiscoveryHero(state: HomeUiState, onToggle: (Boolean) -> Unit) {
    val enabled = state.discovery.mode == DiscoveryMode.ON
    val resting = state.homeZone.suppressed
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint
        Box(Modifier.fillMaxWidth().then(if (wide && !resting) Modifier.height(ChayeonDimensions.SceneMascot - ChayeonDimensions.SceneShadow) else if (wide) Modifier.heightIn(min = ChayeonDimensions.SceneMascot + ChayeonDimensions.PeerLarge) else Modifier)) {
            if (wide) Mascot(if (resting) MascotPose.SLEEP else MascotPose.WAVE,
                Modifier.align(Alignment.CenterEnd).size(ChayeonDimensions.SceneMascot))
            Column(Modifier.fillMaxWidth(if (wide) .56f else 1f).padding(top = if (resting) ChayeonSpacing.Touch else ChayeonSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                DiscoveryChip(enabled, onToggle, Modifier.width(ChayeonDimensions.Knot), resting = resting, enabled = !state.toggle.inProgress)
                HeroHeadline(
                    title = if (resting) when (state.homeZone.status) {
                        HomeZoneStatus.PERMISSION_OFF -> "위치 사용이 꺼져 있어 쉬고 있어요"
                        HomeZoneStatus.LOCATION_UNKNOWN -> "위치를 확인할 때까지 쉬고 있어요"
                        else -> "집 근처에서는\n쉬고 있어요"
                    } else if (!enabled) "지금은 아무에게도 보이지 않아요" else when (state.discovery.status) {
                        DiscoveryStatus.AVAILABLE -> "근처에 인연이 있어요"
                        DiscoveryStatus.ALREADY_CONNECTED -> "지금 이어진 인연이 있어요"
                        else -> "새로운 인연을 천천히 만나봐요"
                    },
                    supportingLine = if (resting) when (state.homeZone.status) {
                        HomeZoneStatus.PERMISSION_OFF -> "위치 권한과 내 프로필의 위치 사용 동의를 확인해 주세요"
                        HomeZoneStatus.LOCATION_UNKNOWN -> "위치 서비스를 켜고 잠시 기다려 주세요. 그동안 발견을 쉬어요"
                        else -> "벗어나면 다시 찾을 수 있어요"
                    } else if (enabled) "발견을 켠 운전자끼리만 서로 보여요" else "켜면 닉네임과 공개한 시그니처, 관심사가 보여요",
                )
                if (resting) {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        InfoRow(ChayeonIcon.LOCK, "집 위치는 이 차에만 있어요", body = "서버와 상대에게는 보내지 않아요")
                    }
                    if (LocalSourceLabels.current && state.homeZone.source == LocationSource.SYNTHETIC) InfoLine("합성 위치 · 가상 집")
                } else if (enabled && state.discovery.status == DiscoveryStatus.AVAILABLE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        StatTile(ChayeonIcon.PEOPLE, "지금 근처", nearbyCountLabel(state.discovery.candidates), Modifier.weight(.8f))
                        GlassPanel(Modifier.weight(1.2f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                InfoLine("내 칭호")
                                ChayeonText(state.profile?.signature?.title ?: "시그니처를 만드는 중이에요", style = ChayeonType.SceneName)
                            }
                        }
                    }
                }
                MeetError(state.toggle.error)
                if (!wide && resting) Mascot(MascotPose.SLEEP, Modifier.size(ChayeonDimensions.PeerLarge).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
internal fun MeetTwoColumns(first: @Composable () -> Unit, second: @Composable () -> Unit, secondWeight: Float = 1f) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= ChayeonSpacing.NarrowBreakpoint) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(secondWeight)) { second() }
        } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            first()
            second()
        }
    }
}

internal fun nearbyCountLabel(candidates: List<Candidate>): String = "${candidates.size}명"
