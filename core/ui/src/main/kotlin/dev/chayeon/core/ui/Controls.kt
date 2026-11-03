package dev.chayeon.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chayeon.core.model.DrivingPolicy

enum class JellyVariant { PRIMARY, SECONDARY, WHITE, DANGER }
enum class StatusVariant { ACTIVE, PLAIN, WARNING, SYNTHETIC }
enum class OrbVariant { RED, GLASS, WHITE }

@Composable
fun JellyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: JellyVariant = JellyVariant.PRIMARY,
    enabled: Boolean = true,
    allowWhenRestricted: Boolean = false,
) {
    val interactive = enabled && (LocalDrivingPolicy.current == DrivingPolicy.ALLOWED || allowWhenRestricted)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && interactive) .96f else 1f,
        if (LocalDecorativeMotion.current) tween(ChayeonMotion.Press) else snap(), label = "gel press")
    Box(modifier.heightIn(min = ChayeonSpacing.Button)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .alpha(if (interactive) 1f else .48f)
        .gelSurface(variant)
        .clickable(source, indication = null, enabled = interactive, role = Role.Button, onClick = onClick)
        .padding(horizontal = ChayeonSpacing.Large, vertical = ChayeonSpacing.Medium), contentAlignment = Alignment.Center) {
        ChayeonText(text, style = ChayeonType.Button, color = when (variant) {
            JellyVariant.WHITE -> ChayeonColors.DeepRed
            JellyVariant.DANGER -> ChayeonColors.Accent
            else -> ChayeonColors.White
        })
    }
}

@Composable
fun IconOrb(
    icon: ChayeonIcon,
    modifier: Modifier = Modifier,
    variant: OrbVariant = OrbVariant.RED,
    contentDescription: String? = null,
) {
    val colors = when (variant) {
        OrbVariant.RED -> listOf(ChayeonColors.OrbTop, ChayeonColors.OrbMiddle, ChayeonColors.OrbBottom)
        OrbVariant.GLASS -> listOf(ChayeonColors.White.copy(alpha = .28f), ChayeonColors.GlassOrbMiddle.copy(alpha = .36f), ChayeonColors.GlassOrbBottom.copy(alpha = .7f))
        OrbVariant.WHITE -> listOf(ChayeonColors.White, ChayeonColors.KnobBottom, ChayeonColors.WhiteOrbBottom)
    }
    Box(modifier.size(ChayeonSpacing.Touch).drawBehind {
        drawCircle(Brush.radialGradient(listOf(ChayeonColors.Glow.copy(alpha = .16f), ChayeonColors.Transparent), radius = size.minDimension * .7f), radius = size.minDimension * .7f)
    }.clip(CircleShape).background(Brush.radialGradient(colors, center = Offset(24f, 18f), radius = 110f))
        .border(ChayeonElevation.Border, ChayeonColors.ButtonBorder.copy(alpha = .45f), CircleShape)
        .drawBehind {
            drawCircle(Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = .25f), ChayeonColors.Transparent, ChayeonColors.Black.copy(alpha = .24f))))
        }, contentAlignment = Alignment.Center) {
        ChayeonIcon(icon, Modifier.size(ChayeonDimensions.OrbIcon), if (variant == OrbVariant.WHITE) ChayeonColors.DeepRed else ChayeonColors.Text, contentDescription)
    }
}

@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, variant: StatusVariant = StatusVariant.ACTIVE) {
    val shape = RoundedCornerShape(ChayeonRadii.Chip)
    val background = when (variant) {
        StatusVariant.ACTIVE -> ChayeonColors.Glow.copy(alpha = .24f)
        StatusVariant.PLAIN -> ChayeonColors.White.copy(alpha = .12f)
        StatusVariant.SYNTHETIC -> ChayeonColors.White.copy(alpha = .08f)
        StatusVariant.WARNING -> ChayeonColors.WhiteBottom
    }
    val foreground = when (variant) {
        StatusVariant.ACTIVE -> ChayeonColors.Pink
        StatusVariant.WARNING -> ChayeonColors.Ink
        else -> ChayeonColors.Text
    }
    Row(modifier.clip(shape).background(background).border(ChayeonElevation.Border,
        if (variant == StatusVariant.ACTIVE) ChayeonColors.Accent.copy(alpha = .6f) else ChayeonColors.White.copy(alpha = .30f), shape)
        .padding(horizontal = ChayeonSpacing.ChipHorizontal, vertical = ChayeonSpacing.Small), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalAlignment = Alignment.CenterVertically) {
        if (variant == StatusVariant.ACTIVE) Box(Modifier.size(ChayeonSpacing.TagGap).background(ChayeonColors.BrightRed, CircleShape))
        ChayeonText(text, style = ChayeonType.Label, color = foreground)
    }
}

