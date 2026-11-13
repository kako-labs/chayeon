package dev.chayeon.feature.meet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.ui.*

@Composable
internal fun MeetVoiceControl(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    driving: Boolean,
    endCall: Boolean,
) {
    val interactive = enabled && (endCall || LocalDrivingPolicy.current != DrivingPolicy.UNKNOWN)
    val shape = RoundedCornerShape(if (driving) ChayeonDimensions.SceneShadow else ChayeonDimensions.ScenePanelRadius)
    val glow = if (endCall && !driving) Modifier.shadow(ChayeonDimensions.SceneShadow, shape, clip = false,
        ambientColor = ChayeonColors.Glow, spotColor = ChayeonColors.Glow) else Modifier
    Row(Modifier.fillMaxWidth()
        .heightIn(min = if (driving) ChayeonDimensions.DrivingControl else ChayeonDimensions.ScenePrimary + ChayeonSpacing.Screen)
        .alpha(if (interactive) 1f else .48f).then(glow).clip(shape)
        .background(if (endCall) Brush.verticalGradient(0f to ChayeonColors.BrightRed, .55f to ChayeonColors.Red, 1f to ChayeonColors.DeepRed)
            else Brush.verticalGradient(listOf(ChayeonColors.SceneGlass, ChayeonColors.SceneGlass)))
        .border(if (endCall) ChayeonDimensions.Hairline else ChayeonElevation.StrongBorder,
            if (endCall) ChayeonColors.ButtonBorder else ChayeonColors.OutlineBorder, shape)
        .clickable(enabled = interactive, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (endCall) "통화 끊기" else text }
        .padding(ChayeonSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeetVoiceIcon(endCall, Modifier.size(if (driving) ChayeonSpacing.Hero else ChayeonDimensions.UtilityIcon))
        ChayeonText(text, style = if (driving) ChayeonType.DrivingButton else ChayeonType.SceneButton)
    }
}

@Composable
private fun MeetVoiceIcon(endCall: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round)
            if (endCall) {
                val handset = Path().apply {
                    moveTo(3f, 15f)
                    lineTo(1.5f, 11f)
                    cubicTo(7f, 6f, 17f, 6f, 22.5f, 11f)
                    lineTo(21f, 15f)
                    lineTo(16f, 13f)
                    lineTo(16f, 10f)
                    cubicTo(13f, 9f, 11f, 9f, 8f, 10f)
                    lineTo(8f, 13f)
                    close()
                }
                drawPath(handset, ChayeonColors.Text, style = stroke)
            } else {
                drawRoundRect(ChayeonColors.Text, Offset(8f, 1.5f), Size(8f, 13f), CornerRadius(4f), style = stroke)
                val cradle = Path().apply {
                    moveTo(4f, 10f)
                    cubicTo(4f, 22f, 20f, 22f, 20f, 10f)
                }
                drawPath(cradle, ChayeonColors.Text, style = stroke)
                drawLine(ChayeonColors.Text, Offset(12f, 19f), Offset(12f, 23f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
    }
}
