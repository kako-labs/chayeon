package dev.chayeon.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.PublicTrait
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TraitLevel

@Composable
fun HeroHeadline(title: String, supportingLine: String, modifier: Modifier = Modifier,
    style: TextStyle = ChayeonType.Headline) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
        ChayeonText(title, Modifier.semantics { heading() }, style)
        ChayeonText(supportingLine, style = ChayeonType.SceneBody, color = ChayeonColors.MutedText)
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(ChayeonDimensions.ScenePanelPadding),
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.sceneGlass().padding(contentPadding), content = content)
}

internal fun Modifier.sceneGlass(radius: androidx.compose.ui.unit.Dp = ChayeonDimensions.ScenePanelRadius): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(Brush.verticalGradient(listOf(ChayeonColors.SceneGlassTop, ChayeonColors.SceneGlass)))
        .border(ChayeonDimensions.Hairline, ChayeonColors.SceneBorder, shape)
}

@Composable
fun StatTile(icon: ChayeonIcon, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.sceneGlass(ChayeonDimensions.SceneTileRadius).padding(ChayeonSpacing.CardHorizontal),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
        ChayeonIcon(icon, Modifier.size(ChayeonDimensions.OrbIcon), ChayeonColors.BrightRed)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
            ChayeonText(label, style = ChayeonType.Minimum, color = ChayeonColors.InfoText)
            ChayeonText(value, style = ChayeonType.TileValue)
        }
    }
}

@Composable
fun ScoreChip(trait: PublicTrait, modifier: Modifier = Modifier, large: Boolean = false) {
    if (trait.score !in 60..100 || trait.level == TraitLevel.RECORDING ||
        trait.trait == TraitId.LONG_ROAD || trait.trait == TraitId.NIGHT_DRIVE) return
    val name = trait.trait.presentation.name
    Row(modifier.heightIn(min = if (large) ChayeonDimensions.SceneScoreLarge else ChayeonDimensions.SceneScore)
        .clip(CircleShape).background(if (large) ChayeonColors.Ink.copy(alpha = .7f) else ChayeonColors.ScoreSurface)
        .border(ChayeonDimensions.Hairline, ChayeonColors.SceneBorder, CircleShape)
        .clearAndSetSemantics { text = AnnotatedString("$name ${trait.score}") }
        .padding(horizontal = if (large) ChayeonSpacing.BubbleHorizontal else ChayeonSpacing.AvatarGap, vertical = ChayeonSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalAlignment = Alignment.CenterVertically) {
        ChayeonText(name, style = if (large) ChayeonType.SceneNavigation else ChayeonType.Label)
        ChayeonText("${trait.score}", style = if (large) ChayeonType.DrivingScore else ChayeonType.Score, color = ChayeonColors.Accent)
    }
}

enum class ButtonAccess { PARKED_ONLY, RESTRICTED_CALL, SAFE_EXIT }

internal fun ButtonAccess.permits(policy: DrivingPolicy): Boolean = when (this) {
    ButtonAccess.PARKED_ONLY -> policy == DrivingPolicy.ALLOWED
    ButtonAccess.RESTRICTED_CALL -> policy != DrivingPolicy.UNKNOWN
    ButtonAccess.SAFE_EXIT -> true
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ChayeonIcon? = null,
    enabled: Boolean = true, access: ButtonAccess = ButtonAccess.PARKED_ONLY, driving: Boolean = false, compact: Boolean = false) {
    SceneButton(text, onClick, modifier, icon, enabled, access, driving, primary = true, compact = compact)
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ChayeonIcon? = null,
    enabled: Boolean = true, access: ButtonAccess = ButtonAccess.PARKED_ONLY, driving: Boolean = false, compact: Boolean = false) {
    SceneButton(text, onClick, modifier, icon, enabled, access, driving, primary = false, compact = compact)
}

@Composable
private fun SceneButton(text: String, onClick: () -> Unit, modifier: Modifier, icon: ChayeonIcon?, enabled: Boolean,
    access: ButtonAccess, driving: Boolean, primary: Boolean, compact: Boolean) {
    val policy = LocalDrivingPolicy.current
    val interactive = enabled && access.permits(policy)
    val drivingSize = driving || policy != DrivingPolicy.ALLOWED
    val shape = RoundedCornerShape(if (drivingSize) ChayeonDimensions.SceneShadow else if (primary) ChayeonDimensions.SceneButtonRadius else ChayeonDimensions.SceneOutlineRadius)
    val glow = if (primary && !drivingSize) Modifier.shadow(ChayeonDimensions.SceneShadow, shape, clip = false,
        ambientColor = ChayeonColors.Glow, spotColor = ChayeonColors.Glow) else Modifier
    Row(modifier.heightIn(min = if (driving) ChayeonDimensions.DrivingControl else if (drivingSize) ChayeonDimensions.DrivingTouch else if (compact) ChayeonSpacing.Touch else if (primary) ChayeonDimensions.ScenePrimary else ChayeonSpacing.Button)
        .widthIn(min = if (drivingSize) ChayeonDimensions.DrivingTouch else ChayeonSpacing.Touch)
        .alpha(if (interactive) 1f else .48f).then(glow).clip(shape)
        .background(if (primary) Brush.verticalGradient(0f to ChayeonColors.BrightRed, .55f to ChayeonColors.Red, 1f to ChayeonColors.DeepRed)
            else Brush.verticalGradient(listOf(ChayeonColors.SceneGlass, ChayeonColors.SceneGlass)))
        .border(if (primary) ChayeonDimensions.Hairline else ChayeonElevation.StrongBorder,
            if (primary) ChayeonColors.ButtonBorder else ChayeonColors.OutlineBorder, shape)
        .clickable(enabled = interactive, role = Role.Button, onClick = onClick)
        .padding(horizontal = ChayeonSpacing.Large, vertical = ChayeonSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { ChayeonIcon(it, Modifier.size(if (driving) ChayeonDimensions.Checkbox else ChayeonDimensions.StepDot)) }
        ChayeonText(text, style = if (driving) ChayeonType.DrivingButton else if (compact) ChayeonType.SceneNavigation else if (primary) ChayeonType.SceneButton else ChayeonType.SceneName)
    }
}

@Composable
fun InfoLine(text: String, modifier: Modifier = Modifier, icon: ChayeonIcon? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { ChayeonIcon(it, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Accent) }
        ChayeonText(text, style = ChayeonType.Label, color = ChayeonColors.InfoText)
    }
}

@Composable
fun TopicLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact), verticalAlignment = Alignment.CenterVertically) {
        ChayeonIcon(ChayeonIcon.CHECK, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Accent)
        ChayeonText(text, style = ChayeonType.SceneStatus)
    }
}

