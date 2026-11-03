package dev.chayeon.core.signature

import dev.chayeon.core.model.*
import org.junit.Test
import kotlin.test.*

class TripAccumulatorTest {
    @Test fun `all twelve traits have passing observed evidence`() {
        val trip = drive(seconds = 2_000)
        assertEquals(TripEligibility.ELIGIBLE, trip.eligibility)
        assertEquals(12, trip.traits.size)
        trip.traits.forEach {
            assertEquals(TraitAvailability.AVAILABLE, it.availability, it.trait.name)
            assertEquals(it.supportedSampleCount, it.passingSampleCount, it.trait.name)
            assertTrue(it.metrics.isNotEmpty(), it.trait.name)
        }
    }

    @Test fun `all traits can fail without inventing unsupported values`() {
        val trip = drive(seconds = 210, good = false)
        trip.traits.filterNot { it.trait == TraitId.STEADY_PACE }.forEach {
            assertEquals(TraitAvailability.AVAILABLE, it.availability, it.trait.name)
            assertEquals(0, it.passingSampleCount, it.trait.name)
            assertTrue(it.metrics.isNotEmpty(), it.trait.name)
        }
        val steady = drive(seconds = 210, cruise = false).trait(TraitId.STEADY_PACE)
        assertEquals(TraitAvailability.AVAILABLE, steady.availability)
        assertEquals(0, steady.passingSampleCount)
    }

    @Test fun `no signals means twelve unsupported traits and no numeric evidence`() {
        val trip = TripAccumulator("empty", 0, DataSource.VEHICLE).finish(0)
        trip.traits.forEach {
            assertEquals(TraitAvailability.UNSUPPORTED, it.availability, it.trait.name)
            assertEquals(0, it.supportedSampleCount)
            assertTrue(it.metrics.isEmpty())
        }
        assertNull(trip.movementMeters)
        assertNull(trip.speedCoverageRatio)
    }

    @Test fun `midstream errors invalidate previous observations instead of producing zero`() {
        val accumulator = basic()
        accumulator.accept(VehicleSample.ChargeLimit(1, 80.0))
        accumulator.accept(VehicleSample.Unsupported(2, SignalKind.CHARGE_LIMIT))
        accumulator.accept(VehicleSample.ChargeLimit(3, 80.0))
        val result = accumulator.finish(3).trait(TraitId.BATTERY_KIND)
        assertEquals(TraitAvailability.UNSUPPORTED, result.availability)
        assertTrue(result.metrics.isEmpty())
    }

    @Test fun `duplicates and reversed samples do not alter accepted evidence`() {
        fun recorded(extra: Boolean): DerivedTripMetrics {
            val a = basic()
            for (second in 1..210) {
                val time = second * 1_000L
                a.accept(VehicleSample.Speed(time, 12.0))
                if (extra) {
                    a.accept(VehicleSample.Speed(time, 99.0))
                    a.accept(VehicleSample.Speed(time - 1, 0.0))
                }
            }
            return a.finish(210_000)
        }
        assertEquals(recorded(false), recorded(true))
    }

    @Test fun `same timestamp on distinct fields is accepted`() {
        val a = basic()
        a.accept(VehicleSample.ChargeLimit(1, 80.0))
        a.accept(VehicleSample.BatteryLevel(1, 60.0))
        val trip = a.finish(1)
        assertEquals(1, trip.trait(TraitId.BATTERY_KIND).supportedSampleCount)
        assertEquals(1, trip.trait(TraitId.ENERGY_PLANNER).supportedSampleCount)
    }

    @Test fun `a gap creates no fictional movement or acceleration`() {
        val a = basic()
        a.accept(VehicleSample.Speed(1_000, 10.0))
        a.accept(VehicleSample.Speed(101_000, 40.0))
        a.accept(VehicleSample.Speed(102_000, 40.0))
        val trip = a.finish(102_000)
        assertEquals(40.0, trip.movementMeters)
        assertEquals(0.0, trip.trait(TraitId.GENTLE_RIDE).metrics.single().value)
        assertTrue(trip.speedCoverageRatio!! < .02)
    }

