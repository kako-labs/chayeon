package dev.chayeon.data.vehicle

import android.app.Application
import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import dev.chayeon.core.model.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeZoneServiceTest {
    @Test fun consentWithdrawalStopsGpsWithAStoredHomeAndGrantRestartsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = shadowOf(app.getSystemService(LocationManager::class.java))
        manager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val graph = VehicleGraph(app, gatewayFactory = { _, scope -> SimulatedVehicleGateway(scope) })
        fun fix() {
            manager.simulateLocation(Location("gps").apply {
                latitude = 0.0; longitude = 0.0; accuracy = 5f
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
            shadowOf(Looper.getMainLooper()).idle()
        }
        try {
            graph.start(); graph.setForeground(true)
            (graph.gateway as SimulatedVehicleGateway).setGear(Gear.PARK)
            runCurrent()
            graph.homeZoneRepository.bindAccount("a")
            graph.prepareHomeLocation(); runCurrent()
            assertFalse(graph.locationServiceAllowed())
            assertTrue(manager.getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
            graph.updateLocationConsent("a", true)
            graph.prepareHomeLocation(); runCurrent(); graph.serviceStarted()
            assertTrue(graph.locationServiceAllowed())
            assertTrue(manager.getLocationRequests(LocationManager.GPS_PROVIDER).isNotEmpty())
            fix(); runCurrent()
            val saved = graph.homeZoneRepository.setFromCurrentPosition()
            assertTrue("Setting failed: $saved; policy=${graph.drivingStateProvider.current().policy}", saved is AppResult.Success)
            graph.updateLocationConsent("a", false); runCurrent()
            assertNotNull(graph.homeZoneRepository.state.value.radius)
            assertEquals(HomeZoneStatus.PERMISSION_OFF, graph.homeZoneRepository.state.value.status)
            assertFalse(graph.locationServiceAllowed())
            assertTrue(manager.getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
            graph.updateLocationConsent("a", null); runCurrent()
            assertEquals(HomeZoneStatus.LOCATION_UNKNOWN, graph.homeZoneRepository.state.value.status)
            while (shadowOf(app).nextStartedService != null) { /* Drain earlier service requests. */ }
            graph.updateLocationConsent("a", true); runCurrent()
            assertNotNull("Restoring consent must request service promotion before GPS can restart", shadowOf(app).nextStartedService)
            graph.serviceStarted()
            assertTrue(graph.locationServiceAllowed())
            assertTrue(manager.getLocationRequests(LocationManager.GPS_PROVIDER).isNotEmpty())
            fix(); runCurrent()
            assertEquals(HomeZoneStatus.INSIDE, graph.homeZoneRepository.state.value.status)
        } finally { graph.close(); Dispatchers.resetMain() }
    }

    @Test fun transientDiscoveryNeverStopsAPendingForegroundServiceBeforePromotion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadow = shadowOf(app)
        val graph = VehicleGraph(app, gatewayFactory = { _, scope -> SimulatedVehicleGateway(scope) { currentTime } })
        try {
            graph.start()
            graph.setForeground(true)
            graph.setPresenceNeeded(true)
            runCurrent()
            assertNotNull(shadow.nextStartedService)
            // A new, unconfigured account goes from loading to discovery OFF before onCreate.
            graph.setPresenceNeeded(false)
            runCurrent()
            assertNull(shadow.nextStoppedService)
            graph.serviceStarted()
            assertNotNull(shadow.nextStoppedService)
            graph.serviceStopped()
        } finally { graph.close(); Dispatchers.resetMain() }
    }
}
