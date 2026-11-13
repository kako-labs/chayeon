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

interface SignatureEngine {
    fun aggregate(
        trips: List<DerivedTripMetrics>,
        window: EvidenceWindow,
        previousEvidence: List<TraitEvidence>,
    ): List<TraitEvidence>

    fun calculate(
        previous: MySignature,
        trips: List<DerivedTripMetrics>,
        visibility: VisibilitySettings,
        evaluatedAtEpochMillis: Long,
    ): MySignature

    fun publicProjection(signature: MySignature): PublicSignature?

    fun chemistry(first: PublicSignature, second: PublicSignature): Chemistry

    /** Deterministic rule-based title. Servers recompute it, so it can never carry free text. */
    fun title(traits: List<PublicTrait>): String

    fun knot(evidence: List<TraitEvidence>): KnotSeed

    fun recap(previous: MySignature, current: MySignature, trip: DerivedTripMetrics): TripRecap
}