    @Test fun `validity boundaries use drive time movement and coverage`() {
        assertEquals(TripEligibility.TOO_SHORT, speedTrip(179, 20.0).eligibility)
        assertEquals(TripEligibility.INSUFFICIENT_MOVEMENT, speedTrip(180, 10.0).eligibility)
        assertEquals(TripEligibility.ELIGIBLE, speedTrip(180, 20.0).eligibility)
        val a = basic()
        for (second in 1..150) a.accept(VehicleSample.Speed(second * 1_000L, 20.0))
        a.accept(VehicleSample.Gear(200_000, Gear.PARK))
        assertEquals(TripEligibility.INSUFFICIENT_SIGNAL, a.finish(200_000).eligibility)
    }

    @Test fun `exactly eighty percent coverage is eligible and just below is insufficient`() {
        fun coverage(lastSecond: Int): DerivedTripMetrics {
            val a = basic()
            for (second in 0..lastSecond) a.accept(VehicleSample.Speed(1 + second * 1_000L, 20.0))
            a.accept(VehicleSample.Gear(200_001, Gear.PARK))
            return a.finish(200_001)
        }
        assertEquals(.8, coverage(160).speedCoverageRatio)
        assertEquals(TripEligibility.ELIGIBLE, coverage(160).eligibility)
        assertEquals(TripEligibility.INSUFFICIENT_SIGNAL, coverage(159).eligibility)
    }

    @Test fun `energy charge and long distance thresholds are exact`() {
        val a = basic()
        a.accept(VehicleSample.FuelLevel(1, 15.0))
        a.accept(VehicleSample.ChargeLimit(1, 90.0))
        val trip = a.finish(1)
        assertEquals(0, trip.trait(TraitId.ENERGY_PLANNER).passingSampleCount)
        assertEquals(1, trip.trait(TraitId.BATTERY_KIND).passingSampleCount)
        assertEquals(1, speedTrip(1_500, 20.0).trait(TraitId.LONG_ROAD).passingSampleCount)
        assertEquals(0, speedTrip(1_499, 20.0).trait(TraitId.LONG_ROAD).passingSampleCount)
    }

    @Test fun `alternating harsh acceleration never becomes gentle as duration grows`() {
        val a = basic()
        repeat(2_000) { index -> a.accept(VehicleSample.Speed((index + 1) * 1_000L, if (index % 2 == 0) 30.0 else 0.0)) }
        val gentle = a.finish(2_000_000).trait(TraitId.GENTLE_RIDE)
        assertEquals(0, gentle.passingSampleCount)
        assertTrue(gentle.metrics.single().value > 100)
    }

    @Test fun `unsupported energy source cannot contaminate a supported alternative`() {
        val a = basic()
        a.accept(VehicleSample.FuelLevel(1, 0.0))
        a.accept(VehicleSample.Unsupported(2, SignalKind.FUEL_LEVEL))
        a.accept(VehicleSample.BatteryLevel(2, 80.0))
        val energy = a.finish(2).trait(TraitId.ENERGY_PLANNER)
        assertEquals(1, energy.supportedSampleCount)
        assertEquals(1, energy.passingSampleCount)
        assertEquals(1.0, energy.metrics.single().value)
    }

    @Test fun `an interrupted brake segment does not become a successful segment`() {
        val a = basic()
        a.accept(VehicleSample.Brake(1, true))
        a.accept(VehicleSample.Speed(1_000, 20.0))
        a.accept(VehicleSample.Speed(2_000, 15.0))
        a.accept(VehicleSample.Speed(100_000, 10.0))
        a.accept(VehicleSample.Speed(101_000, 10.0))
        a.accept(VehicleSample.Brake(102_000, false))
        val brake = a.finish(102_000).trait(TraitId.EARLY_BRAKE)
        assertEquals(TraitAvailability.INSUFFICIENT_EVIDENCE, brake.availability)
        assertTrue(brake.metrics.isEmpty())
    }

    @Test fun `five second speed intervals cannot claim complete reception`() {
        val a = basic()
        for (second in 0..200 step 5) a.accept(VehicleSample.Speed(1 + second * 1_000L, 20.0))
        val trip = a.finish(200_001)
        assertEquals(.2, trip.speedCoverageRatio)
        assertEquals(4_000.0, trip.movementMeters)
        assertEquals(TripEligibility.INSUFFICIENT_SIGNAL, trip.eligibility)
    }

