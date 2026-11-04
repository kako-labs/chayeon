package dev.chayeon.data.vehicle

import android.Manifest
import android.app.Application
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.model.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeLocationProviderTest {
    @Test fun poorerProviderCannotDisplaceAFixUnderFiveSecondsOldButThenResumesFailClosedEvaluation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        var now = 10_000L
        val provider = PlatformHomeLocationProvider(app) { now }
        val home = HomeRecord.jitter(PrivatePoint(0.0, 0.0), HomeZoneRadius.MEDIUM, LocationSource.DEVICE, Random(7))
        val point = home.center.offset(900.0, 0.0)
        fun fix(accuracy: Float, providerName: String = "gps") = Location(providerName).apply {
            latitude = point.latitude; longitude = point.longitude
            elapsedRealtimeNanos = now * 1_000_000; this.accuracy = accuracy
        }
        val evaluator = HomeZoneEvaluator(exitDelayMillis = { 0 }).apply { reset(home) }
        provider.onLocationChanged(fix(5f))
        val good = provider.location.value
        assertEquals(HomeZoneStatus.OUTSIDE, evaluator.evaluate(good, now).status)
        now += 4_999
        provider.onLocationChanged(fix(500f, "network"))
        assertSame(good, provider.location.value)
        assertEquals(HomeZoneStatus.OUTSIDE, evaluator.evaluate(provider.location.value, now).status)
        now++
        provider.onLocationChanged(fix(500f, "network"))
        assertNotSame(good, provider.location.value)
        assertEquals(HomeZoneStatus.INSIDE, evaluator.evaluate(provider.location.value, now).status)
        provider.onLocationChanged(fix(3f))
        assertEquals(3.0, (provider.location.value as ZoneLocation.Fix).accuracyMeters, 0.0)
        provider.stop()
        assertEquals(HomeZoneStatus.LOCATION_UNKNOWN, evaluator.evaluate(provider.location.value, now).status)
    }
}
