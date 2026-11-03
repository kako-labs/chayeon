package dev.chayeon.feature.signature

import dev.chayeon.core.model.*

internal object SignatureScreenFixtures {
    val driving = DrivingState(version = 1, policy = DrivingPolicy.ALLOWED, gear = Gear.PARK)
    val evidence = TraitEvidence(
        "gentle", 4, TraitId.GENTLE_RIDE, TraitAvailability.AVAILABLE, TraitLevel.STEADY,
        DataSource.SYNTHETIC, EvidenceWindow(1_792_800_000_000, 1_795_392_000_000), 8, 240,
        setOf(SignalKind.SPEED),
        listOf(PrivateMetric(MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES, .8, MetricUnit.EVENTS_PER_TEN_MINUTES)),
        listOf(EvidenceChange(1_795_305_600_000, TraitLevel.VISIBLE, TraitLevel.STEADY)),
    )
    val public = PublicSignature("public", 4, "여유롭게 멈추는 드라이버",
        listOf(PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.SIGNATURE), PublicTrait(TraitId.GENTLE_RIDE, TraitLevel.STEADY),
            PublicTrait(TraitId.TIRE_CARE, TraitLevel.STEADY)), KnotSeed(42), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
    val visibility = VisibilitySettings(version = 4, visibleTraits = public.traits.map { it.trait }.toSet(), publishSignature = true)
    val signature = MySignature(version = 4, publicSignature = public, knotSeed = KnotSeed(42),
        evidence = TraitId.entries.map { trait ->
            when (trait) {
                TraitId.BATTERY_KIND -> evidence.copy(id = trait.name, trait = trait, level = TraitLevel.RECORDING,
                    availability = TraitAvailability.UNSUPPORTED, signals = emptySet(), metrics = emptyList())
                TraitId.EARLY_BRAKE -> evidence.copy(id = trait.name, trait = trait, level = TraitLevel.SIGNATURE)
                TraitId.GENTLE_RIDE, TraitId.TIRE_CARE -> evidence.copy(id = trait.name, trait = trait)
                else -> evidence.copy(id = trait.name, trait = trait, level = TraitLevel.RECORDING,
                    availability = TraitAvailability.INSUFFICIENT_EVIDENCE, validTripCount = 2, metrics = emptyList())
            }
        }, visibility = visibility)
    val profile = MyProfile("my-profile", 4, PublicProfile("public-profile", 4, "새벽드라이브", AvatarId.DAWN,
        setOf(InterestId.JAZZ, InterestId.COFFEE, InterestId.DRIVE_MUSIC), ProfileKind.VIRTUAL_PROFILE, public), RegistrationState.READY)
    val recap = TripRecap("recap", 4, "trip", 1_795_392_000_000, DataSource.SYNTHETIC, KnotSeed(7), KnotSeed(42),
        listOf(TraitChange(TraitId.GENTLE_RIDE, TraitLevel.VISIBLE, TraitLevel.STEADY,
            "부드러운 주행이 꾸준히 나타나고 있어요. 오늘의 기록도 매듭에 담았어요.")))
    fun <T> ready(value: T) = LoadState.Ready(value, 4)
}