enum class BondStatus(val label: String) {
    INCOMING("애프터 요청이 왔어요"), CONNECTED("이어졌어요"), WAITING("답을 기다려요"),
}

@Composable
fun BondStatusBadge(status: BondStatus, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(when (status) {
        BondStatus.INCOMING -> ChayeonColors.BrightRed
        BondStatus.CONNECTED -> ChayeonColors.Red.copy(alpha = .2f)
        BondStatus.WAITING -> ChayeonColors.Transparent
    }).border(ChayeonDimensions.Hairline, if (status == BondStatus.CONNECTED) ChayeonColors.DeepRed else ChayeonColors.SceneBorder, CircleShape)
        .padding(horizontal = ChayeonSpacing.Gap, vertical = ChayeonSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
        if (status == BondStatus.CONNECTED) ChayeonIcon(ChayeonIcon.HEART, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.BrightRed)
        ChayeonText(status.label, style = ChayeonType.Minimum, color = if (status == BondStatus.CONNECTED) ChayeonColors.Pink else ChayeonColors.Text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListRow(avatar: MochiAvatar, name: String, subLine: String, modifier: Modifier = Modifier,
    status: BondStatus? = null, trailingAction: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(ChayeonRadii.Card)
    Row(modifier.fillMaxWidth().sceneGlass(ChayeonRadii.Card)
        .then(if (status == BondStatus.INCOMING) Modifier.border(ChayeonElevation.StrongBorder, ChayeonColors.Red, shape) else Modifier)
        .padding(ChayeonSpacing.AvatarGap), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
        AvatarDish(avatar, Modifier.size(ChayeonDimensions.SceneListAvatar), decorative = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                ChayeonText(name, style = ChayeonType.SceneName)
                status?.let { BondStatusBadge(it) }
            }
            ChayeonText(subLine, style = ChayeonType.Label, color = ChayeonColors.InfoText)
        }
        trailingAction()
    }
}
