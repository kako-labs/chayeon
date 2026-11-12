package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TraitDetailScreen(
    state: TraitDetailUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    SignatureFrame("특성 상세", state.driving.policy, chrome, onBack,
        primary = {
            SignatureColumn {
                HeroPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = ChayeonSpacing.Hero),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Screen)) {
                        StatusChip("나에게만 보이는 기록", variant = StatusVariant.PLAIN)
                        IconOrb(state.trait.presentation.icon, Modifier.size(ChayeonDimensions.Avatar), OrbVariant.GLASS)
                        GradientText(state.trait.presentation.name, style = ChayeonType.Title)
                        val evidence = state.evidence.readyValue()
                        if (evidence != null) {
                            ChayeonText("시그니처 근거: ${evidence.source.label}", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                            if (evidence.availability == TraitAvailability.UNSUPPORTED) {
                                ChayeonText(evidence.unsupportedLabel(LocalSourceLabels.current), color = ChayeonColors.Secondary)
                            } else TraitBadge(state.trait, evidence.level, size = TraitBadgeSize.DETAIL)
                            ChayeonText(evidence.levelExplanation(LocalSourceLabels.current), color = ChayeonColors.Secondary)
                        }
                        InfoRow(ChayeonIcon.LOCK, "평가가 아닌 나의 기록이에요",
                            body = "수치는 상대에게 보이지 않아요. 이 기록에 맞춰 운전할 필요는 없어요.")
                    }
                }
            }
        },
        secondary = {
            SignatureColumn {
                when (val loaded = state.evidence) {
                    is LoadState.Loading -> SignatureMessage("기록을 살펴보고 있어요", "읽은 신호와 최근 변화를 불러오고 있어요.")
                    is LoadState.Failed -> SignatureFailure(loaded.error, "특성 기록을 불러오지 못했어요", onRetry)
                    is LoadState.Ready -> {
                        val evidence = loaded.value
                        if (evidence == null) {
                            SignatureMessage("아직 기록이 없어요", "기록할 수 있는 신호가 모이면 여기에서 확인할 수 있어요.")
                        } else if (evidence.availability == TraitAvailability.UNSUPPORTED) {
                            SignatureMessage(evidence.unsupportedLabel(LocalSourceLabels.current), "이 특성에 필요한 신호를 읽을 수 없어요. 다른 특성의 기록은 계속 볼 수 있어요.", state.trait.presentation.icon)
                        } else {
                            GlassCard(Modifier.fillMaxWidth()) {
                                SectionTitle("기록한 기간", subtitle = evidencePeriod(evidence.window))
                                Spacer(Modifier.height(ChayeonSpacing.Medium))
                                ChayeonText("유효한 주행 ${evidence.validTripCount}회에서 모았어요.", style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                            }
                            GlassCard(Modifier.fillMaxWidth()) {
                                SectionTitle("읽은 신호")
                                Spacer(Modifier.height(ChayeonSpacing.Medium))
                                if (evidence.signals.isEmpty()) ChayeonText("읽은 신호가 아직 없어요.", style = ChayeonType.Secondary)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap),
                                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.TagGap)) {
                                    evidence.signals.sortedBy { it.ordinal }.forEach { InterestTag(it.noteLabel()) }
                                }
                            }
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    SectionTitle("나의 주행 메모", subtitle = "이 차 안에서만 보는 개인 기록이에요")
                                    if (evidence.metrics.isEmpty()) ChayeonText("메모할 기록을 모으고 있어요.", style = ChayeonType.Secondary)
                                    evidence.metrics.forEach { metric ->
                                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                            ChayeonText(metric.id.noteLabel(), style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                                            ChayeonText(metric.noteValue(), style = ChayeonType.Section)
                                        }
                                    }
                                }
                            }
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                    SectionTitle("최근 변화")
                                    if (evidence.recentChanges.isEmpty()) ChayeonText("아직 단계의 변화는 없어요. 모인 기록은 그대로 간직해요.", style = ChayeonType.Secondary)
                                    evidence.recentChanges.sortedByDescending { it.recordedAtEpochMillis }.forEach { change ->
                                        ChayeonText("${evidenceDate(change.recordedAtEpochMillis)} · ${change.previousLevel.label} → ${change.currentLevel.label}", style = ChayeonType.Secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
