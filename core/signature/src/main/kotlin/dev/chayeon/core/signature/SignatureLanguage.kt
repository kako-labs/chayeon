package dev.chayeon.core.signature

import dev.chayeon.core.model.TraitId

/** Closed vocabulary: profile titles never include user input or private evidence. */
object SignatureLanguage {
    const val RECORDING_TITLE = "시그니처를 만드는 중이에요"
    val modifiers: Map<TraitId, String> = linkedMapOf(
        TraitId.GENTLE_RIDE to "함께 타면 편안한",
        TraitId.EARLY_BRAKE to "여유롭게 멈추는",
        TraitId.SIGNAL_MANNERS to "깜빡이를 잊지 않는",
        TraitId.STEADY_PACE to "흐름을 지키는",
        TraitId.BELT_FIRST to "벨트부터 챙기는",
        TraitId.LONG_ROAD to "길 위의 여유를 즐기는",
        TraitId.NIGHT_DRIVE to "차분하게 여정을 즐기는",
        TraitId.TIRE_CARE to "타이어까지 챙기는",
        TraitId.ENERGY_PLANNER to "미리 채울 줄 아는",
        TraitId.BATTERY_KIND to "배터리를 아끼는",
        TraitId.FLUID_READY to "작은 점검도 챙기는",
        TraitId.CABIN_CARE to "쾌적함을 챙기는",
    )
    val nouns: List<String> = listOf("드라이버", "장거리 드라이버", "밤 드라이버")
    val sharedSentences: Map<TraitId, String> = linkedMapOf(
        TraitId.GENTLE_RIDE to "둘 다 함께 타면 편안한 운전을 해요",
        TraitId.EARLY_BRAKE to "둘 다 미리 천천히 멈춰요",
        TraitId.SIGNAL_MANNERS to "둘 다 깜빡이를 챙겨요",
        TraitId.STEADY_PACE to "둘 다 흐름을 지키며 달려요",
        TraitId.BELT_FIRST to "둘 다 출발 전에 벨트부터 챙겨요",
        TraitId.LONG_ROAD to "둘 다 긴 드라이브를 좋아해요",
        TraitId.NIGHT_DRIVE to "둘 다 밤 드라이브를 좋아해요",
        TraitId.TIRE_CARE to "둘 다 타이어 공기압을 챙겨요",
        TraitId.ENERGY_PLANNER to "둘 다 연료와 배터리를 미리 채워요",
        TraitId.BATTERY_KIND to "둘 다 배터리를 아껴요",
        TraitId.FLUID_READY to "둘 다 소모품 점검을 챙겨요",
        TraitId.CABIN_CARE to "둘 다 실내 공기를 챙겨요",
    )

    /** Exhaustive review surface for the complete modifier/noun vocabulary. */
    fun titleCombinations(): List<String> = modifiers.values.flatMap { modifier -> nouns.map { "$modifier $it" } }
}
