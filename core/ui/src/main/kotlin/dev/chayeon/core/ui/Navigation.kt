package dev.chayeon.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.Gear

@Composable
fun GearStrip(gear: Gear, policy: DrivingPolicy, syntheticLocation: Boolean, modifier: Modifier = Modifier, onDemo: (() -> Unit)? = null,
    resting: Boolean = false) {
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.GearStrip), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
        val gearLabel = when (gear) { Gear.PARK -> "P"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.DRIVE -> "D"; Gear.UNKNOWN -> "—" }
        Box(Modifier.size(ChayeonDimensions.GearBadge).background(ChayeonColors.Glow.copy(alpha = .24f), RoundedCornerShape(ChayeonRadii.Small))
            .border(ChayeonElevation.Border, ChayeonColors.Accent.copy(alpha = .60f), RoundedCornerShape(ChayeonRadii.Small)), contentAlignment = Alignment.Center) {
            ChayeonText(gearLabel, style = ChayeonType.Label, color = ChayeonColors.Pink)
        }
        ChayeonText(when (policy) {
            DrivingPolicy.ALLOWED -> if (gear == Gear.PARK) "주차 중" else "주행 상태 확인 중"
            DrivingPolicy.RESTRICTED -> "주행 중"
            DrivingPolicy.UNKNOWN -> "주행 상태 확인 중"
        }, style = ChayeonType.Label, color = ChayeonColors.Secondary)
        if (resting) StatusChip("쉬는 중", variant = StatusVariant.PLAIN)
        Spacer(Modifier.weight(1f))
        if (syntheticLocation) StatusChip("합성 위치", variant = StatusVariant.SYNTHETIC)
        if (onDemo != null && policy == DrivingPolicy.ALLOWED) Box(
            Modifier.heightIn(min = ChayeonSpacing.Touch).widthIn(min = ChayeonSpacing.Touch)
                .testTag("demo-control-chip").clickable(role = Role.Button, onClick = onDemo),
            contentAlignment = Alignment.Center,
        ) { StatusChip("시연 제어", variant = StatusVariant.SYNTHETIC) }
    }
}

