package dev.chayeon.feature.signature

import dev.chayeon.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SignatureProgressTest {
    @Test fun twoEligibleUnsupportedTripsStillAdvanceProgress() {
        val signature = signature(listOf(trip("first"), trip("second"), trip("excluded", included = false)))
        assertEquals(2, signature.includedTripCount)
        assertTrue(signature.isRecording)
    }

    @Test fun threeEligibleUnsupportedTripsFinishBuildingWithoutPublishingTraits() {
        val signature = signature(listOf(trip("first"), trip("second"), trip("third"), trip("excluded", included = false)))
        assertEquals(3, signature.includedTripCount)
        assertFalse(signature.isRecording)
        assertNull(signature.publicSignature)
        assertTrue(signature.evidence.all { it.score == null && it.availability == TraitAvailability.UNSUPPORTED })
    }

    @Test fun excludedTripsNeverFallBackToPerTraitCountsAndTripIdsCountOnce() {
        val excluded = signature(listOf(trip("excluded", included = false))).copy(evidence =
            signature(listOf(trip("excluded", included = false))).evidence.map { it.copy(validTripCount = 3) })
        assertEquals(0, excluded.includedTripCount)
        assertTrue(excluded.isRecording)
        assertEquals(1, signature(listOf(trip("same"), trip("same"))).includedTripCount)
    }

    @Test fun legacyEvidenceWithoutTripRowsUsesExistingTripCounts() {
        val legacy = signature(emptyList()).copy(evidence = signature(emptyList()).evidence.mapIndexed { index, evidence ->
            evidence.copy(validTripCount = index + 2)
        })
        assertEquals(3, legacy.includedTripCount)
        assertFalse(legacy.isRecording)
    }

    private fun signature(trips: List<TraitTripEvidence>) = MySignature(evidence =
        listOf(TraitId.GENTLE_RIDE, TraitId.SIGNAL_MANNERS).map { trait ->
            TraitEvidence(trait.name, 1, trait, TraitAvailability.UNSUPPORTED, TraitLevel.RECORDING,
                DataSource.SYNTHETIC, EvidenceWindow(0, 1_000_000), 0, 0, emptySet(), emptyList(), recentTrips = trips)
        })

    private fun trip(id: String, included: Boolean = true) = TraitTripEvidence(id, 0, 180_000,
        180_000, 0, 0, included, TraitAvailability.UNSUPPORTED)
}
