package dev.chayeon.data.vehicle

import dev.chayeon.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Only derived domain values cross this boundary. VehicleSample has no storage encoder. */
internal object VehicleStorageCodec {
    private val json = Json { encodeDefaults = true }
    fun trip(value: DerivedTripMetrics): String = json.encodeToString(StoredTrip.from(value))
    fun trip(value: String): DerivedTripMetrics = json.decodeFromString<StoredTrip>(value).domain()
    fun recap(value: TripRecap): String = json.encodeToString(StoredRecap.from(value))
    fun recap(value: String): TripRecap = json.decodeFromString<StoredRecap>(value).domain()
    fun signature(value: MySignature): String = json.encodeToString(StoredSignature.from(value))
    fun signature(value: String): MySignature = json.decodeFromString<StoredSignature>(value).domain()
    fun visibility(value: VisibilitySettings): String = json.encodeToString(value)
    fun visibility(value: String): VisibilitySettings = json.decodeFromString(value)
}

@Serializable
private data class StoredMetric(val id: String, val value: Double, val unit: String) {
    fun domain() = PrivateMetric(MetricId.valueOf(id), value, MetricUnit.valueOf(unit))
    companion object { fun from(v: PrivateMetric) = StoredMetric(v.id.name, v.value, v.unit.name) }
}

@Serializable
private data class StoredTrait(val trait: String, val availability: String, val metrics: List<StoredMetric>, val samples: Int, val passing: Int) {
    fun domain() = DerivedTraitMetrics(TraitId.valueOf(trait), TraitAvailability.valueOf(availability), metrics.map { it.domain() }, samples, passing)
    companion object { fun from(v: DerivedTraitMetrics) = StoredTrait(v.trait.name, v.availability.name, v.metrics.map(StoredMetric::from), v.supportedSampleCount, v.passingSampleCount) }
}

@Serializable
private data class StoredTrip(
    val id: String, val version: Long, val start: Long, val end: Long, val source: DataSource,
    val eligibility: String, val traits: List<StoredTrait>, val duration: Long, val movement: Double?,
    val coverage: Double?, val excluded: Boolean,
) {
    fun domain() = DerivedTripMetrics(id, version, start, end, source, TripEligibility.valueOf(eligibility), traits.map { it.domain() }, duration, movement, coverage, excluded)
    companion object { fun from(v: DerivedTripMetrics) = StoredTrip(v.id, v.version, v.startedAtEpochMillis, v.endedAtEpochMillis, v.source, v.eligibility.name, v.traits.map(StoredTrait::from), v.driveDurationMillis, v.movementMeters, v.speedCoverageRatio, v.excluded) }
}

@Serializable
private data class StoredChange(val trait: TraitId, val previous: TraitLevel, val current: TraitLevel, val text: String) {
    fun domain() = TraitChange(trait, previous, current, text)
    companion object { fun from(v: TraitChange) = StoredChange(v.trait, v.previousLevel, v.currentLevel, v.explanation) }
}

@Serializable
private data class StoredRecap(
    val id: String, val version: Long, val trip: String, val end: Long, val source: DataSource,
    val previous: KnotSeed, val current: KnotSeed, val changes: List<StoredChange>, val included: Boolean, val seen: Boolean,
) {
    fun domain() = TripRecap(id, version, trip, end, source, previous, current, changes.map { it.domain() }, included, seen)
    companion object { fun from(v: TripRecap) = StoredRecap(v.id, v.version, v.tripId, v.completedAtEpochMillis, v.source, v.previousKnot, v.currentKnot, v.changes.map(StoredChange::from), v.included, v.seen) }
}

@Serializable
private data class StoredEvidenceChange(val at: Long, val previous: TraitLevel, val current: TraitLevel) {
    fun domain() = EvidenceChange(at, previous, current)
}

@Serializable
private data class StoredEvidence(
    val id: String, val version: Long, val trait: TraitId, val availability: TraitAvailability, val level: TraitLevel,
    val source: DataSource, val from: Long, val until: Long, val trips: Int, val samples: Int,
    val signals: List<String>, val metrics: List<StoredMetric>, val changes: List<StoredEvidenceChange>,
) {
    fun domain() = TraitEvidence(id, version, trait, availability, level, source, EvidenceWindow(from, until), trips, samples,
        signals.map { SignalKind.valueOf(it) }.toSet(), metrics.map { it.domain() }, changes.map { it.domain() })
    companion object {
        fun from(v: TraitEvidence) = StoredEvidence(v.id, v.version, v.trait, v.availability, v.level, v.source,
            v.window.fromEpochMillis, v.window.untilEpochMillis, v.validTripCount, v.sampleCount,
            v.signals.map { it.name }, v.metrics.map(StoredMetric::from), v.recentChanges.map { StoredEvidenceChange(it.recordedAtEpochMillis, it.previousLevel, it.currentLevel) })
    }
}

@Serializable
private data class StoredSignature(
    val id: String, val version: Long, val publicSignature: PublicSignature?, val knot: KnotSeed,
    val evidence: List<StoredEvidence>, val visibility: VisibilitySettings,
) {
    fun domain() = MySignature(id, version, publicSignature, knot, evidence.map { it.domain() }, visibility)
    companion object { fun from(v: MySignature) = StoredSignature(v.id, v.version, v.publicSignature, v.knotSeed, v.evidence.map(StoredEvidence::from), v.visibility) }
}
