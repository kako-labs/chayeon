package dev.chayeon.feature.signature

import dev.chayeon.core.model.AppError
import dev.chayeon.core.model.EvidenceWindow
import dev.chayeon.core.model.MetricId
import dev.chayeon.core.model.MetricUnit
import dev.chayeon.core.model.PrivateMetric
import dev.chayeon.core.model.SignalKind
import dev.chayeon.core.model.TraitAvailability
import dev.chayeon.core.model.TraitEvidence
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TraitLevel
import dev.chayeon.core.ui.label
import dev.chayeon.core.ui.presentation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun MetricId.noteLabel(): String = when (this) {
    MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES -> "가속이나 감속이 크게 달라진 횟수"
    MetricId.GENTLE_BRAKE_RATIO -> "천천히 멈춘 제동 구간"
    MetricId.SIGNAL_USAGE_RATIO -> "회전할 때 방향지시등을 켠 비율"
    MetricId.CRUISE_VARIATION_COEFFICIENT -> "정속 구간에서 속도가 달라진 정도"
    MetricId.CRUISE_USAGE_RATIO -> "크루즈 기능을 사용한 비율"
    MetricId.BELT_BEFORE_DRIVE_RATIO -> "출발 전에 안전벨트를 맨 주행"
    MetricId.LONG_TRIP_RATIO -> "긴 드라이브가 차지한 비율"
    MetricId.NIGHT_TRIP_RATIO -> "밤 드라이브가 차지한 비율"
    MetricId.HEALTHY_TIRE_RATIO -> "타이어 공기압이 저압 기준보다 높았던 비율"
    MetricId.ENERGY_RESERVE_RATIO -> "연료나 배터리가 15%보다 남아 있던 비율"
    MetricId.KIND_CHARGE_LIMIT_RATIO -> "충전 한도를 90% 이하로 둔 비율"
    MetricId.NO_FLUID_WARNING_RATIO -> "소모품 경고가 없었던 비율"
    MetricId.AIR_CLEANING_RATIO -> "공기청정을 켠 비율"
}

internal fun PrivateMetric.noteValue(): String {
    val expectedUnit = when (id) {
        MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES -> MetricUnit.EVENTS_PER_TEN_MINUTES
        MetricId.CRUISE_VARIATION_COEFFICIENT -> MetricUnit.COEFFICIENT
        else -> MetricUnit.RATIO
    }
    if (!value.isFinite() || value < 0 || unit != expectedUnit || (unit == MetricUnit.RATIO && value > 1)) {
        return "기록을 확인할 수 없어요"
    }
    return when (unit) {
        MetricUnit.RATIO -> "${decimal(value * 100)}%"
        MetricUnit.EVENTS_PER_TEN_MINUTES -> "10분당 ${decimal(value)}회"
        MetricUnit.COEFFICIENT -> if ((value * 100).isFinite()) {
            "평균 대비 ${decimal(value * 100)}%"
        } else {
            "기록을 확인할 수 없어요"
        }
    }
}

private fun decimal(value: Double): String = BigDecimal.valueOf(value)
    .setScale(1, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

internal fun SignalKind.noteLabel(): String = when (this) {
    SignalKind.SPEED -> "속도 변화"
    SignalKind.BRAKE -> "브레이크 사용"
    SignalKind.STEERING_MAGNITUDE -> "핸들을 돌린 정도"
    SignalKind.TURN_INDICATOR -> "방향지시등 사용"
    SignalKind.CRUISE -> "크루즈 기능 사용"
    SignalKind.SEAT_BELT -> "안전벨트 착용"
    SignalKind.GEAR -> "기어 상태"
    SignalKind.TRIP_LENGTH -> "주행 길이"
    SignalKind.CLOCK -> "주행 시간대"
    SignalKind.TIRE_PRESSURE -> "타이어 공기압"
    SignalKind.TIRE_WARNING -> "타이어 저압 경고"
    SignalKind.FUEL_LEVEL -> "연료 잔량"
    SignalKind.BATTERY_LEVEL -> "배터리 잔량"
    SignalKind.CHARGE_LIMIT -> "배터리 충전 한도"
    SignalKind.OIL_WARNING -> "오일 경고"
    SignalKind.BRAKE_FLUID_WARNING -> "브레이크액 경고"
    SignalKind.WASHER_FLUID_WARNING -> "워셔액 경고"
    SignalKind.AIR_CLEANING -> "실내 공기청정 사용"
}

internal fun TraitEvidence.levelExplanation(): String {
    if (availability == TraitAvailability.UNSUPPORTED) {
        return "이 차량에서 제공되지 않아요. 이 특성은 계산하지 않고, 상대에게도 보이지 않아요."
    }
    if (availability == TraitAvailability.INSUFFICIENT_EVIDENCE || level == TraitLevel.RECORDING) {
        return if (validTripCount < 3) {
            "시그니처는 유효한 주행이 3회 이상 모이면 만들어요. 아직은 나만 보는 기록으로 남아요."
        } else {
            "이 특성을 설명할 기록을 모으고 있어요. 기록이 충분해지기 전에는 상대에게 보이지 않아요."
        }
    }
    val subject = if (trait == TraitId.LONG_ROAD || trait == TraitId.NIGHT_DRIVE) "취향" else "습관"
    val meaning = when (level) {
        TraitLevel.VISIBLE -> "이 기간의 기록에서 이 ${subject}이 나타났어요"
        TraitLevel.STEADY -> "이 기간의 기록에서 이 ${subject}이 꾸준히 나타났어요"
        TraitLevel.SIGNATURE -> "이 기간의 기록에서 이 ${subject}이 자주, 일관되게 나타났어요"
        TraitLevel.RECORDING -> error("Recording evidence is handled before level presentation")
    }
    return "$meaning. 그래서 ‘${trait.presentation.name}’ 특성을 ‘${level.label}’로 표시해요."
}

private val evidenceZone: ZoneId = ZoneId.of("Asia/Seoul")
private val evidenceDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

internal fun evidenceDate(epochMillis: Long): String = if (epochMillis < 0) {
    "날짜를 확인할 수 없어요"
} else {
    evidenceDateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(evidenceZone))
}

internal fun evidencePeriod(window: EvidenceWindow): String {
    if (window.fromEpochMillis < 0 || window.untilEpochMillis < window.fromEpochMillis) {
        return "기록 기간을 확인할 수 없어요"
    }
    val first = evidenceDate(window.fromEpochMillis)
    val last = evidenceDate(window.untilEpochMillis)
    return if (first == last) first else "$first ~ $last"
}

internal fun AppError.signatureMessage(action: String): String = when (this) {
    AppError.NotAuthorized -> "로그인 상태를 확인할 수 없어요. 다시 로그인한 뒤 $action 주세요."
    AppError.AlreadyClosed -> "기록 상태가 바뀌었어요. 새로 확인한 뒤 다시 $action 주세요."
    AppError.Expired -> "요청 시간이 지났어요. 새로 확인한 뒤 다시 $action 주세요."
    AppError.PeerUnavailable -> "지금은 이 작업을 마칠 수 없어요. 잠시 후 다시 $action 주세요."
    AppError.Restricted -> "주차 상태를 확인한 뒤 $action 주세요. 주행 중에는 바꿀 수 없어요."
    AppError.Network -> "연결이 끊겨서 마치지 못했어요. 연결을 확인한 뒤 다시 $action 주세요."
    AppError.Unknown -> "변경을 마쳤는지 확인할 수 없어요. 다시 $action 주세요."
}
