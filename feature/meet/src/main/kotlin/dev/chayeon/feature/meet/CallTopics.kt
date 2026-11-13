package dev.chayeon.feature.meet

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

internal data class CallTopics(
    val sharedInterests: Set<InterestId>,
    val chemistryLines: List<String>,
    val ownTitle: String?,
    val peerTitle: String?,
) {
    val isEmpty: Boolean get() = sharedInterests.isEmpty() && chemistryLines.isEmpty() && ownTitle == null && peerTitle == null
}

internal fun ConnectedUiState.callTopics(): CallTopics {
    val peer = connection?.peer
    return CallTopics(
        sharedInterests = ownProfile?.interests.orEmpty().intersect(peer?.interests.orEmpty()),
        chemistryLines = connection?.chemistry?.lines.orEmpty().map { it.text.trim() }.filter { it.isNotEmpty() }.distinct(),
        ownTitle = ownProfile?.signature?.title?.trim()?.takeIf { it.isNotEmpty() },
        peerTitle = peer?.signature?.title?.trim()?.takeIf { it.isNotEmpty() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetCallTopics(state: ConnectedUiState) {
    val topics = state.callTopics()
    if (topics.isEmpty) return
    Column(Modifier.fillMaxWidth().testTag("call-topics"), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small), itemVerticalAlignment = Alignment.CenterVertically) {
            InfoLine("이야깃거리")
            topics.chemistryLines.forEach { ChayeonText(it, style = ChayeonType.SceneStatus) }
            topics.sharedInterests.sortedBy { it.ordinal }.forEach { InterestTag(it.label, matched = true) }
        }
        if (topics.ownTitle != null || topics.peerTitle != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                topics.ownTitle?.let { ChayeonText("나 · $it", style = ChayeonType.Secondary, color = ChayeonColors.Pink) }
                topics.peerTitle?.let { ChayeonText("${state.connection?.peer?.nickname.orEmpty()} · $it", style = ChayeonType.Secondary, color = ChayeonColors.Pink) }
            }
        }
    }
}
