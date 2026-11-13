package dev.chayeon.core.signature

import dev.chayeon.core.model.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class DemoSignatureTest {
    private val engine = DefaultSignatureEngine()
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "fixtures/drive").isDirectory }
    private val visibility = VisibilitySettings(publishSignature = true)

    @Test fun `only unsupported fixture omits signals and every supported drive earns care`() {
        val fullSignals = Json.parseToJsonElement(File(root, "fixtures/drive/care.json").readText())
            .jsonObject.getValue("signals").jsonArray.toSet()
        listOf("gentle", "signal-manners", "care", "early-brake").forEach { name ->
            val signals = Json.parseToJsonElement(File(root, "fixtures/drive/$name.json").readText())
                .jsonObject.getValue("signals").jsonArray.toSet()
            assertEquals(fullSignals, signals, name)
            val trips = (1..3).map { replay(name, it) }
            val signature = engine.calculate(MySignature(), trips, visibility, trips.last().endedAtEpochMillis)
            assertTrue(signature.evidence.none { it.availability == TraitAvailability.UNSUPPORTED }, name)
            assertTrue(signature.publicSignature!!.traits.any { it.trait.ordinal >= TraitId.TIRE_CARE.ordinal }, name)
        }
        val unsupported = (1..3).map { replay("unsupported", it) }
        assertTrue(engine.calculate(MySignature(), unsupported, visibility, unsupported.last().endedAtEpochMillis)
            .evidence.any { it.availability == TraitAvailability.UNSUPPORTED })
    }

    @Test fun `three scenario trips earn distinct titles outside the cast and varied chemistry`() {
        val scenarios = listOf("gentle", "signal-manners", "care").associateWith { scenario ->
            val trips = (1..3).map { replay(scenario, it) }
            assertTrue(trips.all { it.eligibility == TripEligibility.ELIGIBLE })
            val calculated = engine.calculate(MySignature(), trips, visibility, trips.last().endedAtEpochMillis)
            assertTrue(calculated.evidence.none { it.availability == TraitAvailability.UNSUPPORTED }, scenario)
            val signature = calculated.publicSignature!!
            assertTrue(signature.traits.any { it.trait.ordinal >= TraitId.TIRE_CARE.ordinal }, scenario)
            println("DEMO_TRAITS $scenario: ${signature.traits}")
            signature
        }
        val expected = Json.parseToJsonElement(File(root, "core/signature/src/test/resources/demo-results.json").readText()).jsonArray
        expected.forEach { entry ->
            val obj = entry.jsonObject
            val actual = scenarios.getValue(obj.getValue("id").jsonPrimitive.content)
            assertEquals(obj.getValue("title").jsonPrimitive.content, actual.title)
            assertEquals(Json.decodeFromJsonElement<List<PublicTrait>>(obj.getValue("traits")), actual.traits)
        }
        assertEquals(3, scenarios.values.map { it.title }.toSet().size)
        val cast = Json.parseToJsonElement(File(root, "core/signature/src/test/resources/demo-cast.json").readText()).jsonArray
        val castSignatures = cast.associate { entry ->
            val obj = entry.jsonObject
            val traits = Json.decodeFromJsonElement<List<PublicTrait>>(obj.getValue("traits"))
            val title = engine.title(traits)
            assertEquals(obj.getValue("title").jsonPrimitive.content, title)
            obj.getValue("id").jsonPrimitive.content to PublicSignature("cast", 1, title, traits, KnotSeed(1), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
        }
        assertEquals(5, castSignatures.values.map { it.title }.toSet().size)
        assertTrue(castSignatures.values.none { peer -> scenarios.values.any { it.title == peer.title } }, "scenarios=${scenarios.mapValues { it.value.title }}")
        val typical = scenarios.getValue("care")
        val counts = castSignatures.mapValues { engine.chemistry(typical, it.value).lines.size }
        assertTrue(1 in counts.values && 3 in counts.values, "counts=$counts traits=${typical.traits}")
        scenarios.forEach { (name, signature) -> println("DEMO $name × 3: ${signature.title}") }
        castSignatures.forEach { (id, signature) -> println("CAST $id: ${signature.title}; chemistry=${counts[id]}") }
    }

    @Test fun `first signature belongs only to the first publishable eligible recap`() {
        val trips = (1..4).map { replay("signal-manners", it) }
        var previous = MySignature()
        trips.forEachIndexed { index, trip ->
            val current = engine.calculate(previous, trips.take(index + 1), visibility, trip.endedAtEpochMillis)
            val recap = engine.recap(previous, current, trip)
            assertEquals(index == 2, recap.firstSignature)
            if (index == 2) {
                assertTrue(recap.changes.isNotEmpty())
                assertTrue(recap.changes.all { it.explanation.endsWith("의 매력이 첫 시그니처에 담겼어요") })
                assertFalse(engine.recap(previous, current, trip.copy(excluded = true)).firstSignature)
                assertFalse(engine.recap(previous, current, trip.copy(eligibility = TripEligibility.TOO_SHORT)).firstSignature)
            }
            previous = current
        }
        val private = engine.calculate(MySignature(), trips, VisibilitySettings(), trips.last().endedAtEpochMillis)
        assertFalse(engine.recap(MySignature(), private, trips.last()).firstSignature)
    }

    @Test fun `chemistry orders style before taste before care and stays symmetric for every pair`() {
        val traits = listOf(TraitId.TIRE_CARE, TraitId.LONG_ROAD, TraitId.GENTLE_RIDE)
        fun signature(ids: List<TraitId>) = PublicSignature("test", 1, "", ids.map { PublicTrait(it, TraitLevel.SIGNATURE) }, KnotSeed(0), DataSource.SYNTHETIC, EvidenceBucket.ESTABLISHED)
        assertEquals(listOf(TraitId.GENTLE_RIDE, TraitId.LONG_ROAD, TraitId.TIRE_CARE), engine.chemistry(signature(traits), signature(traits.reversed())).lines.map { it.firstTrait })
        val mixed = engine.chemistry(signature(listOf(TraitId.NIGHT_DRIVE, TraitId.GENTLE_RIDE)), signature(listOf(TraitId.NIGHT_DRIVE, TraitId.CABIN_CARE)))
        assertEquals(TraitId.NIGHT_DRIVE, mixed.lines.first().firstTrait)
        assertEquals(TraitId.CABIN_CARE, mixed.lines.last().secondTrait)
        TraitId.entries.forEach { a -> TraitId.entries.forEach { b ->
            assertEquals(engine.chemistry(signature(listOf(a)), signature(listOf(b))), engine.chemistry(signature(listOf(b)), signature(listOf(a))))
        } }
        assertEquals("밤길을 좋아하는 드라이버", engine.title(listOf(PublicTrait(TraitId.NIGHT_DRIVE, TraitLevel.SIGNATURE))))
        assertEquals("먼 길을 즐기는 드라이버", engine.title(listOf(PublicTrait(TraitId.LONG_ROAD, TraitLevel.SIGNATURE))))
    }

    // Mirrors the console's 200 ms sampling of the shared JSON, including interpolation and defaults.
    private fun replay(name: String, index: Int): DerivedTripMetrics {
        val asset = Json.parseToJsonElement(File(root, "fixtures/drive/$name.json").readText()).jsonObject
        assertEquals("SYNTHETIC", asset.getValue("source").jsonPrimitive.content)
        val duration = asset.getValue("durationMillis").jsonPrimitive.long
        val frames = asset.getValue("frames").jsonArray.map { it.jsonObject }
        val signals = asset.getValue("signals").jsonArray.map { it.jsonPrimitive.content }.toSet()
        fun JsonObject.number(key: String, default: Double = 0.0) = get(key)?.jsonPrimitive?.double ?: default
        fun JsonObject.flag(key: String, default: Boolean = false) = get(key)?.jsonPrimitive?.boolean ?: default
        val end = 10_000_000L + index * (duration + 1)
        val accumulator = TripAccumulator("$name-$index", end - duration - 1, DataSource.SYNTHETIC)
        if ("belt" in signals) accumulator.accept(VehicleSample.SeatBelt(0, frames.first().flag("belt", true)))
        accumulator.accept(VehicleSample.Gear(1, Gear.DRIVE))
        for (time in 1..duration step 200) {
            val before = frames.last { it.getValue("atMillis").jsonPrimitive.long <= time }
            val after = frames.firstOrNull { it.getValue("atMillis").jsonPrimitive.long > time } ?: before
            val start = before.getValue("atMillis").jsonPrimitive.long
            val finish = after.getValue("atMillis").jsonPrimitive.long
            val fraction = if (finish == start) 0.0 else (time - start).toDouble() / (finish - start)
            val speed = before.number("speedKph") + fraction * (after.number("speedKph") - before.number("speedKph"))
            if ("speed" in signals) accumulator.accept(VehicleSample.Speed(time, speed / 3.6))
            if ("brake" in signals) accumulator.accept(VehicleSample.Brake(time, before.flag("brake")))
            if ("steering" in signals) accumulator.accept(VehicleSample.Steering(time, before.number("steering")))
            if ("indicator" in signals) accumulator.accept(VehicleSample.TurnIndicator(time, before.flag("indicator")))
            if ("cruise" in signals) accumulator.accept(VehicleSample.Cruise(time, before.flag("cruise")))
            if ("clock" in signals) accumulator.accept(VehicleSample.Clock(time, before.number("localHour", 14.0).toInt()))
            if (time % 1_000 == 1L) {
                if ("tire" in signals) {
                    accumulator.accept(VehicleSample.TirePressure(time, before.number("tireKpa", 240.0), before.number("tireLowKpa", 200.0)))
                    accumulator.accept(VehicleSample.TireWarning(time, before.number("tireKpa", 240.0) <= before.number("tireLowKpa", 200.0)))
                }
                if ("energy" in signals) accumulator.accept(VehicleSample.BatteryLevel(time, before.number("energyPercent", 65.0)))
                if ("chargeLimit" in signals) accumulator.accept(VehicleSample.ChargeLimit(time, before.number("chargeLimitPercent", 80.0)))
                if ("fluid" in signals) {
                    accumulator.accept(VehicleSample.OilWarning(time, before.flag("fluidWarning")))
                    accumulator.accept(VehicleSample.BrakeFluidWarning(time, before.flag("fluidWarning")))
                    accumulator.accept(VehicleSample.WasherFluidWarning(time, before.flag("fluidWarning")))
                }
                if ("airCleaning" in signals) accumulator.accept(VehicleSample.AirCleaning(time, before.flag("airCleaning", true)))
            }
        }
        accumulator.accept(VehicleSample.Gear(duration + 1, Gear.PARK))
        return accumulator.finish(end)
    }
}
