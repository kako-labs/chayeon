package dev.chayeon.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chayeon.core.ui.*

@Composable
internal fun HistoryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: JellyVariant = JellyVariant.PRIMARY, enabled: Boolean = true, allowWhenRestricted: Boolean = false) {
    val access = if (allowWhenRestricted) ButtonAccess.SAFE_EXIT else ButtonAccess.PARKED_ONLY
    if (variant == JellyVariant.PRIMARY || variant == JellyVariant.WHITE) {
        PrimaryButton(text, onClick, modifier, enabled = enabled, access = access, compact = true)
    } else OutlineButton(text, onClick, modifier, enabled = enabled, access = access, compact = true)
}

@Composable
internal fun ProfileSteps(step: Int) {
    StepIndicator(if (step >= 2) 1 else 0, steps = listOf("프로필", "공개 범위"))
}

@Composable
internal fun HistorySceneHero(title: String, body: String, pose: MascotPose,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
        HeroHeadline(title, body, style = ChayeonType.SceneTitle)
        val narrow = LocalWindowInfo.current.containerSize.width < with(LocalDensity.current) { ChayeonSpacing.NarrowBreakpoint.roundToPx() }
        Mascot(pose, Modifier.fillMaxWidth().height(if (narrow) ChayeonDimensions.HeroKnot else ChayeonDimensions.SceneMascot))
    }
}

@Composable
internal fun ProfileAvatarPicker(selected: AvatarId, onSelect: (AvatarId) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        ChayeonText("캐릭터", style = ChayeonType.SceneStatus)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            AvatarId.entries.forEach { avatar ->
                Column(Modifier.width(ChayeonDimensions.ToggleWidth)
                    .selectable(selected == avatar, enabled = enabled && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED,
                        role = Role.RadioButton) { onSelect(avatar) }
                    .semantics { contentDescription = avatar.label }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarDish(MochiAvatar.from(avatar), Modifier.size(ChayeonDimensions.PickerAvatar)
                        .border(ChayeonElevation.StrongBorder, if (avatar == selected) ChayeonColors.BrightRed else ChayeonColors.SceneBorder, CircleShape), decorative = true)
                    ChayeonText(avatar.label.removeSuffix(" 찹쌀떡"), style = ChayeonType.Minimum)
                }
            }
        }
    }
}

@Composable
internal fun SharedContactCard(peer: PublicProfile, relationship: AfterConnection) {
    GlassPanel(Modifier.fillMaxWidth()) {
        val contacts = relationship.sharedContacts
        if (contacts == null) ChayeonText("공유한 아이디를 확인할 수 없어요", style = ChayeonType.Secondary)
        else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labels: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    contacts.kakaoTalkId?.let { id ->
                        ChayeonText("카카오톡 아이디", style = ChayeonType.SceneStatus, color = ChayeonColors.InfoText)
                        ChayeonText(id, style = ChayeonType.Title)
                    }
                    contacts.instagramId?.let { id ->
                        ChayeonText("${peer.nickname}님의 인스타그램", style = ChayeonType.SceneStatus, color = ChayeonColors.InfoText)
                        ChayeonText("@$id", style = ChayeonType.SceneButton)
                        InfoLine("휴대폰으로 QR을 찍으면 바로 열려요")
                    }
                }
            }
            if (maxWidth >= ChayeonDimensions.ConfirmWidth) Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { labels() }
                contacts.instagramId?.let { InstagramContactQr(it) }
            } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                labels()
                contacts.instagramId?.let { InstagramContactQr(it) }
            }
        }
        relationship.ownSharedContacts?.let { own ->
            Spacer(Modifier.height(ChayeonSpacing.Large))
            ChayeonText("내가 공유한 아이디", style = ChayeonType.Label, color = ChayeonColors.InfoText)
            own.kakaoTalkId?.let { ChayeonText("카카오톡 · $it", style = ChayeonType.Secondary) }
            own.instagramId?.let { ChayeonText("인스타그램 · @$it", style = ChayeonType.Secondary) }
        }
    }
}
