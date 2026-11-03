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
        TraitId.BELT_FIRST to "안전부터 챙기는",
        TraitId.LONG_ROAD to "먼 길을 즐기는",
        TraitId.NIGHT_DRIVE to "밤길을 좋아하는",
        TraitId.TIRE_CARE to "떠나기 전에 살피는",
        TraitId.ENERGY_PLANNER to "미리 채워 두는",
        TraitId.BATTERY_KIND to "오래 아껴 쓰는",
        TraitId.FLUID_READY to "작은 것도 챙기는",
        TraitId.CABIN_CARE to "공기까지 신경 쓰는",
    )
    val nouns: List<String> = listOf("드라이버", "장거리 드라이버", "밤 드라이버")
    val sharedSentences: Map<TraitId, String> = linkedMapOf(
        TraitId.GENTLE_RIDE to "둘 다 함께 타면 편안한 운전을 해요",
        TraitId.EARLY_BRAKE to "둘 다 미리 천천히 멈춰요",
        TraitId.SIGNAL_MANNERS to "둘 다 깜빡이를 챙겨요",
        TraitId.STEADY_PACE to "둘 다 흐름을 지키며 달려요",
        TraitId.BELT_FIRST to "둘 다 출발 전에 안전부터 챙겨요",
        TraitId.LONG_ROAD to "둘 다 긴 드라이브를 좋아해요",
        TraitId.NIGHT_DRIVE to "둘 다 밤 드라이브를 좋아해요",
        TraitId.TIRE_CARE to "둘 다 떠나기 전에 미리 살펴요",
        TraitId.ENERGY_PLANNER to "둘 다 미리 채워 두는 편이에요",
        TraitId.BATTERY_KIND to "둘 다 오래 쓸 것을 아껴요",
        TraitId.FLUID_READY to "둘 다 작은 것도 미리 챙겨요",
        TraitId.CABIN_CARE to "둘 다 함께 타는 공기까지 신경 써요",
    )

    val titlePriority: List<TraitId> = listOf(
        TraitId.TIRE_CARE, TraitId.ENERGY_PLANNER, TraitId.BATTERY_KIND, TraitId.FLUID_READY,
        TraitId.CABIN_CARE, TraitId.SIGNAL_MANNERS, TraitId.GENTLE_RIDE, TraitId.EARLY_BRAKE,
        TraitId.STEADY_PACE, TraitId.BELT_FIRST, TraitId.LONG_ROAD, TraitId.NIGHT_DRIVE,
    )

    fun nounsFor(trait: TraitId): List<String> = when (trait) {
        TraitId.LONG_ROAD -> nouns.filterNot { it == "장거리 드라이버" }
        TraitId.NIGHT_DRIVE -> nouns.filterNot { it == "밤 드라이버" }
        else -> nouns
    }

    fun titleTrait(title: String): TraitId? = modifiers.entries.firstOrNull { (trait, modifier) ->
        nounsFor(trait).any { noun -> title == "$modifier $noun" }
    }?.key

    /** Exhaustive review surface for the complete modifier/noun vocabulary. */
    fun titleCombinations(): List<String> = modifiers.flatMap { (trait, modifier) -> nounsFor(trait).map { "$modifier $it" } }
}
