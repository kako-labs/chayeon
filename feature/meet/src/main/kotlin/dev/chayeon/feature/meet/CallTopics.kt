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
internal fun MeetCallTopics(state: ConnectedUiState, onSafety: () -> Unit) {
    val topics = state.callTopics()
    if (topics.isEmpty) return
    GlassCard(Modifier.fillMaxWidth().testTag("call-topics")) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                SectionTitle("이야깃거리", Modifier.weight(1f))
                JellyButton("차단·신고", onSafety, variant = JellyVariant.SECONDARY)
            }
            if (topics.sharedInterests.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                    topics.sharedInterests.sortedBy { it.ordinal }.forEach { InterestTag(it.label, matched = true) }
                }
            }
            topics.chemistryLines.forEach { ChayeonText(it, style = ChayeonType.Body, color = ChayeonColors.Secondary) }
            if (topics.ownTitle != null || topics.peerTitle != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                    topics.ownTitle?.let { ChayeonText("나 · $it", style = ChayeonType.Secondary, color = ChayeonColors.Pink) }
                    topics.peerTitle?.let { ChayeonText("${state.connection?.peer?.nickname.orEmpty()} · $it", style = ChayeonType.Secondary, color = ChayeonColors.Pink) }
                }
            }
        }
    }
}
