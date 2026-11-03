package dev.chayeon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TraitId {
    GENTLE_RIDE, EARLY_BRAKE, SIGNAL_MANNERS, STEADY_PACE, BELT_FIRST, LONG_ROAD,
    NIGHT_DRIVE, TIRE_CARE, ENERGY_PLANNER, BATTERY_KIND, FLUID_READY, CABIN_CARE,
}

@Serializable
enum class TraitLevel { RECORDING, VISIBLE, STEADY, SIGNATURE }

@Serializable
enum class TraitAvailability { AVAILABLE, INSUFFICIENT_EVIDENCE, UNSUPPORTED }

@Serializable
enum class EvidenceBucket { RECORDING, GROWING, ESTABLISHED }

@Serializable
data class KnotSeed(val value: Long, val algorithmVersion: Int = 1)

@Serializable
data class PublicTrait(val trait: TraitId, val level: TraitLevel) {
    init {
        require(level != TraitLevel.RECORDING) { "Recording traits are private" }
    }
}

/** This is the only signature projection accepted by the social API. */
@Serializable
data class PublicSignature(
    override val id: String,
    override val version: Long,
    val title: String,
    val traits: List<PublicTrait>,
    val knotSeed: KnotSeed,
    val source: DataSource,
    val evidenceBucket: EvidenceBucket,
) : VersionedEntity {
    init {
        require(evidenceBucket != EvidenceBucket.RECORDING || traits.isEmpty()) {
            "Recording signatures cannot publish traits"
        }
    }
}

@Serializable
enum class ChemistryKind { SHARED, COMPLEMENTARY }

@Serializable
data class ChemistryLine(
    val kind: ChemistryKind,
    val firstTrait: TraitId,
    val secondTrait: TraitId,
    val text: String,
)

@Serializable
data class Chemistry(val lines: List<ChemistryLine> = emptyList())

@Serializable
data class VisibilitySettings(
    override val id: String = "signature-visibility",
    override val version: Long = 0,
    val visibleTraits: Set<TraitId> = TraitId.entries.filterNot { it == TraitId.NIGHT_DRIVE }.toSet(),
    val publishSignature: Boolean = false,
) : VersionedEntity

enum class SignalKind {
    SPEED, BRAKE, STEERING_MAGNITUDE, TURN_INDICATOR, CRUISE, SEAT_BELT, GEAR,
    TRIP_LENGTH, CLOCK, TIRE_PRESSURE, TIRE_WARNING, FUEL_LEVEL, BATTERY_LEVEL,
    CHARGE_LIMIT, OIL_WARNING, BRAKE_FLUID_WARNING, WASHER_FLUID_WARNING, AIR_CLEANING,
}

enum class MetricId {
    ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES, GENTLE_BRAKE_RATIO, SIGNAL_USAGE_RATIO,
    CRUISE_VARIATION_COEFFICIENT, CRUISE_USAGE_RATIO, BELT_BEFORE_DRIVE_RATIO,
    LONG_TRIP_RATIO, NIGHT_TRIP_RATIO, HEALTHY_TIRE_RATIO, ENERGY_RESERVE_RATIO,
    KIND_CHARGE_LIMIT_RATIO, NO_FLUID_WARNING_RATIO, AIR_CLEANING_RATIO,
}

enum class MetricUnit { RATIO, EVENTS_PER_TEN_MINUTES, COEFFICIENT }

/** Derived aggregates only. Neither this type nor its containers are wire serializable. */
data class PrivateMetric(val id: MetricId, val value: Double, val unit: MetricUnit)

data class EvidenceWindow(val fromEpochMillis: Long, val untilEpochMillis: Long)

data class EvidenceChange(
    val recordedAtEpochMillis: Long,
    val previousLevel: TraitLevel,
    val currentLevel: TraitLevel,
)

data class TraitEvidence(
    override val id: String,
    override val version: Long,
    val trait: TraitId,
    val availability: TraitAvailability,
    val level: TraitLevel,
    val source: DataSource,
    val window: EvidenceWindow,
    val validTripCount: Int,
    val sampleCount: Int,
    val signals: Set<SignalKind>,
    val metrics: List<PrivateMetric>,
    val recentChanges: List<EvidenceChange> = emptyList(),
) : VersionedEntity {
    init {
        require(availability != TraitAvailability.UNSUPPORTED || metrics.isEmpty()) {
            "Unsupported traits must not contain numeric metrics"
        }
    }
}

data class MySignature(
    override val id: String = "my-signature",
    override val version: Long = 0,
    val publicSignature: PublicSignature? = null,
    val knotSeed: KnotSeed = KnotSeed(0),
    val evidence: List<TraitEvidence> = emptyList(),
    val visibility: VisibilitySettings = VisibilitySettings(),
) : VersionedEntity

data class TraitChange(
    val trait: TraitId,
    val previousLevel: TraitLevel,
    val currentLevel: TraitLevel,
    val explanation: String,
)

data class TripRecap(
    override val id: String,
    override val version: Long,
    val tripId: String,
    val completedAtEpochMillis: Long,
    val source: DataSource,
    val previousKnot: KnotSeed,
    val currentKnot: KnotSeed,
    val changes: List<TraitChange>,
    val included: Boolean = true,
    val seen: Boolean = false,
) : VersionedEntity

/** Passed from the vehicle worker to the signature worker; no raw signal stream is retained. */
data class DerivedTripMetrics(
    override val id: String,
    override val version: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val source: DataSource,
    val eligibility: TripEligibility,
    val traits: List<DerivedTraitMetrics>,
    val driveDurationMillis: Long = 0,
    val movementMeters: Double? = null,
    val speedCoverageRatio: Double? = null,
    val excluded: Boolean = false,
) : VersionedEntity {
    override fun toString(): String = "DerivedTripMetrics(id=$id, version=$version, source=$source)"
}

enum class TripEligibility { ELIGIBLE, TOO_SHORT, INSUFFICIENT_MOVEMENT, INSUFFICIENT_SIGNAL }

data class DerivedTraitMetrics(
    val trait: TraitId,
    val availability: TraitAvailability,
    val metrics: List<PrivateMetric>,
    val supportedSampleCount: Int,
    val passingSampleCount: Int,
) {
    init {
        require(availability != TraitAvailability.UNSUPPORTED || metrics.isEmpty()) {
            "Unsupported signals are not zero-valued measurements"
        }
    }
}
