package dev.chayeon.feature.signature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.chayeon.core.model.*
import dev.chayeon.core.ui.*
import kotlin.math.roundToInt

internal data class TraitCountLabels(val supported: String, val passing: String)

internal val TraitId.countLabels: TraitCountLabels get() = when (this) {
    TraitId.GENTLE_RIDE -> TraitCountLabels("주행 구간", "부드러운 구간")
    TraitId.EARLY_BRAKE -> TraitCountLabels("제동", "여유롭게 멈춤")
    TraitId.SIGNAL_MANNERS -> TraitCountLabels("방향 전환", "깜빡이를 켬")
    TraitId.STEADY_PACE -> TraitCountLabels("정속 구간", "흐름을 지킴")
    TraitId.BELT_FIRST -> TraitCountLabels("출발", "벨트를 먼저 맴")
    TraitId.LONG_ROAD -> TraitCountLabels("주행", "긴 드라이브")
    TraitId.NIGHT_DRIVE -> TraitCountLabels("주행", "밤 드라이브")
    TraitId.TIRE_CARE -> TraitCountLabels("타이어 확인", "저압 경고 없음")
    TraitId.ENERGY_PLANNER -> TraitCountLabels("잔량 확인", "여유 있는 잔량")
    TraitId.BATTERY_KIND -> TraitCountLabels("충전 한도 확인", "배터리를 아낌")
    TraitId.FLUID_READY -> TraitCountLabels("소모품 확인", "경고 없음")
    TraitId.CABIN_CARE -> TraitCountLabels("공조 확인", "공기청정을 켬")
}

internal fun TraitId.collectionSummary(supported: Int, passing: Int): String = when (this) {
    TraitId.GENTLE_RIDE -> "주행 구간 ${supported}번 중 ${passing}번이 부드러웠어요"
    TraitId.EARLY_BRAKE -> "제동 ${supported}번 중 ${passing}번 여유롭게 멈췄어요"
    TraitId.SIGNAL_MANNERS -> "방향 전환 ${supported}번 중 ${passing}번 깜빡이를 켰어요"
    TraitId.STEADY_PACE -> "정속 구간 ${supported}번 중 ${passing}번 흐름을 지켰어요"
    TraitId.BELT_FIRST -> "출발 ${supported}번 중 ${passing}번 벨트를 먼저 맸어요"
    TraitId.LONG_ROAD -> "주행 ${supported}번 중 ${passing}번 긴 드라이브를 했어요"
    TraitId.NIGHT_DRIVE -> "주행 ${supported}번 중 ${passing}번 밤 드라이브를 했어요"
    TraitId.TIRE_CARE -> "확인 ${supported}번 중 ${passing}번 저압 경고가 없었어요"
    TraitId.ENERGY_PLANNER -> "확인 ${supported}번 중 ${passing}번 잔량에 여유가 있었어요"
    TraitId.BATTERY_KIND -> "확인 ${supported}번 중 ${passing}번 충전 한도를 낮춰 두었어요"
    TraitId.FLUID_READY -> "확인 ${supported}번 중 ${passing}번 소모품 경고가 없었어요"
    TraitId.CABIN_CARE -> "확인 ${supported}번 중 ${passing}번 공기청정을 켰어요"
}

internal fun scoreStageHint(score: Int): String = when {
    score < 60 -> "나만 보여요 · 보여요까지 ${60 - score}점"
    score < 75 -> "보여요 · 꾸준해요까지 ${75 - score}점"
    score < 90 -> "꾸준해요 · 시그니처까지 ${90 - score}점"
    else -> "시그니처 · 나의 꾸준한 습관이에요"
}

@Composable
internal fun ScoreBar(score: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(ChayeonDimensions.ProgressDot)
        .clip(RoundedCornerShape(ChayeonRadii.Small)).background(ChayeonColors.Border)) {
        Box(Modifier.fillMaxWidth(score.coerceIn(0, 100) / 100f).fillMaxHeight()
            .clip(RoundedCornerShape(ChayeonRadii.Small)).background(ChayeonColors.Pink))
    }
}

