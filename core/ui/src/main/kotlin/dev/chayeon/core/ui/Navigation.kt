package dev.chayeon.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.Gear

@Composable
fun GearStrip(gear: Gear, policy: DrivingPolicy, syntheticLocation: Boolean, modifier: Modifier = Modifier, onDemo: (() -> Unit)? = null,
    resting: Boolean = false) {
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.SceneStatus), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact, Alignment.End)) {
        if (resting) StatusChip("쉬는 중", variant = StatusVariant.PLAIN)
        if (LocalSourceLabels.current && syntheticLocation) StatusChip("합성 위치", variant = StatusVariant.SYNTHETIC)
        if (onDemo != null && policy == DrivingPolicy.ALLOWED) Box(
            Modifier.size(ChayeonSpacing.Touch).testTag("demo-control-chip").semantics { contentDescription = "시연 제어" }
                .clickable(role = Role.Button, onClick = onDemo), contentAlignment = Alignment.Center,
        ) { ChayeonIcon(ChayeonIcon.PACE, Modifier.size(ChayeonDimensions.UtilityIcon), ChayeonColors.Secondary) }
        DrivingStatusPill(gear, policy)
    }
}

@Composable
fun DrivingStatusPill(gear: Gear, policy: DrivingPolicy, modifier: Modifier = Modifier) {
    val gearLabel = when (gear) { Gear.PARK -> "P"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.DRIVE -> "D"; Gear.UNKNOWN -> "—" }
    val label = when (policy) {
        DrivingPolicy.ALLOWED -> if (gear == Gear.PARK) "주차 중" else "주행 상태 확인 중"
        DrivingPolicy.RESTRICTED -> "주행 중"
        DrivingPolicy.UNKNOWN -> "주행 상태 확인 중"
    }
    Row(modifier.heightIn(min = ChayeonDimensions.SceneStatus).sceneGlass(ChayeonRadii.Card)
        .padding(start = ChayeonSpacing.ChipHorizontal, end = ChayeonSpacing.BubbleHorizontal, top = ChayeonSpacing.Compact, bottom = ChayeonSpacing.Compact),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(ChayeonDimensions.GearBadge)
            .border(ChayeonElevation.StrongBorder, ChayeonColors.BrightRed, RoundedCornerShape(ChayeonRadii.Small)), contentAlignment = Alignment.Center) {
            ChayeonText(gearLabel, style = ChayeonType.Label, color = ChayeonColors.BrightRed)
        }
        ChayeonText(label, style = ChayeonType.SceneStatus)
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(ChayeonDimensions.SceneBrandOrb).background(Brush.radialGradient(listOf(ChayeonColors.DishTop, ChayeonColors.Wine)), CircleShape)
            .border(ChayeonDimensions.Hairline, ChayeonColors.OutlineBorder, CircleShape), contentAlignment = Alignment.Center) {
            ChayeonIcon(ChayeonIcon.HEART, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.BrightRed)
        }
        ChayeonText("차연", style = ChayeonType.SceneBrand)
    }
}

@Composable
fun StepIndicator(current: Int, modifier: Modifier = Modifier, steps: List<String> = listOf("시작", "프로필", "공개 범위")) {
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
        ChayeonText(text, style = ChayeonType.Body, color = ChayeonColors.Ink, maxLines = 2)
    }
}

