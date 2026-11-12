package dev.chayeon.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.Gear
import dev.chayeon.core.model.PublicProfile

enum class PeerHeaderSize { SMALL, LARGE }

@Composable
fun GradientText(text: String, modifier: Modifier = Modifier, style: TextStyle = ChayeonType.Hero) {
    val rendered = remember(text) { keepHangulWords(text) }
    BasicText(rendered, modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style.copy(brush = Brush.verticalGradient(.2f to ChayeonColors.White, 1f to ChayeonColors.Accent)))
}

@Composable
fun PeerHeader(profile: PublicProfile, modifier: Modifier = Modifier, size: PeerHeaderSize = PeerHeaderSize.LARGE) {
    if (size == PeerHeaderSize.LARGE) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
            AvatarDish(MochiAvatar.from(profile.avatar), Modifier.size(ChayeonDimensions.PeerLarge))
            ChayeonText(profile.nickname, style = ChayeonType.Title)
            if (LocalSourceLabels.current) StatusChip(profile.kind.label, variant = StatusVariant.SYNTHETIC)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
            AvatarDish(MochiAvatar.from(profile.avatar), Modifier.size(ChayeonDimensions.PeerSmall))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
                ChayeonText(profile.nickname, style = ChayeonType.Section)
                if (LocalSourceLabels.current) ChayeonText(profile.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppHeader(modifier: Modifier = Modifier, title: String = "차연", subtitle: String = "길 위에서 시작되는 인연", profile: PublicProfile? = null, onProfileClick: () -> Unit = {}) {
    val interactive = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
            Image(painterResource(ChayeonAssets.Logo), null,
                Modifier.size(ChayeonDimensions.Logo).drawBehind {
                    drawCircle(Brush.radialGradient(listOf(ChayeonColors.Glow.copy(alpha = .45f), ChayeonColors.Transparent), radius = size.width * .7f), radius = size.width * .7f)
                }.clip(CircleShape).border(ChayeonElevation.Border, ChayeonColors.ButtonBorder, CircleShape))
            Column {
                GradientText(title, Modifier.semantics { heading() }, ChayeonType.Brand)
                ChayeonText(subtitle, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
            }
        }
        if (profile != null) {
            Row(Modifier.heightIn(min = ChayeonSpacing.Button).gelSurface(JellyVariant.SECONDARY, ChayeonRadii.Banner)
                .clickable(enabled = interactive, role = Role.Button, onClick = onProfileClick)
                .padding(horizontal = ChayeonSpacing.Compact, vertical = ChayeonSpacing.Small),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                AvatarDish(MochiAvatar.from(profile.avatar), Modifier.size(ChayeonDimensions.ProfileAvatar), decorative = true)
                Column {
                    ChayeonText(profile.nickname, style = ChayeonType.Label)
                    if (LocalSourceLabels.current) ChayeonText(profile.kind.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                }
                ChayeonIcon(ChayeonIcon.FORWARD, Modifier.size(ChayeonDimensions.SmallIcon), contentDescription = "내 프로필 보기")
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        ChayeonText(title, Modifier.semantics { heading() }, ChayeonType.Section)
        if (subtitle != null) ChayeonText(subtitle, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
    }
}

@Composable
fun ProgressDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    require(total > 0 && current in 0 until total)
    Row(modifier.clearAndSetSemantics { contentDescription = "${total}단계 중 ${current + 1}단계" },
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        repeat(total) { index ->
            Box(Modifier.width(if (index == current) ChayeonDimensions.ProgressActive else ChayeonDimensions.ProgressDot)
                .height(ChayeonDimensions.ProgressDot).clip(CircleShape)
                .background(if (index == current) ChayeonColors.Pink else ChayeonColors.White.copy(alpha = .24f)))
        }
    }
}

@Composable
fun InfoRow(icon: ChayeonIcon, title: String, modifier: Modifier = Modifier, body: String? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
        IconOrb(icon, variant = OrbVariant.GLASS)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
            ChayeonText(title, style = ChayeonType.Body)
            if (body != null) ChayeonText(body, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        }
    }
}

@Composable
fun SettingsRow(icon: ChayeonIcon, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactive = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonSpacing.Touch).glassSurface()
        .clickable(enabled = interactive, role = Role.Button, onClick = onClick).padding(ChayeonSpacing.Large),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        InfoRow(icon, title, Modifier.weight(1f), body)
        ChayeonIcon(ChayeonIcon.FORWARD, Modifier.size(ChayeonDimensions.SmallIcon))
    }
}

@Composable
fun ResponseCountdown(remainingMillis: Long?, modifier: Modifier = Modifier, totalMillis: Long? = null) {
    val label = countdownLabel(remainingMillis)
    Column(modifier.clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
            ChayeonIcon(ChayeonIcon.CLOCK, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Pink)
            ChayeonText(label, style = ChayeonType.Label, color = ChayeonColors.Secondary)
        }
        if (remainingMillis != null && totalMillis != null && totalMillis > 0) {
            Box(Modifier.fillMaxWidth().height(ChayeonDimensions.CountdownTrack).clip(CircleShape).background(ChayeonColors.White.copy(alpha = .12f))) {
                Box(Modifier.fillMaxWidth((remainingMillis.toDouble() / totalMillis).toFloat().coerceIn(0f, 1f))
                    .height(ChayeonDimensions.CountdownTrack).background(Brush.horizontalGradient(listOf(ChayeonColors.Red, ChayeonColors.Pink))))
            }
        }
    }
}

internal fun countdownLabel(remainingMillis: Long?): String {
    if (remainingMillis == null) return "응답 시간을 확인하고 있어요"
    val millis = remainingMillis.coerceAtLeast(0)
    val seconds = millis / 1_000 + if (millis % 1_000 > 0) 1 else 0
    if (seconds == 0L) return "응답 결과를 확인하고 있어요"
    val hours = seconds / 3_600
    return if (hours > 0) "${hours}시간 ${(seconds % 3_600) / 60}분 남았어요"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} 남았어요"
}

fun previewChrome(
    gear: Gear = Gear.PARK,
    policy: DrivingPolicy = DrivingPolicy.ALLOWED,
    discoveryOn: Boolean = true,
    syntheticLocation: Boolean = true,
    resting: Boolean = false,
    showSourceLabels: Boolean = true,
): ScreenChrome = ScreenChrome(
    showSourceLabels = showSourceLabels,
    topBar = { GearStrip(gear, policy, syntheticLocation) },
    bottomBar = { selected -> BottomNav(selected, discoveryOn, {}, {}, resting = resting) },
)
