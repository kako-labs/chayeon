package dev.chayeon.core.engine

import dev.chayeon.core.model.*

enum class DemoResponse { ACCEPT, DECLINE, IGNORE }
data class DemoPeer(
    val id: String, val nickname: String, val avatar: AvatarId,
    val interests: Set<InterestId>, val traits: List<PublicTrait>,
    val response: DemoResponse, val responseDelayMillis: Long = 4_000,
    val incomingDelayMillis: Long? = null, val acceptsAfter: Boolean = false,
    val presenceScript: List<DemoPresenceStep> = emptyList(),
) {
    val contacts: ContactIds get() = ContactIds(instagramId = when (id) {
        "demo-star" -> "byeol-night.drive"
        "demo-slow" -> "slow-afternoon"
        else -> "${id}-drive"
    })

    // Only already public categorical levels contribute; private metrics cannot enter the seed.
    val knotSeed: KnotSeed get() = KnotSeed(traits.fold(0L) { value, trait ->
        value or (trait.level.ordinal.toLong() shl (trait.trait.ordinal * 2))
    }, algorithmVersion = 2)
}
data class DemoPresenceStep(val afterConnectionMillis: Long, val presence: SyntheticPresence)

/** Timing and behavior are data, shared unchanged by both hosts. */
object DemoCast {
    val peers = listOf(
        DemoPeer("demo-star", "별보는밤", AvatarId.STAR, setOf(InterestId.NIGHT_VIEW, InterestId.FILM_PHOTOGRAPHY),
            listOf(PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE), PublicTrait(TraitId.CABIN_CARE, TraitLevel.STEADY)),
            DemoResponse.ACCEPT, acceptsAfter = true),
        DemoPeer("demo-jazz", "재즈한잔", AvatarId.JAZZ, setOf(InterestId.JAZZ, InterestId.DRIVE_MUSIC),
            listOf(PublicTrait(TraitId.CABIN_CARE, TraitLevel.SIGNATURE), PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.STEADY), PublicTrait(TraitId.STEADY_PACE, TraitLevel.STEADY), PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.VISIBLE)),
            DemoResponse.ACCEPT, incomingDelayMillis = 7_000),
        DemoPeer("demo-slow", "느린오후", AvatarId.SLOW, setOf(InterestId.COFFEE, InterestId.JAZZ),
            listOf(PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.STEADY), PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.SIGNATURE), PublicTrait(TraitId.BELT_FIRST, TraitLevel.VISIBLE)),
            DemoResponse.ACCEPT, acceptsAfter = true, presenceScript = listOf(
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
    )
}
