package dev.chayeon.core.signature

import dev.chayeon.core.model.*
import org.junit.Test
import kotlin.test.*

class TraitScoreTest {
    private val engine = DefaultSignatureEngine()
    private val now = SignaturePolicy.WINDOW_MILLIS + 1_000_000L
    private val visible = VisibilitySettings(publishSignature = true, visibleTraits = TraitId.entries.toSet())

    @Test fun `weighted counts rounded once determine every score and stage boundary`() {
        for ((passing, expected) in listOf(59 to TraitLevel.RECORDING, 60 to TraitLevel.VISIBLE,
            74 to TraitLevel.VISIBLE, 75 to TraitLevel.STEADY, 89 to TraitLevel.STEADY, 90 to TraitLevel.SIGNATURE)) {
            val trips = (1..3).map { trip("$it", 100, passing) }
            val result = calculate(trips)
            result.evidence.filter { it.trait.isScored }.forEach {
                assertEquals(passing, it.score)
                assertEquals(expected, it.level)
            }
            assertEquals(result, calculate(trips.reversed()))
            assertTrue(result.publicSignature?.traits.orEmpty().filter { it.trait.isScored }.all { it.score!! >= 60 })
        }
        for ((passing, score, level) in listOf(Triple(119, 60, TraitLevel.VISIBLE), Triple(149, 75, TraitLevel.STEADY), Triple(179, 90, TraitLevel.SIGNATURE))) {
            val evidence = calculate((1..3).map { trip("$it", 200, passing) }).evidence.first()
            assertEquals(score, evidence.score)
            assertEquals(level, evidence.level)
        }
        // An unweighted average of trip percentages would be 67, rather than 100.
        val weighted = calculate(listOf(trip("a", 1, 0), trip("b", 100, 100), trip("c", 100, 100))).evidence.first()
        assertEquals(100, weighted.score)
        assertEquals(200, weighted.passingSampleCount)
        assertEquals(201, weighted.sampleCount)
    }

    @Test fun `unsupported preferences and fewer than three supported trips never get scores`() {
        assertTrue(calculate(emptyList()).evidence.all { it.score == null })
        assertTrue(calculate(listOf(trip("one"), trip("two"))).evidence.all { it.score == null })
        val result = calculate((1..3).map { trip("$it") })
        assertTrue(result.evidence.filterNot { it.trait.isScored }.all { it.score == null })
        assertTrue(result.publicSignature!!.traits.filterNot { it.trait.isScored }.all { it.score == null })
        val unsupported = calculate((1..3).map { trip("$it").copy(traits = emptyList()) })
        assertTrue(unsupported.evidence.all { it.availability == TraitAvailability.UNSUPPORTED && it.score == null })
    }

    @Test fun `excluded and out of window trips cannot alter score and remain private evidence only`() {
        val base = (1..3).map { trip("$it", 100, 80) }
        val excluded = trip("excluded", 100, 0).copy(excluded = true)
        val expired = trip("expired", 100, 0).copy(startedAtEpochMillis = 0, endedAtEpochMillis = 1)
        val future = trip("future", 100, 0).copy(endedAtEpochMillis = now + 1)
        val result = calculate(base + excluded + expired + future)
        assertEquals(calculate(base).publicSignature, result.publicSignature)
        result.evidence.forEach {
            assertEquals(3, it.validTripCount)
            assertEquals(300, it.sampleCount)
            assertEquals(240, it.passingSampleCount)
            assertEquals(4, it.recentTrips.size)
            assertFalse(it.recentTrips.single { trip -> trip.tripId == "excluded" }.included)
        }
    }

    @Test fun `hidden low and inconsistent private evidence cannot publish scores`() {
        val result = calculate((1..3).map { trip("$it", 100, 59) })
        assertTrue(result.publicSignature?.traits.orEmpty().none { it.trait.isScored })
        val high = calculate((1..3).map { trip("$it", 100, 92) })
        val hidden = high.copy(visibility = visible.copy(visibleTraits = setOf(TraitId.NIGHT_DRIVE)))
        assertEquals(listOf(PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE)), engine.publicProjection(hidden)!!.traits)
        assertNull(engine.publicProjection(high.copy(visibility = visible.copy(publishSignature = false))))
        assertNull(engine.publicProjection(high.copy(evidence = high.evidence.filter { it.trait.isScored }.map { it.copy(score = 59) })))
    }

    @Test fun `recent trip evidence is ordered deterministic and contains derived counts only`() {
        val trips = (1..14).map { trip("$it").copy(endedAtEpochMillis = now - it) }
        val evidence = calculate(trips.reversed()).evidence.first()
        assertEquals(trips.map { it.id }, evidence.recentTrips.map { it.tripId })
        assertEquals(12, evidence.recentTrips.filter { it.included }.take(12).size)
        assertEquals(evidence, calculate(trips).evidence.first())
    }

    private fun calculate(trips: List<DerivedTripMetrics>) = engine.calculate(MySignature(), trips, visible, now)
    private fun trip(id: String, supported: Int = 100, passing: Int = supported) = DerivedTripMetrics(
        id, 0, now - 400_000, now - 1_000, DataSource.VEHICLE, TripEligibility.ELIGIBLE,
        TraitId.entries.map { DerivedTraitMetrics(it, TraitAvailability.AVAILABLE, emptyList(), supported, passing) },
        driveDurationMillis = 399_000,
    )
}