@Composable
internal fun ProfileTraitRows(signature: MySignature, onTrait: (TraitId) -> Unit) {
    SectionTitle("드라이브 프로필", subtitle = "항목을 누르면 근거를 볼 수 있어요 · 60점부터 상대에게 보여요")
    TraitId.entries.forEach { trait ->
        val evidence = signature.evidence.firstOrNull { it.trait == trait }
        val unsupported = evidence?.availability == TraitAvailability.UNSUPPORTED
        val score = evidence?.score.takeIf { trait.isScored && evidence != null && evidence.validTripCount >= 3 && !unsupported }
        val status = when {
            unsupported -> "이 차량에서 제공되지 않아요"
            evidence == null || evidence.validTripCount < 3 -> "기록 중"
            trait.isScored && (score == null || score < 60) -> "나만 보여요"
            signature.visibility.publishSignature && trait in signature.visibility.visibleTraits && evidence.isPublishable -> "공개"
            else -> "숨김"
        }
        GlassCard(Modifier.fillMaxWidth().heightIn(min = ChayeonSpacing.Touch)
            .testTag("profile-trait-${trait.name}")
            .clickable(enabled = LocalDrivingPolicy.current == DrivingPolicy.ALLOWED,
                role = Role.Button, onClick = { onTrait(trait) })
            .semantics { contentDescription = "${trait.presentation.name}, ${score?.let { "${it}점, " }.orEmpty()}$status, 근거 보기" },
            contentPadding = PaddingValues(ChayeonSpacing.Medium)) {
            Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
                    ChayeonIcon(trait.presentation.icon, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Pink)
                    ChayeonText(trait.presentation.name, Modifier.weight(1f), ChayeonType.Label)
                    if (score != null) ChayeonText("$score", style = ChayeonType.Section, color = ChayeonColors.Pink)
                    ChayeonIcon(ChayeonIcon.FORWARD, Modifier.size(ChayeonDimensions.SmallIcon), ChayeonColors.Secondary)
                }
                if (score != null) ScoreBar(score)
                ChayeonText(if (!trait.isScored && !unsupported) "$status · 취향은 점수 없이 보여요" else status,
                    style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            }
        }
    }
}

@Composable
internal fun ProfileBuilding(signature: MySignature, recordingEnabled: Boolean) {
    val count = signature.includedTripCount.coerceIn(0, 3)
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            StatusChip(if (recordingEnabled) "주행 기록 켜짐" else "주행 기록 꺼짐", variant = StatusVariant.PLAIN)
            SectionTitle("달릴수록 프로필이 쌓여요", subtitle = if (recordingEnabled) "평소처럼 운전하면 차가 읽은 신호로 프로필이 만들어져요." else "차량 데이터 사용에 동의하면 주행 기록을 모을 수 있어요.")
            ChayeonText("$count / 3 주행", style = ChayeonType.Section)
            Row(horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).height(ChayeonDimensions.ProgressDot)
                        .clip(RoundedCornerShape(ChayeonRadii.Small))
                        .background(if (index < count) ChayeonColors.Pink else ChayeonColors.Border))
                }
            }
            ChayeonText("${3 - count}번 더 달리면 점수가 생겨요. 공개할 특성은 내가 골라요.", style = ChayeonType.Secondary)
            SectionTitle("지난 주행에서 모은 것")
            val latestId = signature.evidence.flatMap { it.recentTrips }.filter { it.included }
                .maxByOrNull { it.endedAtEpochMillis }?.tripId
            signature.evidence.forEach { evidence ->
                val trip = evidence.recentTrips.firstOrNull { it.tripId == latestId }
                if (evidence.availability == TraitAvailability.UNSUPPORTED) {
                    ChayeonText("${evidence.trait.presentation.name} · 이 차량에서 제공되지 않아요", style = ChayeonType.Minimum)
                } else if (trip != null && trip.supportedSampleCount > 0) {
                    ChayeonText("${evidence.trait.presentation.name} · ${evidence.trait.collectionSummary(trip.supportedSampleCount, trip.passingSampleCount)}", style = ChayeonType.Minimum)
                }
            }
            if (latestId == null) ChayeonText("첫 주행을 마치면 여기에 기록이 모여요.", style = ChayeonType.Secondary)
            ChayeonText("3분 이상, 2km 이상 달린 주행만 넣어요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            ChayeonText("다른 사람이 운전했다면 주행 요약에서 뺄 수 있어요. 뺀 주행은 점수에 넣지 않아요.", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
        }
    }
}

