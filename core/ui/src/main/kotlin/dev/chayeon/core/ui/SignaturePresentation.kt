package dev.chayeon.core.ui

import dev.chayeon.core.model.AvatarId
import dev.chayeon.core.model.ConnectionEndReason
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.InterestId
import dev.chayeon.core.model.PresetCode
import dev.chayeon.core.model.ProfileKind
import dev.chayeon.core.model.ReportReason
import dev.chayeon.core.model.TraitId
import dev.chayeon.core.model.TraitLevel

enum class TraitCategory(val label: String) { STYLE("주행 스타일"), CARE("차량 관리") }

data class TraitPresentation(val name: String, val publicPhrase: String, val icon: ChayeonIcon, val category: TraitCategory)

val TraitId.presentation: TraitPresentation get() = when (this) {
    TraitId.GENTLE_RIDE -> TraitPresentation("부드러운 주행", "함께 타면 편안한 운전이에요", ChayeonIcon.GENTLE, TraitCategory.STYLE)
    TraitId.EARLY_BRAKE -> TraitPresentation("여유로운 제동", "미리 천천히 멈춰요", ChayeonIcon.BRAKE, TraitCategory.STYLE)
    TraitId.SIGNAL_MANNERS -> TraitPresentation("깜빡이 매너", "방향지시등을 잊지 않아요", ChayeonIcon.SIGNAL, TraitCategory.STYLE)
    TraitId.STEADY_PACE -> TraitPresentation("한결같은 항속", "흐름을 지키며 달려요", ChayeonIcon.PACE, TraitCategory.STYLE)
    TraitId.BELT_FIRST -> TraitPresentation("벨트 먼저", "출발 전에 안전벨트부터 매요", ChayeonIcon.BELT, TraitCategory.STYLE)
    TraitId.LONG_ROAD -> TraitPresentation("긴 여정파", "긴 드라이브를 즐겨요", ChayeonIcon.ROAD, TraitCategory.STYLE)
    TraitId.NIGHT_DRIVE -> TraitPresentation("밤 드라이브파", "밤길 드라이브를 좋아해요", ChayeonIcon.MOON, TraitCategory.STYLE)
    TraitId.TIRE_CARE -> TraitPresentation("타이어 관리", "타이어 공기압을 챙겨요", ChayeonIcon.TIRE, TraitCategory.CARE)
    TraitId.ENERGY_PLANNER -> TraitPresentation("미리 채우는 습관", "연료와 배터리를 미리 채워요", ChayeonIcon.ENERGY, TraitCategory.CARE)
    TraitId.BATTERY_KIND -> TraitPresentation("배터리 아끼기", "배터리를 오래 쓰도록 충전해요", ChayeonIcon.BATTERY, TraitCategory.CARE)
    TraitId.FLUID_READY -> TraitPresentation("소모품 점검", "소모품을 제때 점검해요", ChayeonIcon.DROP, TraitCategory.CARE)
    TraitId.CABIN_CARE -> TraitPresentation("실내 공기 챙김", "실내 공기를 쾌적하게 유지해요", ChayeonIcon.AIR, TraitCategory.CARE)
}

val TraitLevel.label: String get() = when (this) {
    TraitLevel.RECORDING -> "기록 중"
    TraitLevel.VISIBLE -> "보여요"
    TraitLevel.STEADY -> "꾸준해요"
    TraitLevel.SIGNATURE -> "시그니처"
}

val InterestId.label: String get() = when (this) {
    InterestId.NIGHT_VIEW -> "야경"
    InterestId.JAZZ -> "재즈"
    InterestId.DRIVE_MUSIC -> "드라이브 음악"
    InterestId.COFFEE -> "커피"
    InterestId.EXHIBITIONS -> "전시"
    InterestId.FILM_PHOTOGRAPHY -> "필름 사진"
    InterestId.CAMPING -> "캠핑"
    InterestId.RUNNING -> "러닝"
    InterestId.WINE -> "와인"
    InterestId.MOVIES -> "영화"
    InterestId.DOGS -> "반려견"
    InterestId.TRAVEL -> "여행"
    InterestId.SURFING -> "서핑"
    InterestId.HIKING -> "등산"
}

