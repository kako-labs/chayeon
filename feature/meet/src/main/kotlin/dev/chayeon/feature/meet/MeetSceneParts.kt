package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal val meetScenePadding = PaddingValues(
    start = ChayeonDimensions.SceneScreenHorizontal,
    end = ChayeonDimensions.SceneScreenHorizontal,
    top = ChayeonDimensions.SceneScreenTop,
    bottom = ChayeonDimensions.SceneScreenBottom,
)

@Composable
internal fun MeetSceneSplit(
    chrome: ScreenChrome,
    pose: MascotPose,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints {
        val wide = maxWidth >= ChayeonSpacing.NarrowBreakpoint
        ScreenScaffold(
            modifier = Modifier.testTag("meet-screen"), showSourceLabels = chrome.showSourceLabels,
            scene = Scene.PARKING, sceneScrim = SceneScrim.RIGHT, contentPadding = meetScenePadding,
            topBar = { Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { BackButton(it) }
                Box(Modifier.weight(1f)) { chrome.topBar() }
            } },
            bottomBar = { if (!wide) actions() },
            primary = {
                if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    Box(Modifier.weight(.44f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Mascot(pose, Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                    Column(Modifier.weight(.56f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                        Box(Modifier.weight(1f)) { MeetPane(content = content) }
                        actions()
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                    Mascot(pose, Modifier.size(ChayeonDimensions.PeerLarge).align(Alignment.CenterHorizontally))
                    content()
                }
            },
        )
    }
}

@Composable
internal fun MeetSourceLabel(peer: PublicProfile) {
    if (LocalSourceLabels.current) InfoLine(peer.kind.label + (peer.signature?.let { " · ${it.source.label}" } ?: ""))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetProfilePanel(peer: PublicProfile, chemistry: Chemistry, shared: Set<InterestId> = emptySet()) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                AvatarDish(MochiAvatar.from(peer.avatar), Modifier.size(ChayeonDimensions.SceneListAvatar + ChayeonDimensions.ScenePanelPadding), decorative = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    ChayeonText(peer.nickname, style = ChayeonType.SceneButton)
                    ChayeonText(peer.signature?.title ?: "시그니처를 만드는 중이에요", style = ChayeonType.SceneStatus, color = ChayeonColors.Secondary)
                    MeetSourceLabel(peer)
                }
            }
            PublishedScoreChips(peer.signature)
            if (chemistry.lines.isNotEmpty()) {
                InfoLine("함께하면 잘 맞는 점")
                chemistry.lines.forEach { TopicLine(it.text) }
            }
            if (peer.interests.isNotEmpty()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
            ) { peer.interests.sortedBy { it.ordinal }.forEach { InterestTag(it.label, matched = it in shared) } }
        }
    }
}
