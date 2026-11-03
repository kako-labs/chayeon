package dev.chayeon.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.chayeon.core.model.Candidate
import dev.chayeon.core.model.Chemistry
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.KnotSeed
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TraitLevel

enum class TraitBadgeSize { CARD, DETAIL }

@Composable
fun TraitBadge(trait: TraitId, level: TraitLevel, modifier: Modifier = Modifier, size: TraitBadgeSize = TraitBadgeSize.CARD) {
    val presentation = trait.presentation
    Row(modifier.gelSurface(JellyVariant.SECONDARY, ChayeonRadii.Row)
        .clearAndSetSemantics { contentDescription = "${presentation.name}, ${level.label}" }
        .padding(horizontal = ChayeonSpacing.ChipHorizontal, vertical = ChayeonSpacing.Compact),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        ChayeonIcon(presentation.icon, Modifier.size(if (size == TraitBadgeSize.CARD) ChayeonDimensions.SmallIcon else ChayeonDimensions.OrbIcon), ChayeonColors.Pink)
        Column {
            ChayeonText(presentation.name, style = if (size == TraitBadgeSize.CARD) ChayeonType.Label else ChayeonType.Body)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                TraitLevelMark(level)
                ChayeonText(level.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
private fun TraitLevelMark(level: TraitLevel) {
    Canvas(Modifier.size(ChayeonDimensions.SmallIcon)) {
        val color = ChayeonColors.Pink
        val stroke = Stroke(ChayeonElevation.StrongBorder.toPx())
        when (level) {
            TraitLevel.RECORDING -> drawArc(color, -90f, 260f, false, style = stroke)
            TraitLevel.VISIBLE -> drawCircle(color, size.minDimension * .28f, style = stroke)
            TraitLevel.STEADY -> {
                drawCircle(color, size.minDimension * .38f, style = stroke)
                drawCircle(color, size.minDimension * .15f)
            }
            TraitLevel.SIGNATURE -> {
                val diamond = Path().apply {
                    moveTo(center.x, 0f); lineTo(size.width, center.y)
                    lineTo(center.x, size.height); lineTo(0f, center.y); close()
                }
                drawPath(diamond, color, style = stroke)
                drawCircle(color, size.minDimension * .12f, Offset(center.x, center.y))
            }
        }
    }
}

@Composable
fun SignatureHero(signature: PublicSignature, modifier: Modifier = Modifier) {
    SignatureHero(signature.knotSeed, signature.title, signature.source, modifier)
}

@Composable
fun SignatureHero(seed: KnotSeed, title: String, source: DataSource, modifier: Modifier = Modifier) {
    HeroPanel(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            StatusChip(source.label, variant = StatusVariant.SYNTHETIC)
            SignatureKnot(seed, Modifier.size(ChayeonDimensions.HeroKnot), contentDescription = "$title, 붉은 시그니처 매듭")
            GradientText(title, style = ChayeonType.Title)
        }
    }
}

@Composable
fun SignatureMini(signature: PublicSignature, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
        SignatureKnot(signature.knotSeed, Modifier.size(ChayeonDimensions.MiniKnot), reveal = false,
            contentDescription = "${signature.title}, 붉은 시그니처 매듭")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
            ChayeonText(signature.title, style = ChayeonType.Body)
            ChayeonText(signature.source.label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
        }
    }
}

@Composable
fun ChemistryCard(chemistry: Chemistry, first: PublicSignature, second: PublicSignature, modifier: Modifier = Modifier) {
    ChemistryCard(chemistry, first.knotSeed, second.knotSeed, first.source, second.source, modifier)
}

@Composable
fun ChemistryCard(
    chemistry: Chemistry,
    firstSeed: KnotSeed,
    secondSeed: KnotSeed,
    firstSource: DataSource,
    secondSource: DataSource,
    modifier: Modifier = Modifier,
) {
    if (chemistry.lines.isEmpty()) return
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            SectionTitle("함께하면 잘 맞는 점")
            StatusChip(if (firstSource == secondSource) firstSource.label else "${firstSource.label} · ${secondSource.label}", variant = StatusVariant.SYNTHETIC)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SignatureKnot(firstSeed, interlockedWith = secondSeed)
            }
            chemistry.lines.forEach { line ->
                InfoRow(ChayeonIcon.KNOT, line.text)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CandidateCard(candidate: Candidate, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, actionLabel: String = "프로필 보기") {
    GlassCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            PeerHeader(candidate.profile, size = PeerHeaderSize.SMALL)
            val signature = candidate.profile.signature
            if (signature != null) {
                SignatureMini(signature)
                signature.traits.maxByOrNull { it.level.ordinal }?.let { TraitBadge(it.trait, it.level) }
            } else {
                ChayeonText("시그니처를 만드는 중이에요", style = ChayeonType.Body, color = ChayeonColors.Secondary)
            }
            if (candidate.chemistry.lines.isNotEmpty()) {
                ChayeonText("잘 맞는 점 ${candidate.chemistry.lines.size}가지", style = ChayeonType.Label, color = ChayeonColors.Pink)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                candidate.profile.interests.sortedBy { it.ordinal }.forEach { interest ->
                    InterestTag(interest.label, matched = interest in candidate.sharedInterests)
                }
            }
            JellyButton(actionLabel, onClick, Modifier.fillMaxWidth(), enabled = enabled)
        }
    }
}
