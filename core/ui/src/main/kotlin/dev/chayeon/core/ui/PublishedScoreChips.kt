package dev.chayeon.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.PublicTrait
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TraitLevel

fun topPublishedScoredTraits(signature: PublicSignature?, limit: Int = 3): List<PublicTrait> =
    signature?.traits.orEmpty()
        .filter { it.trait != TraitId.LONG_ROAD && it.trait != TraitId.NIGHT_DRIVE &&
            it.level != TraitLevel.RECORDING && it.score in 60..100 }
        .distinctBy { it.trait }
        .sortedWith(compareByDescending<PublicTrait> { it.score }.thenBy { it.trait.ordinal })
        .take(limit.coerceIn(0, 3))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublishedScoreChips(signature: PublicSignature?, modifier: Modifier = Modifier, limit: Int = 3, large: Boolean = false) {
    val traits = topPublishedScoredTraits(signature, if (LocalDrivingPolicy.current == DrivingPolicy.ALLOWED) limit else minOf(limit, 2))
    if (traits.isEmpty()) return
    FlowRow(modifier.testTag("published-scores"),
        horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
        traits.forEach { trait ->
            ScoreChip(trait, Modifier.testTag("public-score-${trait.trait.name}"), large = large)
        }
    }
}
