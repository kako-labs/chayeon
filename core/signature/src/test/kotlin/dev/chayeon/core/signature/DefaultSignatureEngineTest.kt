package dev.chayeon.core.signature

import dev.chayeon.core.model.*
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

class DefaultSignatureEngineTest {
    private val engine = DefaultSignatureEngine()
    private val end = SignaturePolicy.WINDOW_MILLIS + 1_000_000
    private val window = EvidenceWindow(end - SignaturePolicy.WINDOW_MILLIS, end)

    @Test fun `aggregation applies three trip minimum and all exact ratio boundaries`() {
        for ((passing, expected) in listOf(59 to TraitLevel.RECORDING, 60 to TraitLevel.VISIBLE, 74 to TraitLevel.VISIBLE, 75 to TraitLevel.STEADY, 89 to TraitLevel.STEADY, 90 to TraitLevel.SIGNATURE, 100 to TraitLevel.SIGNATURE)) {
            val trips = (1..3).map { trip("trip-$it", passing = passing) }
            val evidence = engine.aggregate(trips, window, emptyList())
            assertTrue(evidence.all { it.level == expected }, "$passing")
            assertTrue(engine.aggregate(trips.take(2), window, emptyList()).all { it.level == TraitLevel.RECORDING })
        }
    }

    @Test fun `unsupported and insufficient traits keep distinct availability`() {
        val empty = engine.aggregate(emptyList(), window, emptyList())
        assertEquals(12, empty.size)
        assertTrue(empty.all { it.availability == TraitAvailability.UNSUPPORTED && it.metrics.isEmpty() })
        val trips = (1..3).map { trip("$it").copy(traits = listOf(DerivedTraitMetrics(TraitId.EARLY_BRAKE, TraitAvailability.INSUFFICIENT_EVIDENCE, emptyList(), 0, 0))) }
        assertEquals(TraitAvailability.INSUFFICIENT_EVIDENCE, engine.aggregate(trips, window, emptyList()).first { it.trait == TraitId.EARLY_BRAKE }.availability)
    }

    @Test fun `one supported trip among three is still insufficient evidence for that trait`() {
        val trips = listOf(trip("one"), trip("two").copy(traits = emptyList()), trip("three").copy(traits = emptyList()))
        assertTrue(engine.aggregate(trips, window, emptyList()).all { it.level == TraitLevel.RECORDING })
    }

    @Test fun `window is thirty days inclusive and excludes future and crossing trips`() {
        val atStart = trip("start").copy(startedAtEpochMillis = window.fromEpochMillis, endedAtEpochMillis = window.fromEpochMillis + 200_000)
        val atEnd = trip("end").copy(endedAtEpochMillis = window.untilEpochMillis)
        val regular = trip("regular")
        val selected = listOf(atStart, atEnd, regular)
        assertTrue(engine.aggregate(selected, EvidenceWindow(0, end), emptyList()).all { it.validTripCount == 3 })
        val outside = atStart.copy(id = "outside", startedAtEpochMillis = window.fromEpochMillis - 1)
        val future = atEnd.copy(id = "future", endedAtEpochMillis = end + 1)
        assertTrue(engine.aggregate(selected + outside + future, window, emptyList()).all { it.validTripCount == 3 })
    }

    @Test fun `excluded invalid and duplicate trips never add evidence`() {
        val original = trip("same")
        val excluded = original.copy(version = 1, excluded = true)
        val invalid = trip("invalid").copy(eligibility = TripEligibility.INSUFFICIENT_SIGNAL)
        val trips = listOf(original, original, excluded, trip("two"), trip("three"), invalid)
        assertTrue(engine.aggregate(trips, window, emptyList()).all { it.validTripCount == 2 && it.level == TraitLevel.RECORDING })
        assertTrue(engine.aggregate(listOf(original, original.copy(excluded = true)), window, emptyList()).all { it.validTripCount == 0 })
    }

