package dev.chayeon.feature.signature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
            SignatureColumn {
                when (val loaded = state.signature) {
                    is LoadState.Loading -> SignatureMessage("주행의 이야기를 모으고 있어요", "매듭과 특성을 불러오고 있어요.")
                    is LoadState.Failed -> SignatureFailure(loaded.error, "시그니처를 불러오지 못했어요", onRetry)
                    is LoadState.Ready -> {
                        val signature = loaded.value
                        HeroPanel(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                ChayeonText("주행으로 쓰는 나의 한 문장", style = ChayeonType.Label, color = ChayeonColors.Pink)
                                SignatureKnot(signature.knotSeed, Modifier.size(ChayeonDimensions.HeroKnot + ChayeonDimensions.MiniKnot), reveal = false)
                                GradientText(signature.displayTitle, style = ChayeonType.Title)
                                SignatureSource(signature.displaySource)
                                JellyButton("공개 범위", onVisibility, Modifier.fillMaxWidth(), JellyVariant.WHITE)
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
                    TraitCategory.entries.forEach { category ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                SectionTitle(category.label, subtitle = if (category == TraitCategory.STYLE)
                                    "함께 달릴 때 드러나는 나예요" else "일상에서 차를 챙기는 마음이에요")
                                TraitId.entries.filter { it.presentation.category == category }.chunked(2).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Compact)) {
                                        row.forEach { trait ->
                                            TraitSummary(trait, signature.evidence.firstOrNull { it.trait == trait },
                                                { onTrait(trait) }, Modifier.weight(1f))
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
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
        evidence?.availability == TraitAvailability.UNSUPPORTED -> "이 차량에서 제공되지 않아요"
        evidence == null || evidence.level == TraitLevel.RECORDING -> "기록 중 · 나만 보여요"
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