@Composable
fun StepIndicator(current: Int, modifier: Modifier = Modifier, steps: List<String> = listOf("계정", "프로필", "공개 범위")) {
    Row(modifier.semantics { contentDescription = "참여 준비 ${current + 1} / ${steps.size} 단계" }, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        steps.forEachIndexed { index, title ->
            val active = current == index
            Row(Modifier.gelSurface(if (active) JellyVariant.WHITE else JellyVariant.SECONDARY, ChayeonRadii.Row)
                .padding(start = ChayeonSpacing.Small, top = ChayeonSpacing.Small, end = ChayeonSpacing.StepHorizontal, bottom = ChayeonSpacing.Small), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                Box(Modifier.size(ChayeonDimensions.StepDot).background(if (active) ChayeonColors.Red else ChayeonColors.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    ChayeonText("${index + 1}", style = ChayeonType.Label)
                }
                ChayeonText(title, style = ChayeonType.Secondary, color = if (active) ChayeonColors.DeepRed else ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
fun TopBanner(text: String, modifier: Modifier = Modifier, icon: ChayeonIcon = ChayeonIcon.BELL) {
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.Banner).gelSurface(JellyVariant.WHITE, ChayeonRadii.Banner)
        .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = ChayeonSpacing.BubbleHorizontal, vertical = ChayeonSpacing.ChipHorizontal),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        ChayeonIcon(icon, Modifier.size(ChayeonDimensions.UtilityIcon), ChayeonColors.DeepRed)
        ChayeonText(text, style = ChayeonType.Body, color = ChayeonColors.Ink)
    }
}

enum class NavigationItem(val label: String, val icon: ChayeonIcon) {
    MEET("오늘의 인연", ChayeonIcon.THREAD),
    HISTORY("스친 인연", ChayeonIcon.HISTORY),
    AFTER("이어진 인연", ChayeonIcon.KNOT),
    PROFILE("내 프로필", ChayeonIcon.PROFILE),
}

@Composable
fun BottomNav(selected: NavigationItem, discoveryOn: Boolean, onSelect: (NavigationItem) -> Unit, onDiscoveryToggle: () -> Unit, modifier: Modifier = Modifier,
    resting: Boolean = false) {
    val interactive = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.Navigation).glassSurface(radius = ChayeonRadii.Banner), verticalAlignment = Alignment.CenterVertically) {
        NavigationItem.entries.forEachIndexed { index, item ->
            if (index == 2) {
                Box(Modifier.width(ChayeonDimensions.Avatar), contentAlignment = Alignment.Center) {
                    Column(Modifier.size(ChayeonDimensions.CenterOrb).drawBehind {
                        drawCircle(Brush.radialGradient(listOf(ChayeonColors.Glow.copy(alpha = .48f), ChayeonColors.Transparent), radius = size.width * .78f), radius = size.width * .78f)
                    }.clip(CircleShape).drawBehind {
                        drawCircle(Brush.radialGradient(
                            if (discoveryOn) listOf(ChayeonColors.OrbTop, ChayeonColors.OrbMiddle, ChayeonColors.OrbBottom)
                            else listOf(ChayeonColors.DishTop, ChayeonColors.DishMiddle, ChayeonColors.DishBottom),
                            center = Offset(size.width * .34f, size.height * .24f), radius = size.width,
                        ))
                        drawCircle(Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = .22f), ChayeonColors.Transparent), endY = size.height * .55f))
                    }.border(ChayeonElevation.StrongBorder, ChayeonColors.ButtonBorder.copy(alpha = .8f), CircleShape)
                        .toggleable(discoveryOn, enabled = interactive, role = Role.Switch, onValueChange = { onDiscoveryToggle() })
                        .semantics { contentDescription = if (discoveryOn) "발견 끄기" else "발견 켜기" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny, Alignment.CenterVertically)) {
                        ChayeonIcon(ChayeonIcon.THREAD, Modifier.size(ChayeonDimensions.NavigationIcon))
                        ChayeonText(if (discoveryOn && resting) "쉬는 중" else if (discoveryOn) "발견 중" else "꺼짐", style = ChayeonType.Label)
                    }
                }
            }
            Column(Modifier.weight(1f).heightIn(min = ChayeonSpacing.Touch)
                .clickable(enabled = interactive, role = Role.Tab) { onSelect(item) }
                .semantics { this.selected = selected == item }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny, Alignment.CenterVertically)) {
                val color = if (selected == item) ChayeonColors.Accent else ChayeonColors.Secondary
                ChayeonIcon(item.icon, Modifier.size(ChayeonDimensions.NavigationIcon), color)
                ChayeonText(item.label, style = ChayeonType.Minimum, color = color)
            }
        }
    }
}

/**
 * App-owned chrome. Screens place it through ScreenScaffold slots and never wire gear state,
 * the discovery orb, or tab navigation themselves.
 */
@Stable
class ScreenChrome(
    val topBar: @Composable () -> Unit,
    val bottomBar: @Composable (NavigationItem) -> Unit,
    val onViewing: (requestId: String?, relationshipId: String?) -> (() -> Unit) = { _, _ -> {} },
)

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    primary: @Composable () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
) {
    ChayeonBackground(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < ChayeonSpacing.NarrowBreakpoint
            Column(Modifier.fillMaxSize().padding(horizontal = ChayeonSpacing.Screen, vertical = ChayeonSpacing.ChipHorizontal), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                topBar()
                if (narrow) {
                    val scroll = rememberScrollState()
                    Column(Modifier.weight(1f).fillMaxWidth().scrollEdgeFade(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        primary()
                        secondary?.invoke()
                    }
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                        Box(Modifier.weight(1f).fillMaxSize()) { primary() }
                        if (secondary != null) Box(Modifier.weight(1f).fillMaxSize()) { secondary() }
                    }
                }
                bottomBar()
            }
        }
    }
}

@Composable
fun ViewedContent(chrome: ScreenChrome, policy: DrivingPolicy, requestId: String? = null, relationshipId: String? = null) {
    DisposableEffect(chrome, policy, requestId, relationshipId) {
        val release = if (policy == DrivingPolicy.ALLOWED && (requestId != null || relationshipId != null))
            chrome.onViewing(requestId, relationshipId) else ({})
        onDispose { release() }
    }
}
