package dev.chayeon.core.engine

import dev.chayeon.core.model.*

enum class DemoResponse { ACCEPT, DECLINE, IGNORE }
data class DemoPeer(
    val id: String, val nickname: String, val avatar: AvatarId,
    val interests: Set<InterestId>, val traits: List<PublicTrait>,
    val response: DemoResponse, val responseDelayMillis: Long = 4_000,
    val greetingDelayMillis: Long = 1_000,
    val incomingDelayMillis: Long? = null, val acceptsAfter: Boolean = false,
    val replyDelayMillis: Long = 1_800,
    val greetingReplies: List<PresetCode> = listOf(PresetCode.NICE_TO_MEET_YOU, PresetCode.SHARED_INTERESTS, PresetCode.TALK_LATER),
    val afterReplies: List<String> = listOf("다시 이야기하게 되어 반가워요", "천천히 서로 알아가면 좋겠어요", "함께 나누고 싶은 이야기가 많아요"),
    val presenceScript: List<DemoPresenceStep> = emptyList(),
) {
    // Only already public categorical levels contribute; private metrics cannot enter the seed.
    val knotSeed: KnotSeed get() = KnotSeed(traits.fold(0L) { value, trait ->
        value or (trait.level.ordinal.toLong() shl (trait.trait.ordinal * 2))
    }, algorithmVersion = 2)
}
data class DemoPresenceStep(val afterConnectionMillis: Long, val presence: SyntheticPresence)

/** Timing and behavior are data, shared unchanged by both hosts. */
object DemoCast {
    val peers = listOf(
        DemoPeer("demo-slow", "느린오후", AvatarId.SLOW, setOf(InterestId.COFFEE, InterestId.JAZZ),
            listOf(PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.STEADY), PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.SIGNATURE), PublicTrait(TraitId.BELT_FIRST, TraitLevel.VISIBLE)),
            DemoResponse.ACCEPT, acceptsAfter = true, afterReplies = listOf("다시 만나서 반가워요. 오늘은 어떤 음악을 들었어요?", "저는 잔잔한 음악과 커피를 좋아해요", "다음에도 편안하게 이야기 나눠요"), presenceScript = listOf(
                DemoPresenceStep(12_000, SyntheticPresence.AWAY),
                DemoPresenceStep(16_000, SyntheticPresence.NEARBY),
                DemoPresenceStep(24_000, SyntheticPresence.AWAY),
                DemoPresenceStep(40_000, SyntheticPresence.NEARBY),
            )),
        DemoPeer("demo-sea", "바다보러가요", AvatarId.SEA, setOf(InterestId.SURFING, InterestId.TRAVEL),
            listOf(PublicTrait(TraitId.LONG_ROAD, TraitLevel.SIGNATURE), PublicTrait(TraitId.ENERGY_PLANNER, TraitLevel.STEADY)),
            DemoResponse.DECLINE),
        DemoPeer("demo-forest", "숲길산책", AvatarId.FOREST, setOf(InterestId.HIKING, InterestId.CAMPING),
            listOf(PublicTrait(TraitId.ENERGY_PLANNER, TraitLevel.SIGNATURE)),
            DemoResponse.IGNORE),
        DemoPeer("demo-jazz", "재즈한잔", AvatarId.JAZZ, setOf(InterestId.JAZZ, InterestId.DRIVE_MUSIC),
            listOf(PublicTrait(TraitId.CABIN_CARE, TraitLevel.SIGNATURE), PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.STEADY), PublicTrait(TraitId.STEADY_PACE, TraitLevel.STEADY), PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.VISIBLE)),
            DemoResponse.ACCEPT, incomingDelayMillis = 7_000),
        DemoPeer("demo-star", "별보는밤", AvatarId.STAR, setOf(InterestId.NIGHT_VIEW, InterestId.FILM_PHOTOGRAPHY),
            listOf(PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE), PublicTrait(TraitId.CABIN_CARE, TraitLevel.STEADY)),
            DemoResponse.ACCEPT, acceptsAfter = true, afterReplies = listOf("다시 이야기하게 되어 반가워요. 야경 보는 걸 좋아하세요?", "저는 조용히 별을 보는 시간이 좋아요", "서로 좋아하는 풍경을 하나씩 나눠 봐요")),
    )
}
