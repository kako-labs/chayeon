package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chayeon.core.ui.*

@Composable
internal fun SignatureButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: JellyVariant = JellyVariant.PRIMARY, enabled: Boolean = true, allowWhenRestricted: Boolean = false) {
    val access = if (allowWhenRestricted) ButtonAccess.SAFE_EXIT else ButtonAccess.PARKED_ONLY
    if (variant == JellyVariant.PRIMARY || variant == JellyVariant.WHITE) {
        PrimaryButton(text, onClick, modifier, enabled = enabled, access = access, compact = true)
    } else OutlineButton(text, onClick, modifier, enabled = enabled, access = access, compact = true)
}

@Composable
internal fun DrivingModeCard(title: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean, body: String) {
    GlassPanel(Modifier.fillMaxWidth()
        .border(if (selected) ChayeonElevation.StrongBorder else ChayeonDimensions.Hairline,
            if (selected) ChayeonColors.BrightRed else ChayeonColors.SceneBorder,
            RoundedCornerShape(ChayeonDimensions.ScenePanelRadius))
        .selectable(selected, enabled = enabled && LocalDrivingPolicy.current == dev.chayeon.core.model.DrivingPolicy.ALLOWED,
            role = Role.RadioButton, onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.AvatarGap)) {
            Box(Modifier.size(ChayeonDimensions.OrbIcon).border(ChayeonElevation.StrongBorder,
                if (selected) ChayeonColors.BrightRed else ChayeonColors.OutlineBorder, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(ChayeonSpacing.Medium).background(ChayeonColors.BrightRed, CircleShape))
            }
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                ChayeonText(title, style = ChayeonType.SceneName)
                ChayeonText(body, style = ChayeonType.Secondary, color = ChayeonColors.InfoText)
            }
        }
    }
}