enum class NavigationItem(val label: String, val icon: ChayeonIcon) {
    MEET("홈", ChayeonIcon.HOME),
    RELATIONSHIPS("인연", ChayeonIcon.HEART),
    PROFILE("내 프로필", ChayeonIcon.PROFILE),
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun BottomNav(selected: NavigationItem, discoveryOn: Boolean, onSelect: (NavigationItem) -> Unit, onDiscoveryToggle: () -> Unit, modifier: Modifier = Modifier,
    resting: Boolean = false, incomingAfter: Boolean = false, incomingAfterCount: Int = if (incomingAfter) 1 else 0) {
    val interactive = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonDimensions.SceneNavigation).sceneGlass(ChayeonDimensions.SceneNavRadius)
        .padding(ChayeonSpacing.Compact), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact), verticalAlignment = Alignment.CenterVertically) {
        NavigationItem.entries.forEach { item ->
            val active = selected == item
            val shape = RoundedCornerShape(ChayeonSpacing.Hero)
            Row(Modifier.weight(1f).heightIn(min = ChayeonDimensions.SceneNavItem).clip(shape)
                .then(if (active) Modifier.background(Brush.verticalGradient(listOf(ChayeonColors.SelectedTop, ChayeonColors.SelectedBottom)))
                    .border(ChayeonDimensions.Hairline, ChayeonColors.SelectedBorder, shape) else Modifier)
                .clickable(enabled = interactive, role = Role.Tab) { onSelect(item) }
                .semantics { this.selected = active }, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                val color = if (active) ChayeonColors.White else ChayeonColors.NavigationText
                ChayeonIcon(item.icon, Modifier.size(ChayeonDimensions.UtilityIcon), color)
                ChayeonText(item.label, style = ChayeonType.SceneNavigation, color = color)
                if (interactive && item == NavigationItem.RELATIONSHIPS && incomingAfterCount > 0) Box(
                    Modifier.heightIn(min = ChayeonDimensions.SceneBadge).widthIn(min = ChayeonDimensions.SceneBadge)
                        .background(ChayeonColors.BrightRed, CircleShape)
                        .semantics { contentDescription = "새 애프터 요청 ${incomingAfterCount}개" }
                        .padding(horizontal = ChayeonSpacing.Small), contentAlignment = Alignment.Center,
                ) { ChayeonText(incomingAfterCount.toString(), style = ChayeonType.Minimum) }
            }
        }
    }
}

@Composable
fun DiscoveryChip(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    resting: Boolean = false, enabled: Boolean = true) {
    Box(modifier.sceneGlass(ChayeonRadii.Card).padding(horizontal = ChayeonSpacing.AvatarGap)) {
        GelToggle(checked, onCheckedChange, if (resting) "쉬는 중" else if (checked) "발견 켜짐" else "발견 꺼짐", enabled = enabled)
    }
}

/**
 * App-owned chrome. Screens place it through ScreenScaffold slots and never wire gear state,
 * discovery commands, or tab navigation themselves.
 */
@Stable
class ScreenChrome(
    val topBar: @Composable () -> Unit,
    val bottomBar: @Composable (NavigationItem) -> Unit,
    val showSourceLabels: Boolean = true,
    val discoveryOn: Boolean = false,
    val discoveryResting: Boolean = false,
    val discoveryBusy: Boolean = false,
    val onDiscoveryToggle: () -> Unit = {},
    val onViewing: (requestId: String?) -> (() -> Unit) = { {} },
)

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    primary: @Composable () -> Unit,
    secondary: (@Composable () -> Unit)? = null,
    showSourceLabels: Boolean = LocalSourceLabels.current,
    scene: Scene = LocalScene.current,
    sceneScrim: SceneScrim = LocalSceneScrim.current,
    contentPadding: PaddingValues = PaddingValues(horizontal = ChayeonSpacing.Screen, vertical = ChayeonSpacing.Small),
    sceneDimmed: Boolean = false,
) {
    CompositionLocalProvider(LocalSourceLabels provides showSourceLabels) {
        SceneBackground(if (LocalDrivingPolicy.current == DrivingPolicy.ALLOWED) scene else Scene.ROAD,
            modifier.fillMaxSize(), dimmed = sceneDimmed || LocalDrivingPolicy.current != DrivingPolicy.ALLOWED, scrim = sceneScrim) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val narrow = maxWidth < ChayeonSpacing.NarrowBreakpoint
                Column(Modifier.fillMaxSize().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
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
}

@Composable
fun ViewedContent(chrome: ScreenChrome, policy: DrivingPolicy, requestId: String? = null) {
    DisposableEffect(chrome, policy, requestId) {
        val release = if (policy == DrivingPolicy.ALLOWED && requestId != null)
            chrome.onViewing(requestId) else ({})
        onDispose { release() }
    }
}