@Composable
internal fun TraitTripChart(evidence: TraitEvidence) {
    val trips = evidence.recentTrips.filter { it.included && it.supportedSampleCount > 0 }.take(12).reversed()
    var selectedId by remember(evidence.id) { mutableStateOf<String?>(null) }
    val selected = trips.firstOrNull { it.tripId == selectedId }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Medium)) {
            SectionTitle("주행별 ${evidence.trait.presentation.name}", subtitle = "최근 ${trips.size}번 주행 · 막대를 누르면 그 주행을 봐요")
            ChayeonText("공개 기준 60", style = ChayeonType.Minimum, color = ChayeonColors.Secondary)
            if (trips.isEmpty()) ChayeonText("반영할 주행을 모으고 있어요.", style = ChayeonType.Secondary)
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).drawWithContent {
                drawContent()
                val threshold = ChayeonDimensions.PeerLarge.toPx() * .4f
                drawLine(ChayeonColors.Secondary.copy(alpha = .7f), Offset(0f, threshold), Offset(size.width, threshold),
                    ChayeonElevation.Border.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(ChayeonSpacing.Small.toPx(), ChayeonSpacing.Tiny.toPx())))
            }, horizontalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                trips.forEach { trip ->
                    val ratio = trip.passingSampleCount.toFloat() / trip.supportedSampleCount
                    val score = (ratio * 100).roundToInt()
                    Column(Modifier.width(ChayeonSpacing.Touch), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ChayeonSpacing.Small)) {
                        Box(Modifier.fillMaxWidth().height(ChayeonDimensions.PeerLarge)
                            .testTag("trip-bar-${trip.tripId}")
                            .clickable(role = Role.Button, onClick = { selectedId = trip.tripId })
                            .semantics { contentDescription = "${evidenceDate(trip.endedAtEpochMillis)}, ${score}점, 주행 보기" }) {
                            Canvas(Modifier.matchParentSize()) {
                                listOf(0f, .5f, 1f).forEach { fraction ->
                                    drawLine(ChayeonColors.Border.copy(alpha = .12f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), ChayeonElevation.Border.toPx())
                                }
                            }
                            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = ChayeonSpacing.Small)
                                .fillMaxWidth().fillMaxHeight(ratio.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = ChayeonRadii.Small, topEnd = ChayeonRadii.Small))
                                .background(ChayeonColors.Pink.copy(alpha = if (selectedId == trip.tripId) 1f else .75f)))
                        }
                        ChayeonText(evidenceShortDate(trip.endedAtEpochMillis), style = ChayeonType.Minimum)
                    }
                }
            }
            if (selected != null) {
                ChayeonText("선택한 주행 · ${evidenceDate(selected.endedAtEpochMillis)}", style = ChayeonType.Label)
                ChayeonText("${selected.driveDurationMillis / 60_000}분 · ${evidence.trait.countLabels.supported} ${selected.supportedSampleCount}번 · ${evidence.trait.countLabels.passing} ${selected.passingSampleCount}번", style = ChayeonType.Secondary)
            }
        }
    }
}
