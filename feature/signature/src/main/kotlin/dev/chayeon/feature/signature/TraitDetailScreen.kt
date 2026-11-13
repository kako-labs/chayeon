package dev.chayeon.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*

@Composable
fun TraitDetailScreen(
    state: TraitDetailUiState,
    chrome: ScreenChrome,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVisible: (Boolean) -> Unit = {},
) {
    val evidence = state.evidence.readyValue()
    val score = evidence?.score.takeIf { state.trait.isScored && evidence != null && evidence.validTripCount >= 3 && evidence.availability != TraitAvailability.UNSUPPORTED }
    SignatureFrame(state.trait.presentation.name, state.driving.policy, chrome, onBack, recap = true, ratio = .48f,
        primary = {
            SignatureColumn {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                        ChayeonText(if (state.visible) "상대에게 보이는 점수" else "나에게만 보이는 기록", style = ChayeonType.Label, color = ChayeonColors.Pink)
                        if (evidence != null) {
                            ChayeonText("시그니처 근거: ${evidence.source.label}", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                            when {
                                evidence.availability == TraitAvailability.UNSUPPORTED -> ChayeonText("이 차량에서 제공되지 않아요", color = ChayeonColors.Secondary)
                                score != null -> {
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                        ChayeonText("$score", style = ChayeonType.Headline)
                                        ChayeonText("/ 100", style = ChayeonType.Section, color = ChayeonColors.Secondary)
                                    }
                                    ScoreStageLadder(score)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        ChayeonText("60 보여요", style = ChayeonType.Minimum)
                                        ChayeonText("75 꾸준해요", style = ChayeonType.Minimum)
                                        ChayeonText("90 시그니처", style = ChayeonType.Minimum)
                                    }
                                    ChayeonText(scoreStageHint(score), style = ChayeonType.Secondary)
                                }
                                !state.trait.isScored -> ChayeonText("취향은 점수 없이 보여요", style = ChayeonType.Section)
                                else -> ChayeonText("3번의 주행을 모으면 점수가 생겨요", style = ChayeonType.Section)
                            }
                            GelToggle(state.visible, onVisible, "상대에게 공개", enabled = evidence.isPublishable && !state.updatingVisibility)
                            if (state.visibilityError != null) ChayeonText("공개 설정을 반영하지 못했어요. 다시 시도해 주세요.", style = ChayeonType.Minimum, color = ChayeonColors.Pink)
                        }
                    }
                }
                if (evidence != null && evidence.availability != TraitAvailability.UNSUPPORTED) {
                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                CountTile(state.trait.countLabels.supported, "${evidence.sampleCount}번", Modifier.weight(1f))
                                CountTile(state.trait.countLabels.passing, "${evidence.passingSampleCount}번", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                CountTile("반영한 주행", "${evidence.validTripCount}회", Modifier.weight(1f))
                                CountTile("기간", "최근 30일", Modifier.weight(1f))
                            }
                            ChayeonText(evidencePeriod(evidence.window), style = ChayeonType.Minimum, color = ChayeonColors.InfoText)
                    }
                }
                InfoRow(ChayeonIcon.LOCK, "주행 기록은 차 안에만 남아요",
                    body = "원래 신호는 요약한 뒤 지워요. 상대에게는 공개한 특성의 점수만 가요. 이 기록에 맞춰 운전할 필요는 없어요.")
            }
        },
        secondary = {
            SignatureColumn {
                when (val loaded = state.evidence) {
                    is LoadState.Loading -> SignatureMessage("기록을 살펴보고 있어요", "최근 주행의 근거를 불러오고 있어요.")
                    is LoadState.Failed -> SignatureFailure(loaded.error, "특성 기록을 불러오지 못했어요", onRetry)
                    is LoadState.Ready -> {
                        val detail = loaded.value
                        when {
                            detail == null -> SignatureMessage("아직 기록이 없어요", "신호가 모이면 여기에서 확인할 수 있어요.")
                            detail.availability == TraitAvailability.UNSUPPORTED -> SignatureMessage("이 차량에서 제공되지 않아요", "이 특성에 필요한 신호를 읽을 수 없어요. 다른 특성의 기록은 계속 볼 수 있어요.")
                            else -> {
                                if (score != null) TraitTripChart(detail)
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                                        SectionTitle("최근 주행")
                                        TripTableRow("언제", "시간", state.trait.countLabels.supported, state.trait.countLabels.passing, "반영")
                                        if (detail.recentTrips.isEmpty()) ChayeonText("주행 기록을 모으고 있어요.", style = ChayeonType.Secondary)
                                        detail.recentTrips.take(12).forEach { trip ->
                                            val unsupported = trip.availability == TraitAvailability.UNSUPPORTED
                                            TripTableRow(evidenceShortDate(trip.endedAtEpochMillis), "${trip.driveDurationMillis / 60_000}분",
                                                if (unsupported) "–" else "${trip.supportedSampleCount}번",
                                                if (unsupported) "–" else "${trip.passingSampleCount}번",
                                                if (!trip.included) "내가 운전 안 함" else if (unsupported) "미지원" else "반영")
                                        }
                                        ChayeonText("3분 미만이거나 2km 미만인 주행은 넣지 않아요. 주행 요약에서 뺄 수 있어요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                    }
                                }
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                                        SectionTitle("읽은 신호")
                                        detail.signals.sortedBy { it.ordinal }.forEach { ChayeonText(it.noteLabel(), style = ChayeonType.Minimum) }
                                        SectionTitle("나의 주행 메모")
                                        detail.metrics.forEach { metric ->
                                            ChayeonText(metric.id.noteLabel(), style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
                                            ChayeonText(metric.noteValue(), style = ChayeonType.Secondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })
}

@Composable
private fun CountTile(label: String, value: String, modifier: Modifier) {
    GlassPanel(modifier, contentPadding = PaddingValues(ChayeonSpacing.Large)) {
        ChayeonText(label, style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
        ChayeonText(value, style = ChayeonType.TileValue)
    }
}

@Composable
private fun TripTableRow(whenText: String, duration: String, supported: String, passing: String, inclusion: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = ChayeonSpacing.Compact), horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
        ChayeonText(whenText, Modifier.weight(1.2f), ChayeonType.Minimum)
        ChayeonText(duration, Modifier.weight(1f), ChayeonType.Minimum)
        ChayeonText(supported, Modifier.weight(1.3f), ChayeonType.Minimum)
        ChayeonText(passing, Modifier.weight(1.3f), ChayeonType.Minimum)
        ChayeonText(inclusion, Modifier.weight(1.8f), ChayeonType.Minimum, ChayeonColors.Secondary)
    }
}