    @Test fun `tire cohorts count each fresh pair once regardless of health order`() {
        for (healthyFirst in listOf(true, false)) {
            for (pressureFirst in listOf(true, false)) {
                val a = basic()
                listOf(healthyFirst, !healthyFirst).forEachIndexed { index, healthy ->
                    val time = 10L + index * 10
                    val pressure = VehicleSample.TirePressure(time, if (healthy) 230.0 else 190.0, 200.0)
                    val warning = VehicleSample.TireWarning(time + 1, !healthy)
                    if (pressureFirst) {
                        a.accept(pressure)
                        a.accept(warning)
                    } else {
                        a.accept(warning.copy(elapsedRealtimeMillis = time))
                        a.accept(pressure.copy(elapsedRealtimeMillis = time + 1))
                    }
                }
                val tire = a.finish(21).trait(TraitId.TIRE_CARE)
                assertEquals(2, tire.supportedSampleCount, "$healthyFirst $pressureFirst")
                assertEquals(1, tire.passingSampleCount)
                assertEquals(.5, tire.metrics.single().value)
            }
        }
    }

    @Test fun `fluid cohorts count each complete set once regardless of health or constituent order`() {
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        for (healthyFirst in listOf(true, false)) {
            for (order in orders) {
                val a = basic()
                listOf(healthyFirst, !healthyFirst).forEachIndexed { cohort, healthy ->
                    order.forEachIndexed { offset, kind ->
                        val time = 10L + cohort * 10 + offset
                        a.accept(when (kind) {
                            0 -> VehicleSample.OilWarning(time, !healthy)
                            1 -> VehicleSample.BrakeFluidWarning(time, !healthy)
                            else -> VehicleSample.WasherFluidWarning(time, !healthy)
                        })
                    }
                }
                val fluid = a.finish(22).trait(TraitId.FLUID_READY)
                assertEquals(2, fluid.supportedSampleCount, "$healthyFirst $order")
                assertEquals(1, fluid.passingSampleCount)
                assertEquals(.5, fluid.metrics.single().value)
            }
        }
    }

    @Test fun `missing any care constituent remains unsupported`() {
        val samples = listOf<VehicleSample>(VehicleSample.TirePressure(10, 230.0, 200.0), VehicleSample.TireWarning(10, false),
            VehicleSample.OilWarning(10, false), VehicleSample.BrakeFluidWarning(10, false), VehicleSample.WasherFluidWarning(10, false))
        samples.indices.forEach { missing ->
            val a = basic()
            samples.filterIndexed { index, _ -> index != missing }.forEach(a::accept)
            val trait = if (missing < 2) TraitId.TIRE_CARE else TraitId.FLUID_READY
            val evidence = a.finish(10).trait(trait)
            assertEquals(TraitAvailability.UNSUPPORTED, evidence.availability, "$missing")
            assertTrue(evidence.metrics.isEmpty())
        }
    }

    @Test fun `partial care updates never reuse an already consumed constituent`() {
        val a = basic()
        a.accept(VehicleSample.TirePressure(10, 230.0, 200.0))
        a.accept(VehicleSample.TireWarning(11, false))
        a.accept(VehicleSample.TirePressure(20, 190.0, 200.0))
        a.accept(VehicleSample.TirePressure(21, 190.0, 200.0))
        a.accept(VehicleSample.OilWarning(21, false))
        a.accept(VehicleSample.BrakeFluidWarning(21, false))
        a.accept(VehicleSample.WasherFluidWarning(21, false))
        a.accept(VehicleSample.OilWarning(22, true))
        a.accept(VehicleSample.BrakeFluidWarning(22, true))
        val trip = a.finish(22)
        assertEquals(1, trip.trait(TraitId.TIRE_CARE).supportedSampleCount)
        assertEquals(1.0, trip.trait(TraitId.TIRE_CARE).metrics.single().value)
        assertEquals(1, trip.trait(TraitId.FLUID_READY).supportedSampleCount)
        assertEquals(1.0, trip.trait(TraitId.FLUID_READY).metrics.single().value)
    }

