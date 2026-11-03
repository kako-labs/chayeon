package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun TripRecapScreen(
    state: TripRecapUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onExclude: () -> Unit,
    onSignature: () -> Unit,
    onRetry: () -> Unit,
    onVisibility: () -> Unit = onSignature,
) {
    val allowed = state.driving.policy == DrivingPolicy.ALLOWED && LocalDrivingPolicy.current == DrivingPolicy.ALLOWED
    val current = state.recap.readyValue()?.takeIf { it.firstSignature && it.included }
    var presentedMoment by remember { mutableStateOf<TripRecap?>(null) }
    SideEffect { if (allowed) presentedMoment = current }
    // Keep the already presented milestone still when policy redacts the repository observation.
    val first = current ?: presentedMoment.takeIf { !allowed }
    if (first != null) {
        FirstSignatureMoment(state, first, chrome, onExclude, onSignature, onVisibility, onRetry)
        return
    }
    SignatureFrame("주행 후 요약", state.driving.policy, chrome, onBack, recap = true,
        backEnabled = !state.excluding && !state.markingSeen && !state.pendingChanges,
        primary = {
            SignatureColumn {
                when (val loaded = state.recap) {
                    is LoadState.Loading -> SignatureMessage("이번 주행을 담고 있어요", "주차한 지금, 매듭에 남은 이야기를 준비하고 있어요.")
                    is LoadState.Failed -> SignatureFailure(loaded.error, "주행 요약을 불러오지 못했어요", onRetry)
                    is LoadState.Ready -> {
                        val recap = loaded.value
                        if (recap == null) {
                            SignatureMessage("아직 마친 주행이 없어요", "주행을 마치고 주차하면 이곳에서 함께 돌아봐요.",
                                action = "내 시그니처 보기", onAction = onSignature)
                        } else {
                            HeroPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                                    SignatureSource(recap.source)
                                    key(recap.id, recap.included) {
                                        SignatureKnot(recap.currentKnot, Modifier.size(ChayeonDimensions.ChemistryKnot).testTag("recap-knot"),
                                            reveal = recap.included && recap.previousKnot != recap.currentKnot,
                                            contentDescription = if (recap.included) "이번 주행을 담은 나의 붉은 매듭" else "주행을 제외한 나의 붉은 매듭")
                                    }
                                    GradientText(if (!recap.included) "이 주행은 빼 두었어요"
                                        else if (recap.source == DataSource.SYNTHETIC) "이 주행이 매듭이 됐어요" else "내 주행이 매듭이 됐어요", style = ChayeonType.Hero)
                                    ChayeonText(if (!recap.included) "나머지 주행으로 시그니처를 다시 살펴봐요."
                                        else "주차한 지금, 오늘의 주행을 돌아봐요.", color = ChayeonColors.Secondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        secondary = {
            SignatureColumn {
                val recap = state.recap.readyValue()
                if (recap != null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Large)) {
                            SectionTitle(if (recap.included) "이번 주행이 남긴 이야기" else "내 기록에서 제외했어요")
                            when {
                                !recap.included -> InfoRow(ChayeonIcon.CHECK, "내가 운전한 주행만 남겨요",
                                    body = "이 주행은 시그니처를 계산할 때 사용하지 않아요.")
                                recap.changes.isEmpty() -> InfoRow(ChayeonIcon.KNOT, "특성의 단계는 그대로예요",
                                    body = "새로운 변화가 없어도 괜찮아요. 이번 주행도 매듭에 더했어요.")
                                else -> recap.changes.forEach { change ->
                                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                        TraitBadge(change.trait, change.currentLevel, size = TraitBadgeSize.DETAIL)
                                        ChayeonText(change.explanation.ifBlank { "이번 주행을 반영해 ${change.trait.presentation.name} 기록을 살펴봤어요." },
                                            color = ChayeonColors.Secondary)
                                    }
                                }
                            }
                        }
                    }
                    state.error?.let { SignatureFailure(it, "주행 요약을 반영하지 못했어요", onRetry) }
                    if (state.pendingChanges && state.error == null) {
                        ChayeonText("공개 내용도 함께 반영하고 있어요.", style = ChayeonType.Secondary,
                            color = ChayeonColors.Secondary)
                    }
                    JellyButton(if (state.markingSeen) "확인하고 있어요" else "내 시그니처 보기", onSignature,
                        Modifier.fillMaxWidth().testTag("recap-open-signature"), enabled = !state.markingSeen && !state.excluding && !state.pendingChanges)
                    if (recap.included && recap.source == DataSource.VEHICLE) {
                        JellyButton(if (state.excluding) "주행을 빼고 있어요" else "내가 운전하지 않았어요", onExclude,
                            Modifier.fillMaxWidth().testTag("exclude-trip"), JellyVariant.SECONDARY,
                            enabled = !state.excluding && !state.markingSeen)
                        ChayeonText("다른 사람이 운전했다면 내 시그니처에서 뺄 수 있어요.",
                            style = ChayeonType.Secondary, color = ChayeonColors.Secondary)
                    }
                    InfoRow(ChayeonIcon.LOCK, "이 요약은 나에게만 보여요", body = "상대에게는 공개 범위에서 고른 특성만 보여요.")
                }
            }
        },
    )
}