@Composable
fun InterestTag(text: String, modifier: Modifier = Modifier, matched: Boolean = false) {
    val surface = if (matched) Modifier.gelSurface(JellyVariant.WHITE, ChayeonRadii.Tag)
        else Modifier.background(ChayeonColors.Black.copy(alpha = .35f), RoundedCornerShape(ChayeonRadii.Tag))
            .border(ChayeonElevation.Border, ChayeonColors.Border, RoundedCornerShape(ChayeonRadii.Tag))
    Box(modifier.then(surface).padding(horizontal = ChayeonSpacing.AvatarGap, vertical = ChayeonSpacing.TagVertical)) {
        ChayeonText(text, style = ChayeonType.Secondary, color = if (matched) ChayeonColors.DeepRed else ChayeonColors.Secondary)
    }
}

@Composable
fun GelToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactive = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Row(modifier.heightIn(min = ChayeonSpacing.Touch).toggleable(checked, enabled = interactive, role = Role.Switch, onValueChange = onCheckedChange)
        .alpha(if (interactive) 1f else .48f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
        ChayeonText(label, Modifier.weight(1f), ChayeonType.Body)
        Box(Modifier.width(ChayeonDimensions.ToggleWidth).heightIn(min = ChayeonDimensions.ToggleHeight).clip(CircleShape)
            .background(Brush.verticalGradient(if (checked) listOf(ChayeonColors.BrightRed, ChayeonColors.ToggleBottom) else listOf(ChayeonColors.Black.copy(alpha = .65f), ChayeonColors.Black.copy(alpha = .35f))))
            .border(ChayeonElevation.Border, ChayeonColors.White.copy(alpha = .20f), CircleShape).padding(ChayeonSpacing.ControlInset), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(ChayeonDimensions.ToggleKnob).background(Brush.radialGradient(if (checked) listOf(ChayeonColors.White, ChayeonColors.KnobBottom) else listOf(ChayeonColors.KnobOff, ChayeonColors.KnobOff), center = Offset(16f, 12f), radius = 54f), CircleShape))
        }
    }
}

@Composable
fun GelRadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null, enabled: Boolean = true) {
    val interactive = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonSpacing.Touch).glassSurface(radius = ChayeonRadii.Row)
        .selectable(selected, enabled = interactive, role = Role.RadioButton, onClick = onClick)
        .padding(ChayeonSpacing.Large), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
        Box(Modifier.size(ChayeonDimensions.Radio).border(ChayeonElevation.StrongBorder, if (selected) ChayeonColors.White else ChayeonColors.Secondary, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(ChayeonSpacing.BubbleHorizontal).background(Brush.radialGradient(listOf(ChayeonColors.BrightRed, ChayeonColors.DeepRed)), CircleShape))
        }
        Column {
            ChayeonText(title, style = ChayeonType.Body)
            if (description != null) ChayeonText(description, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        }
    }
}

@Composable
fun GelCheckbox(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null, enabled: Boolean = true) {
    val interactive = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Row(modifier.fillMaxWidth().heightIn(min = ChayeonSpacing.Touch).glassSurface(radius = ChayeonRadii.Row)
        .toggleable(checked, enabled = interactive, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(ChayeonSpacing.Large), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
        Box(Modifier.size(ChayeonDimensions.Checkbox).gelSurface(if (checked) JellyVariant.PRIMARY else JellyVariant.SECONDARY, ChayeonRadii.Check), contentAlignment = Alignment.Center) {
            if (checked) ChayeonIcon(ChayeonIcon.CHECK, Modifier.size(ChayeonDimensions.UtilityIcon))
        }
        Column {
            ChayeonText(title, style = ChayeonType.Body)
            if (description != null) ChayeonText(description, style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, allowWhenRestricted: Boolean = false) {
    Box(modifier.size(ChayeonSpacing.Touch).gelSurface(JellyVariant.SECONDARY, ChayeonRadii.Banner)
        .clickable(enabled = enabled && (LocalDrivingPolicy.current == DrivingPolicy.ALLOWED || allowWhenRestricted), role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "뒤로" }, contentAlignment = Alignment.Center) {
        ChayeonIcon(ChayeonIcon.BACK, Modifier.size(ChayeonDimensions.BackIcon))
    }
}