    @Test fun `stale care constituents wait for a fresh complete cohort`() {
        val a = basic()
        a.accept(VehicleSample.TirePressure(10, 230.0, 200.0))
        a.accept(VehicleSample.TireWarning(6_000, false))
        a.accept(VehicleSample.OilWarning(6_000, false))
        a.accept(VehicleSample.BrakeFluidWarning(6_001, false))
        a.accept(VehicleSample.WasherFluidWarning(12_000, false))
        val trip = a.finish(12_000)
        for (trait in listOf(TraitId.TIRE_CARE, TraitId.FLUID_READY)) {
            assertEquals(TraitAvailability.INSUFFICIENT_EVIDENCE, trip.trait(trait).availability)
            assertEquals(0, trip.trait(trait).supportedSampleCount)
        }
    }

    @Test fun `D duration excludes other gears and parked speed cannot bridge into driving`() {
        val a = TripAccumulator("gear", 0, DataSource.VEHICLE)
        a.accept(VehicleSample.Gear(0, Gear.PARK))
        a.accept(VehicleSample.Speed(1_000, 50.0))
        a.accept(VehicleSample.Gear(2_000, Gear.DRIVE))
        a.accept(VehicleSample.Speed(3_000, 20.0))
        a.accept(VehicleSample.Speed(4_000, 20.0))
        a.accept(VehicleSample.Gear(5_000, Gear.NEUTRAL))
        a.accept(VehicleSample.Speed(10_000, 50.0))
        val trip = a.finish(10_000)
        assertEquals(3_000, trip.driveDurationMillis)
        assertEquals(20.0, trip.movementMeters)
    }

    @Test fun `wall clock jumps never create valid drive time`() {
        val a = basic()
        for (second in 1..210) a.accept(VehicleSample.Speed(second * 1_000L, 20.0))
        assertEquals(TripEligibility.INSUFFICIENT_SIGNAL, a.finish(86_400_000).eligibility)
    }

    @Test fun `belt must be fastened strictly before changing to D`() {
        fun result(beltTime: Long): DerivedTraitMetrics {
            val a = TripAccumulator("belt", 0, DataSource.VEHICLE)
            a.accept(VehicleSample.SeatBelt(beltTime, true))
            a.accept(VehicleSample.Gear(1_000, Gear.DRIVE))
            return a.finish(1_000).trait(TraitId.BELT_FIRST)
        }
        assertEquals(1, result(999).passingSampleCount)
        assertEquals(0, result(1_000).passingSampleCount)
    }

    @Test fun `steering direction is ignored and low speed maneuvers do not create turns`() {
        fun turn(degrees: Double, speed: Double): DerivedTraitMetrics {
            val a = basic()
            a.accept(VehicleSample.Speed(1_000, speed))
            a.accept(VehicleSample.TurnIndicator(1_000, true))
            a.accept(VehicleSample.Steering(2_000, degrees))
            a.accept(VehicleSample.Steering(3_000, 0.0))
            return a.finish(3_000).trait(TraitId.SIGNAL_MANNERS)
        }
        assertEquals(turn(30.0, 10.0), turn(-30.0, 10.0))
        assertEquals(1, turn(-30.0, 10.0).passingSampleCount)
        assertEquals(TraitAvailability.INSUFFICIENT_EVIDENCE, turn(30.0, 1.0).availability)
    }

    @Test fun `indicator may arrive three seconds after turn starts including after turn ends`() {
        val a = basic()
        a.accept(VehicleSample.Speed(1_000, 10.0))
        a.accept(VehicleSample.Steering(2_000, 30.0))
        a.accept(VehicleSample.Steering(3_000, 0.0))
        a.accept(VehicleSample.TurnIndicator(5_000, true))
        assertEquals(1, a.finish(5_000).trait(TraitId.SIGNAL_MANNERS).passingSampleCount)
    }

    @Test fun `missing brake and turn events are insufficient rather than perfect ratios`() {
        val a = basic()
        a.accept(VehicleSample.Brake(1, false))
        a.accept(VehicleSample.TurnIndicator(1, false))
        a.accept(VehicleSample.Steering(1, 0.0))
        a.accept(VehicleSample.Speed(1, 10.0))
        val trip = a.finish(1)
        for (trait in listOf(TraitId.EARLY_BRAKE, TraitId.SIGNAL_MANNERS)) {
            assertEquals(TraitAvailability.INSUFFICIENT_EVIDENCE, trip.trait(trait).availability)
            assertTrue(trip.trait(trait).metrics.isEmpty())
        }
    }