    @Test fun `calculate records changes and exclusion recomputes levels`() {
        val trips = (1..3).map { trip("$it") }
        val first = engine.calculate(MySignature(), trips, VisibilitySettings(), end)
        val second = engine.calculate(first, trips.map { if (it.id == "1") it.copy(excluded = true) else it }, first.visibility, end)
        assertTrue(first.evidence.all { it.level == TraitLevel.SIGNATURE })
        assertTrue(second.evidence.all { it.level == TraitLevel.RECORDING && it.recentChanges.last().previousLevel == TraitLevel.SIGNATURE })
        assertNull(first.publicSignature)
    }

    @Test fun `synthetic contamination is labeled and excluded synthetic trips do not contaminate`() {
        val trips = (1..3).map { trip("$it") }
        val synthetic = trip("synthetic").copy(source = DataSource.SYNTHETIC)
        val visibility = VisibilitySettings(publishSignature = true)
        assertEquals(DataSource.SYNTHETIC, engine.calculate(MySignature(), trips + synthetic, visibility, end).publicSignature!!.source)
        assertEquals(DataSource.VEHICLE, engine.calculate(MySignature(), trips + synthetic.copy(excluded = true), visibility, end).publicSignature!!.source)
    }

    @Test fun `public projection removes hidden traits title hints numeric metrics and seed leakage`() {
        val visible = evidence(TraitId.EARLY_BRAKE, TraitLevel.STEADY)
        val hidden = evidence(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE)
        val settings = VisibilitySettings(visibleTraits = setOf(TraitId.EARLY_BRAKE), publishSignature = true)
        val a = MySignature(version = 1, evidence = listOf(visible), visibility = settings)
        val b = a.copy(knotSeed = KnotSeed(999), evidence = listOf(visible.copy(sampleCount = 900_000, metrics = listOf(PrivateMetric(MetricId.GENTLE_BRAKE_RATIO, .984737, MetricUnit.RATIO))), hidden))
        assertEquals(engine.publicProjection(a), engine.publicProjection(b))
        val projected = engine.publicProjection(b)!!
        assertEquals("여유롭게 멈추는 드라이버", projected.title)
        assertEquals(listOf(PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.STEADY)), projected.traits)
        assertFalse(VisibilitySettings().visibleTraits.contains(TraitId.NIGHT_DRIVE))
    }

    @Test fun `public projection ignores a stale cached publication and never publishes recording`() {
        val old = public(listOf(PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE)))
        val a = MySignature(publicSignature = old, evidence = listOf(evidence(TraitId.GENTLE_RIDE, TraitLevel.RECORDING)))
        assertNull(engine.publicProjection(a))
        assertNull(engine.publicProjection(a.copy(visibility = VisibilitySettings(publishSignature = true))))
    }

    @Test fun `title is independent of input order and uses strongest trait and preference noun`() {
        val traits = listOf(PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.SIGNATURE), PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.STEADY))
        assertEquals("깜빡이를 잊지 않는 밤 드라이버", engine.title(traits))
        assertEquals(engine.title(traits), engine.title(traits.reversed()))
        assertEquals(SignatureLanguage.RECORDING_TITLE, engine.title(emptyList()))
    }

    @Test fun `every modifier noun combination is enumerated for Korean editorial review`() {
        val combinations = SignatureLanguage.titleCombinations()
        assertEquals(34, combinations.size)
        assertEquals(34, combinations.toSet().size)
        combinations.forEach { println(it); assertTrue(it.length in 8..30); assertFalse(it.contains("  ")) }
    }

    @Test fun `chemistry is public only symmetric deterministic and limited to three lines`() {
        val all = public(TraitId.entries.map { PublicTrait(it, TraitLevel.VISIBLE) })
        assertEquals(3, engine.chemistry(all, all).lines.size)
        val brake = public(listOf(PublicTrait(TraitId.EARLY_BRAKE, TraitLevel.VISIBLE)))
        val steady = public(listOf(PublicTrait(TraitId.STEADY_PACE, TraitLevel.VISIBLE)))
        val chemistry = engine.chemistry(brake, steady)
        assertEquals(chemistry, engine.chemistry(steady, brake))
        assertEquals("한 분은 미리 멈추고, 한 분은 흐름을 지켜요", chemistry.lines.single().text)
        assertEquals(Chemistry(), engine.chemistry(brake, public(emptyList())))
        val signal = public(listOf(PublicTrait(TraitId.SIGNAL_MANNERS, TraitLevel.VISIBLE)))
        assertEquals("둘 다 깜빡이를 챙겨요", engine.chemistry(signal, signal).lines.single().text)
    }

    @Test fun `version two knot preserves categorical levels without packing private evidence`() {
        val evidence = TraitId.entries.map { evidence(it, TraitLevel.STEADY) }
        val seed = engine.knot(evidence)
        assertEquals(2, seed.algorithmVersion)
        TraitId.entries.forEach { trait ->
            assertEquals(2L, (seed.value ushr (trait.ordinal * 2)) and 3)
        }
        assertEquals(seed, engine.knot(evidence.reversed()))
        assertEquals(0L, seed.value ushr 24)
        val changed = engine.knot(evidence.map { if (it.trait == TraitId.GENTLE_RIDE) it.copy(level = TraitLevel.SIGNATURE) else it })
        assertEquals(1L, seed.value xor changed.value)
    }

    @Test fun `private metrics counts and timestamps cannot be recovered from the seed`() {
        val original = listOf(evidence(TraitId.GENTLE_RIDE, TraitLevel.STEADY))
        for (value in listOf(.01, .71, .72, .99, 8.0)) {
            val changed = original.map { it.copy(metrics = listOf(PrivateMetric(MetricId.GENTLE_BRAKE_RATIO, value, MetricUnit.RATIO)),
                sampleCount = 12_345, validTripCount = 99, window = EvidenceWindow(1, 9_999)) }
            assertEquals(engine.knot(original), engine.knot(changed))
        }
    }

    @Test fun `recap explains improvements warmly and exclusions create no praise`() {
        val trip = trip("recap")
        val current = MySignature(version = 2, evidence = listOf(evidence(TraitId.EARLY_BRAKE, TraitLevel.VISIBLE)))
        val recap = engine.recap(MySignature(), current, trip)
        assertEquals("여유로운 제동의 매력이 조금 더 쌓였어요", recap.changes.single().explanation)
        assertTrue(recap.included)
        val excluded = engine.recap(MySignature(), current, trip.copy(excluded = true))
        assertFalse(excluded.included)
        assertTrue(excluded.changes.isEmpty())
    }

    @Test fun `belt recap uses a natural Korean habit name`() {
        val current = MySignature(version = 2, evidence = listOf(evidence(TraitId.BELT_FIRST, TraitLevel.VISIBLE)))
        val recap = engine.recap(MySignature(), current, trip("belt"))
        assertEquals("출발 전 벨트 습관의 매력이 조금 더 쌓였어요", recap.changes.single().explanation)
    }

    private fun trip(id: String, passing: Int = 100) = DerivedTripMetrics(id, 0, end - 300_000, end - 100_000, DataSource.VEHICLE, TripEligibility.ELIGIBLE,
        TraitId.entries.map { DerivedTraitMetrics(it, TraitAvailability.AVAILABLE, listOf(PrivateMetric(MetricId.GENTLE_BRAKE_RATIO, passing / 100.0, MetricUnit.RATIO)), 100, passing) },
        driveDurationMillis = 200_000, movementMeters = 3_000.0, speedCoverageRatio = 1.0)
    private fun evidence(trait: TraitId, level: TraitLevel) = TraitEvidence("evidence", 1, trait, TraitAvailability.AVAILABLE, level, DataSource.VEHICLE, window, 3, 100, emptySet(), emptyList())
    private fun public(traits: List<PublicTrait>) = PublicSignature("public", 1, engine.title(traits), traits, KnotSeed(0), DataSource.VEHICLE, if (traits.isEmpty()) EvidenceBucket.RECORDING else EvidenceBucket.GROWING)
}