val PresetCode.label: String get() = when (this) {
    PresetCode.HELLO -> "안녕하세요"
    PresetCode.NICE_TO_MEET_YOU -> "반가워요"
    PresetCode.GOOD_EVENING -> "좋은 저녁이에요"
    PresetCode.SHARED_INTERESTS -> "관심사가 비슷하네요"
    PresetCode.TALK_LATER -> "잠시 후 다시 이야기해요"
    PresetCode.TAKE_CARE -> "조심히 가세요"
    PresetCode.NICE_TO_MEET_AGAIN -> "다시 만나서 반가워요"
    PresetCode.ADMIRE_GENTLE_RIDE -> "함께 타면 편안할 것 같아요"
    PresetCode.ADMIRE_EARLY_BRAKE -> "여유롭게 멈추는 습관이 멋져요"
    PresetCode.ADMIRE_SIGNAL_MANNERS -> "깜빡이 매너가 멋져요"
    PresetCode.ADMIRE_STEADY_PACE -> "한결같은 주행이 멋져요"
    PresetCode.ADMIRE_BELT_FIRST -> "벨트부터 챙기는 습관이 멋져요"
    PresetCode.SHARE_LONG_ROAD -> "저도 긴 드라이브를 좋아해요"
    PresetCode.SHARE_NIGHT_DRIVE -> "저도 밤 드라이브를 좋아해요"
    PresetCode.ADMIRE_TIRE_CARE -> "타이어까지 챙기는 모습이 멋져요"
    PresetCode.ADMIRE_ENERGY_PLANNER -> "미리 채우는 습관이 멋져요"
    PresetCode.ADMIRE_BATTERY_KIND -> "배터리를 아끼는 모습이 멋져요"
    PresetCode.ADMIRE_FLUID_READY -> "꼼꼼하게 점검하는 모습이 멋져요"
    PresetCode.ADMIRE_CABIN_CARE -> "실내 공기까지 챙기는 모습이 멋져요"
}

val ReportReason.label: String get() = when (this) {
    ReportReason.UNPLEASANT_MESSAGE -> "불쾌한 메시지"
    ReportReason.UNWANTED_APPROACH -> "원치 않는 접근"
    ReportReason.IMPERSONATION -> "사칭 의심"
    ReportReason.POSSIBLE_MINOR -> "미성년자로 보임"
    ReportReason.OTHER -> "기타"
}

data class ConnectionEndPresentation(val title: String, val body: String, val label: String, val tag: String = "연결 종료")

fun ConnectionEndReason.presentation(peerNickname: String): ConnectionEndPresentation {
    val tail = "스친 인연에 시각과 기간만 남겨요. 위치와 경로는 남기지 않아요."
    return when (this) {
        ConnectionEndReason.DISTANCE -> ConnectionEndPresentation("멀어져서 연결이 끝났어요", tail, "멀어짐")
        ConnectionEndReason.PEER_LEFT -> ConnectionEndPresentation("$peerNickname 님이 연결을 마쳤어요", tail, "상대가 나감")
        ConnectionEndReason.SELF_LEFT -> ConnectionEndPresentation("연결을 마쳤어요", tail, "내가 나감")
        ConnectionEndReason.STATUS_UNKNOWN -> ConnectionEndPresentation("연결 상태를 확인할 수 없어 끝냈어요", "위치나 통신이 오래 끊겼어요. 멀어졌다고 판단한 것은 아니에요.", "확인 불가", "상태 확인 불가")
        ConnectionEndReason.DISCOVERY_OFF -> ConnectionEndPresentation("발견을 꺼서 연결이 끝났어요", tail, "발견 끔")
        ConnectionEndReason.BLOCKED -> ConnectionEndPresentation("연결을 마쳤어요", tail, "연결 종료")
    }
}

val ProfileKind.label: String get() = when (this) {
    ProfileKind.VIRTUAL_PROFILE -> "가상 프로필"
    ProfileKind.DEMO_PEER -> "시연용 가상 상대"
}

val DataSource.label: String get() = when (this) {
    DataSource.VEHICLE -> "차량 주행"
    DataSource.SYNTHETIC -> "합성 주행"
}

val AvatarId.label: String get() = when (this) {
    AvatarId.DAWN -> "초승달 찹쌀떡"
    AvatarId.SLOW -> "햇님 찹쌀떡"
    AvatarId.JAZZ -> "음표 찹쌀떡"
    AvatarId.SEA -> "물결 찹쌀떡"
    AvatarId.FOREST -> "새싹 찹쌀떡"
    AvatarId.STAR -> "별 찹쌀떡"
}