    @Test fun `night boundaries include 21 and exclude 05`() {
        for ((hour, expected) in listOf(20 to 0, 21 to 1, 0 to 1, 4 to 1, 5 to 0)) {
            val a = basic()
            a.accept(VehicleSample.Clock(1, hour))
            a.accept(VehicleSample.Speed(1_001, 10.0))
            assertEquals(expected, a.finish(1_001).trait(TraitId.NIGHT_DRIVE).passingSampleCount, "$hour")
        }
    }

    @Test fun `millions of samples keep a fixed amount of retained state`() {
        val a = basic()
        fun retainedArrayBytes(): Int = TripAccumulator::class.java.declaredFields.sumOf { field ->
            field.isAccessible = true
            when (val value = field.get(a)) {
                is IntArray -> value.size * Int.SIZE_BYTES
                is LongArray -> value.size * Long.SIZE_BYTES
                is BooleanArray -> value.size
                else -> 0
            }
        }
        val baselineBytes = retainedArrayBytes()
        repeat(2_000_000) { index -> a.accept(VehicleSample.Speed((index + 1).toLong(), 12.0)) }
        assertEquals(baselineBytes, retainedArrayBytes())
        val fields = TripAccumulator::class.java.declaredFields.map { field -> field.isAccessible = true; field.get(a) }
        assertFalse(fields.any { it is Collection<*> || it is Map<*, *> || it is VehicleSample })
        assertEquals(2, fields.filterIsInstance<LongArray>().size + fields.filterIsInstance<BooleanArray>().size)
        assertTrue(fields.filterIsInstance<IntArray>().all { it.size <= SignalKind.entries.size })
        assertEquals(TripEligibility.ELIGIBLE, a.finish(2_000_000).eligibility)
    }

    @Test fun `finish is idempotent and later samples are rejected`() {
        val a = basic()
        val trip = a.finish(1)
        assertSame(trip, a.finish(2))
        assertFailsWith<IllegalStateException> { a.accept(VehicleSample.Speed(3, 0.0)) }
    }

    private fun basic() = TripAccumulator("trip", 0, DataSource.SYNTHETIC).also {
        it.accept(VehicleSample.SeatBelt(0, true))
        it.accept(VehicleSample.Gear(1, Gear.DRIVE))
    }
    private fun speedTrip(seconds: Int, speed: Double): DerivedTripMetrics {
        val a = basic()
        for (second in 0..seconds) a.accept(VehicleSample.Speed(1 + second * 1_000L, speed))
        return a.finish(seconds * 1_000L + 1)
    }
    private fun drive(seconds: Int, good: Boolean = true, cruise: Boolean = true): DerivedTripMetrics {
        val a = TripAccumulator("trip", 0, DataSource.SYNTHETIC)
        a.accept(VehicleSample.SeatBelt(0, good))
        a.accept(VehicleSample.Gear(1, Gear.DRIVE))
        a.accept(VehicleSample.Cruise(1, cruise))
        a.accept(VehicleSample.Clock(1, if (good) 23 else 12))
        a.accept(VehicleSample.TirePressure(1, if (good) 230.0 else 190.0, 200.0))
        a.accept(VehicleSample.TireWarning(1, !good))
        a.accept(VehicleSample.FuelLevel(1, if (good) 60.0 else 15.0))
        a.accept(VehicleSample.ChargeLimit(1, if (good) 80.0 else 91.0))
        a.accept(VehicleSample.OilWarning(1, !good))
        a.accept(VehicleSample.BrakeFluidWarning(1, !good))
        a.accept(VehicleSample.WasherFluidWarning(1, !good))
        a.accept(VehicleSample.AirCleaning(1, good))
        for (second in 1..seconds) {
            val time = second * 1_000L
            a.accept(VehicleSample.Speed(time, if (good) 20.0 else if (second in 50..52) 10.0 else if (second % 2 == 0) 0.0 else 30.0))
            if (second == 10) a.accept(VehicleSample.Brake(time, true))
            if (second == 20) a.accept(VehicleSample.Brake(time, false))
            if (second == 49) a.accept(VehicleSample.TurnIndicator(time, good))
            if (second == 50) a.accept(VehicleSample.Steering(time, -30.0))
            if (second == 51) a.accept(VehicleSample.Steering(time, 0.0))
        }
        return a.finish(seconds * 1_000L)
    }
    private fun DerivedTripMetrics.trait(trait: TraitId) = traits.first { it.trait == trait }
}
