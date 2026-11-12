package dev.chayeon.feature.signature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun MySignatureScreen(
    state: SignatureUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onVisibility: () -> Unit,
    onTrait: (TraitId) -> Unit,
    onDemo: () -> Unit,
    onRetry: () -> Unit,
) {
    SignatureFrame("내 시그니처", state.driving.policy, chrome, onBack,
        primary = {
            when (val loaded = state.signature) {
                is LoadState.Loading -> SignatureMessage("주행의 이야기를 모으고 있어요", "매듭과 특성을 불러오고 있어요.")
                is LoadState.Failed -> SignatureFailure(loaded.error, "시그니처를 불러오지 못했어요", onRetry)
                is LoadState.Ready -> {
                    val signature = loaded.value
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val bounded = constraints.hasBoundedHeight
                        val knotSize = if (bounded) minOf(ChayeonDimensions.ChemistryKnot, maxHeight * .55f) else ChayeonDimensions.ChemistryKnot
                        HeroPanel(Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier),
                            contentPadding = PaddingValues(ChayeonSpacing.Screen)) {
                            val scroll = rememberScrollState()
                            Column(Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight().scrollEdgeFade(scroll).verticalScroll(scroll) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large, Alignment.CenterVertically)) {
                                ChayeonText("주행으로 쓰는 나의 한 문장", style = ChayeonType.Label, color = ChayeonColors.Pink)
                                SignatureKnot(signature.knotSeed, Modifier.size(knotSize), reveal = false)
                                GradientText(signature.displayTitle, style = ChayeonType.Title)
                                SignatureSource(signature.displaySource)
                                JellyButton("공개 범위", onVisibility, Modifier.fillMaxWidth(), JellyVariant.SECONDARY)
                            }
                        }
                    }
                }
            }
        },
        secondary = {
            SignatureColumn {
                val signature = state.signature.readyValue()
                if (signature != null) {
                    if (signature.isRecording) SignatureRecording(state.consoleAvailable, onDemo)
                    val earned = signature.evidence.filter {
                        it.availability == TraitAvailability.AVAILABLE && it.level != TraitLevel.RECORDING
                    }.sortedWith(compareByDescending<TraitEvidence> { it.level.ordinal }.thenBy { it.trait.ordinal })
                    if (earned.isNotEmpty()) {
                        SectionTitle("함께 타면 알게 되는 나", subtitle = "주행과 돌봄에서 모인 나의 매력이에요")
                        earned.filter { it.level == TraitLevel.SIGNATURE }.forEach { evidence ->
                            EarnedTrait(evidence, { onTrait(evidence.trait) }, Modifier.fillMaxWidth())
                        }
                        earned.filter { it.level != TraitLevel.SIGNATURE }.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Gap)) {
                                row.forEach { evidence -> EarnedTrait(evidence, { onTrait(evidence.trait) }, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    val remaining = TraitId.entries.filter { trait -> earned.none { it.trait == trait } }
                    if (remaining.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(ChayeonSpacing.Medium)) {
                            SettingsRow(ChayeonIcon.KNOT, "아직 모으는 이야기", "기록 중·미지원 특성 ${remaining.size}개 · 나만 보여요", { expanded = !expanded })
                            if (expanded) remaining.forEach { trait ->
                                TraitSummary(trait, signature.evidence.firstOrNull { it.trait == trait }, { onTrait(trait) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    InfoRow(ChayeonIcon.LOCK, "내 기록은 나에게만 보여요",
                        body = "상대에게는 내가 고른 특성만 보여요. 수치와 차량 정보는 공유하지 않아요.")
                }
            }
        },
    )
}

@Composable
private fun TraitSummary(trait: TraitId, evidence: TraitEvidence?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = when {
        evidence?.availability == TraitAvailability.UNSUPPORTED -> evidence.unsupportedLabel(LocalSourceLabels.current)
        evidence == null || evidence.level == TraitLevel.RECORDING -> "기록 중"
        else -> evidence.level.label
    }
    GlassCard(modifier.heightIn(min = ChayeonSpacing.Touch).clickable(
        enabled = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED, role = Role.Button, onClick = onClick,
    ).semantics { contentDescription = "${trait.presentation.name}, $status, 근거 보기" },
        contentPadding = PaddingValues(ChayeonSpacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
            ChayeonIcon(trait.presentation.icon, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Pink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Tiny)) {
                ChayeonText(trait.presentation.name, style = ChayeonType.Label)
                ChayeonText(status, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
            ChayeonIcon(ChayeonIcon.FORWARD, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Secondary)
        }
    }
}

@Composable
private fun EarnedTrait(evidence: TraitEvidence, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pearl = evidence.level == TraitLevel.SIGNATURE
    val shape = RoundedCornerShape(ChayeonRadii.Card)
    Column(modifier.testTag("earned-${evidence.trait.name}").heightIn(min = ChayeonSpacing.Touch)
        .shadow(ChayeonElevation.Button, shape).clip(shape)
        .background(Brush.verticalGradient(if (pearl) listOf(ChayeonColors.White, ChayeonColors.WhiteBottom)
            else listOf(ChayeonColors.DishTop, ChayeonColors.DishMiddle, ChayeonColors.DishBottom)))
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(ChayeonColors.White.copy(alpha = .14f), ChayeonColors.Transparent), endY = size.height * .5f))
        }
        .border(ChayeonElevation.Border, ChayeonColors.ButtonBorder, shape)
        .clickable(enabled = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED, role = Role.Button, onClick = onClick)
        .padding(ChayeonSpacing.Large), verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
        val ink = if (pearl) ChayeonColors.DeepRed else ChayeonColors.Pink
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
            ChayeonIcon(evidence.trait.presentation.icon, Modifier.size(ChayeonDimensions.UtilityIcon), ink)
            ChayeonText(evidence.trait.presentation.name, Modifier.weight(1f), ChayeonType.Label, ink)
            ChayeonIcon(ChayeonIcon.FORWARD, Modifier.size(ChayeonDimensions.SmallIcon), ink)
        }
        ChayeonText(evidence.trait.presentation.publicPhrase, style = if (pearl) ChayeonType.Section else ChayeonType.Body,
            color = if (pearl) ChayeonColors.Ink else ChayeonColors.White)
        ChayeonText(evidence.level.label, style = ChayeonType.Minimum, color = ink)
    }
}
