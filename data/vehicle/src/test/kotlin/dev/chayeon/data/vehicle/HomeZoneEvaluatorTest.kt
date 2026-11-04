package dev.chayeon.data.vehicle

import dev.chayeon.core.model.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class HomeZoneEvaluatorTest {
    private val origin = PrivatePoint(37.123456, 127.654321)
    private fun home(radius: HomeZoneRadius = HomeZoneRadius.MEDIUM) = HomeRecord(origin, radius, LocationSource.DEVICE)
    private fun fix(meters: Double, time: Long) = ZoneLocation.Fix(origin.offset(meters, 0.0), time)

    @Test fun jitterNeverStoresTheExactPointAndAlwaysContainsTheTrueHome() {
        for (radius in HomeZoneRadius.entries) repeat(2_000) { seed ->
            val record = HomeRecord.jitter(origin, radius, LocationSource.DEVICE, Random(seed))
            val distance = origin.distanceTo(record.center)
            assertTrue(distance > 0 && distance <= radius.meters * .25 + .0001)
            val evaluator = HomeZoneEvaluator().apply { reset(record) }
            assertEquals(HomeZoneStatus.INSIDE, evaluator.evaluate(ZoneLocation.Fix(origin, 0), 0).status)
            assertFalse(record.toString().contains("37.123456"))
            assertFalse(record.center.toString().contains("127.654321"))
        }
    }

    @Test fun startupAndEveryNonFixStateSuppressIncludingStaleFutureErrorAndPermissionLoss() {
        val evaluator = HomeZoneEvaluator({ 0 }).apply { reset(home()) }
        for (invalid in listOf(ZoneLocation.Unknown, ZoneLocation.PermissionOff, fix(900.0, -30_001), fix(900.0, 1),
            ZoneLocation.Fix(PrivatePoint(Double.NaN, 0.0), 0), ZoneLocation.Fix(origin, 0, Double.NaN))) {
            assertTrue(evaluator.evaluate(invalid, 0).suppressed)
            assertEquals(HomeZoneStatus.OUTSIDE, evaluator.evaluate(fix(900.0, 0), 0).status)
        }
        evaluator.reset(home())
        assertEquals(HomeZoneStatus.LOCATION_UNKNOWN, evaluator.evaluate(ZoneLocation.Unknown, 0).status)
        assertEquals(HomeZoneStatus.PERMISSION_OFF, evaluator.evaluate(ZoneLocation.PermissionOff, 0).status)
        evaluator.reset(null)
        assertEquals(HomeZoneStatus.NOT_SET, evaluator.evaluate(ZoneLocation.Unknown, 0).status)
    }

    @Test fun leaveNeedsContinuousBufferPlusDelayAndFlappingRestartsIt() {
        val evaluator = HomeZoneEvaluator({ 30_000 }).apply { reset(home()) }
        assertTrue(evaluator.evaluate(fix(551.0, 0), 0).suppressed)
        assertTrue(evaluator.evaluate(fix(551.0, 29_999), 29_999).suppressed)
        assertTrue(evaluator.evaluate(fix(549.0, 30_000), 30_000).suppressed)
        assertTrue(evaluator.evaluate(fix(551.0, 30_001), 30_001).suppressed)
        assertTrue(evaluator.evaluate(fix(551.0, 60_000), 60_000).suppressed)
        assertFalse(evaluator.evaluate(fix(551.0, 60_001), 60_001).suppressed)
        assertFalse(evaluator.evaluate(fix(501.0, 60_002), 60_002).suppressed)
        assertTrue(evaluator.evaluate(fix(499.0, 60_003), 60_003).suppressed)
    }

    @Test fun uncertaintyAndRadiusChangesResetTheExitWindowAndAccuracyCannotProveOutside() {
        val evaluator = HomeZoneEvaluator({ 90_000 }).apply { reset(home()) }
        evaluator.evaluate(fix(800.0, 0), 0)
        assertTrue(evaluator.evaluate(ZoneLocation.Unknown, 89_000).suppressed)
        assertTrue(evaluator.evaluate(fix(800.0, 90_000), 90_000).suppressed)
        evaluator.reset(home(HomeZoneRadius.WIDE))
        assertTrue(evaluator.evaluate(fix(800.0, 190_000), 190_000).suppressed)
        evaluator.reset(home(HomeZoneRadius.NEAR))
        assertTrue(evaluator.evaluate(ZoneLocation.Fix(origin.offset(500.0, 0.0), 190_001, 250.0), 190_001).suppressed)
    }

    @Test fun missingFreshFixDuringExitDelayDoesNotReleaseProtection() {
        val evaluator = HomeZoneEvaluator({ 60_000 }).apply { reset(home()) }
        val fix = fix(900.0, 0)
        evaluator.evaluate(fix, 0)
        assertTrue(evaluator.evaluate(fix, 30_001).suppressed)
        assertTrue(evaluator.evaluate(fix, 60_001).suppressed)
        assertTrue(evaluator.evaluate(fix(900.0, 60_002), 60_002).suppressed)
    }
}
