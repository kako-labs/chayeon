package dev.chayeon.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chayeon.core.model.AvatarId
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.InterestId

@Composable
fun GelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    maxLength: Int? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    maxLines: Int = Int.MAX_VALUE,
) {
    require(maxLength == null || maxLength > 0)
    val interactive = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(interactive, focused) {
        if (!interactive && focused) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
        ChayeonText(label, style = ChayeonType.Label)
        BasicTextField(
            value = value,
            onValueChange = { next ->
                if (interactive && (maxLength == null || next.codePointCount(0, next.length) <= maxLength)) onValueChange(next)
            },
            enabled = interactive,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            keyboardOptions = keyboardOptions,
            textStyle = ChayeonType.Body.copy(color = ChayeonColors.Text),
            cursorBrush = SolidColor(ChayeonColors.Pink),
            modifier = Modifier.fillMaxWidth().heightIn(min = ChayeonSpacing.Button)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                }
                .alpha(if (interactive) 1f else .48f)
                .clip(RoundedCornerShape(ChayeonRadii.Row))
                .background(Brush.verticalGradient(listOf(ChayeonColors.Black.copy(alpha = .55f), ChayeonColors.Ink)))
                .border(ChayeonElevation.Border,
                    if (error != null || focused) ChayeonColors.Pink else ChayeonColors.Border,
                    RoundedCornerShape(ChayeonRadii.Row)),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = ChayeonSpacing.BubbleHorizontal, vertical = ChayeonSpacing.AvatarGap), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) ChayeonText(placeholder, style = ChayeonType.Body, color = ChayeonColors.Secondary)
                    inner()
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
            ChayeonText(error.orEmpty(), Modifier.weight(1f), ChayeonType.Minimum, ChayeonColors.Pink)
            if (maxLength != null) ChayeonText("${value.codePointCount(0, value.length)} / $maxLength", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestPicker(selected: Set<InterestId>, maximumInterests: Int, onSelectionChange: (Set<InterestId>) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    require(maximumInterests >= 0)
    val allowed = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        SectionTitle("관심사", subtitle = "${selected.size}개 골랐어요 · 최대 ${maximumInterests}개")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
            InterestId.entries.forEach { interest ->
                val checked = interest in selected
                val interactive = allowed && (checked || selected.size < maximumInterests)
                Box(Modifier.heightIn(min = ChayeonSpacing.Touch)
                    .gelSurface(if (checked) JellyVariant.WHITE else JellyVariant.SECONDARY, ChayeonRadii.Tag)
                    .toggleable(checked, enabled = interactive, role = Role.Checkbox, onValueChange = { next ->
                        if (allowed && (!next || selected.size < maximumInterests)) {
                            onSelectionChange(if (next) selected + interest else selected - interest)
                        }
                    })
                    .padding(horizontal = ChayeonSpacing.AvatarGap, vertical = ChayeonSpacing.Medium), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                        if (checked) ChayeonIcon(ChayeonIcon.CHECK, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.DeepRed)
                        ChayeonText(interest.label, style = ChayeonType.Secondary, color = if (checked) ChayeonColors.DeepRed else ChayeonColors.Secondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(selected: AvatarId, onSelect: (AvatarId) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactive = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        SectionTitle("가상 아바타")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
            AvatarId.entries.forEach { avatar ->
                Column(Modifier.widthIn(max = ChayeonDimensions.Avatar)
                    .selectable(avatar == selected, enabled = interactive, role = Role.RadioButton) { onSelect(avatar) }
                    .semantics { contentDescription = avatar.label }
                    .padding(ChayeonSpacing.Small), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    Box {
                        AvatarDish(MochiAvatar.from(avatar), Modifier.size(ChayeonDimensions.PickerAvatar)
                            .border(ChayeonElevation.StrongBorder, if (avatar == selected) ChayeonColors.White else ChayeonColors.Transparent, CircleShape), decorative = true)
                        if (avatar == selected) Box(Modifier.align(Alignment.BottomEnd).size(ChayeonDimensions.BackIcon)
                            .background(ChayeonColors.White, CircleShape), contentAlignment = Alignment.Center) {
                            ChayeonIcon(ChayeonIcon.CHECK, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.DeepRed)
                        }
                    }
                    ChayeonText(avatar.label.removeSuffix(" 찹쌀떡"), style = ChayeonType.Minimum)
                }
            }
        }
    }
}

@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    allowExitWhenRestricted: Boolean = false,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = !busy)) {
        ConfirmSheetContent(title, body, confirmLabel, onConfirm, onDismiss, modifier, busy, allowExitWhenRestricted)
    }
}

@Composable
internal fun ConfirmSheetContent(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    allowExitWhenRestricted: Boolean = false,
) {
    Box(modifier.widthIn(max = ChayeonDimensions.ConfirmWidth).padding(ChayeonSpacing.Large)
        .semantics { paneTitle = title }) {
        HeroPanel {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                IconOrb(ChayeonIcon.WARNING, variant = OrbVariant.WHITE)
                SectionTitle(title, subtitle = body)
                JellyButton(if (busy) "처리하고 있어요" else confirmLabel, onConfirm, Modifier.fillMaxWidth(), JellyVariant.DANGER,
                    enabled = !busy, allowWhenRestricted = allowExitWhenRestricted)
                JellyButton("돌아가기", onDismiss, Modifier.fillMaxWidth(), JellyVariant.SECONDARY,
                    enabled = !busy, allowWhenRestricted = true)
            }
        }
    }
}
