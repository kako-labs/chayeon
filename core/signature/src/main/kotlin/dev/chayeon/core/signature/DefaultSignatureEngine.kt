package dev.chayeon.core.signature

import dev.chayeon.core.model.Chemistry
import dev.chayeon.core.model.DerivedTripMetrics
import dev.chayeon.core.model.EvidenceWindow
import dev.chayeon.core.model.KnotSeed
import dev.chayeon.core.model.MySignature
import dev.chayeon.core.model.PublicSignature
import dev.chayeon.core.model.PublicTrait
import dev.chayeon.core.model.TraitEvidence
import dev.chayeon.core.model.TripRecap
import dev.chayeon.core.model.VisibilitySettings

/** Scaffold with neutral results. The signature track replaces this body. */
class DefaultSignatureEngine : SignatureEngine {
    override fun aggregate(
        trips: List<DerivedTripMetrics>,
        window: EvidenceWindow,
        previousEvidence: List<TraitEvidence>,
    ): List<TraitEvidence> = emptyList()

    override fun calculate(
        previous: MySignature,
        trips: List<DerivedTripMetrics>,
        visibility: VisibilitySettings,
        evaluatedAtEpochMillis: Long,
    ): MySignature = previous.copy(visibility = visibility)

    override fun publicProjection(signature: MySignature): PublicSignature? = signature.publicSignature

    override fun chemistry(first: PublicSignature, second: PublicSignature): Chemistry = Chemistry()

    override fun title(traits: List<PublicTrait>): String = "시그니처를 만드는 중이에요"

    override fun knot(evidence: List<TraitEvidence>): KnotSeed = KnotSeed(0)

    override fun recap(previous: MySignature, current: MySignature, trip: DerivedTripMetrics): TripRecap = TripRecap(
        id = trip.id,
        version = current.version,
        tripId = trip.id,
        completedAtEpochMillis = trip.endedAtEpochMillis,
        source = trip.source,
        previousKnot = previous.knotSeed,
        currentKnot = current.knotSeed,
        changes = emptyList(),
    )
}
