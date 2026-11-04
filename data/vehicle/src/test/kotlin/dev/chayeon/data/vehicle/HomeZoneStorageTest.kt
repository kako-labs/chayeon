package dev.chayeon.data.vehicle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.model.*
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeZoneStorageTest {
    @Test fun absentConsentRejectsDeviceSettingImmediatelyWhileSyntheticSettingRemainsAvailable() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalHomeZoneRepository(context, Locations(), { DrivingPolicy.ALLOWED }, backgroundScope, true,
            { currentTime }, UnconfinedTestDispatcher(testScheduler), "consent-${UUID.randomUUID()}.db")
        repo.bindAccount("a")
        for (consent in listOf(null, false)) {
            repo.updateLocationConsent("a", consent)
            val before = currentTime
            assertEquals(AppResult.Failure(AppError.Restricted), repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.DEVICE))
            assertEquals(before, currentTime)
            assertTrue(repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC) is AppResult.Success)
            assertEquals(if (consent == null) HomeZoneStatus.LOCATION_UNKNOWN else HomeZoneStatus.PERMISSION_OFF, repo.state.value.status)
            assertTrue(repo.state.value.suppressed)
        }
        repo.updateLocationConsent("b", true)
        assertNull(repo.locationConsent.value)
        assertEquals(HomeZoneStatus.LOCATION_UNKNOWN, repo.state.value.status)
        repo.updateLocationConsent("a", true)
        assertEquals(HomeZoneStatus.INSIDE, repo.state.value.status)
        repo.close()
    }

    @Test fun queuedCommandsCannotModifyAnotherAccountOrReturnAfterLocalCleanup() = runTest {
        val pending = mutableListOf<Runnable>()
        var hold = false
        val io = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (hold) pending += block else block.run()
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LocalHomeZoneRepository(context, Locations(), { DrivingPolicy.ALLOWED }, backgroundScope, true,
            { currentTime }, io, "race-${UUID.randomUUID()}.db")
        for (nextAccount in listOf("b", "a")) for (action in listOf("radius", "clear", "set")) {
            repo.bindAccount("a")
            repo.updateLocationConsent("a", true)
            repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC)
            hold = true
            val command = async(start = CoroutineStart.UNDISPATCHED) {
                when (action) {
                    "clear" -> repo.clear()
                    "set" -> repo.setFromCurrentPosition(HomeZoneRadius.WIDE, LocationSource.SYNTHETIC)
                    else -> repo.changeRadius(HomeZoneRadius.WIDE)
                }
            }
            hold = false
            repo.clearLocalData()
            repo.bindAccount(nextAccount)
            repo.updateLocationConsent(nextAccount, true)
            repo.setFromCurrentPosition(HomeZoneRadius.NEAR, LocationSource.SYNTHETIC)
            pending.toList().also { pending.clear() }.forEach { it.run() }
            runCurrent()
            assertEquals(AppResult.Failure(AppError.NotAuthorized), command.await())
            assertEquals(HomeZoneRadius.NEAR, repo.state.value.radius)
            assertEquals(HomeZoneStatus.INSIDE, repo.state.value.status)
        }
        repo.close()
    }

    private class Locations : HomeLocationProvider {
        override val location = MutableStateFlow<ZoneLocation>(ZoneLocation.Unknown)
        var permission = true
        override fun hasPermission() = permission
        override fun start() {}
        override fun stop() {}
    }

    @Test fun onlyJitteredCenterIsStoredRadiusPreservesItAndAccountsAreIsolated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Exclude Robolectric's package bootstrap; inspect every log from the device scenario.
        ShadowLog.clear()
        val name = "test-${UUID.randomUUID()}.db"
        val locations = Locations()
        val point = PrivatePoint(37.123456, 127.654321)
        locations.location.value = ZoneLocation.Fix(point, currentTime)
        val repo = LocalHomeZoneRepository(context, locations, { DrivingPolicy.ALLOWED }, backgroundScope, true,
            { currentTime }, UnconfinedTestDispatcher(testScheduler), name)
        val signature = LocalSignatureRepository(context, MutableRecordingGate().apply { update("a", true) },
            { DrivingPolicy.ALLOWED }, backgroundScope, epochMillis = { 1_000_000 },
            dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = name)
        runCurrent()
        val trip = DerivedTripMetrics("trip", 0, 760_000, 1_000_000, DataSource.SYNTHETIC, TripEligibility.ELIGIBLE,
            listOf(DerivedTraitMetrics(TraitId.GENTLE_RIDE, TraitAvailability.AVAILABLE,
                listOf(PrivateMetric(MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES, 0.0, MetricUnit.EVENTS_PER_TEN_MINUTES)), 1, 1)),
            240_000, 2_500.0, 1.0)
        assertTrue(signature.recordTrip(RecordTripRequest(CommandMeta("record"), trip)) is AppResult.Success)
        repo.bindAccount("a")
        repo.updateLocationConsent("a", true)
        assertTrue(repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.DEVICE) is AppResult.Success)
        val helper = VehicleDatabase(context, name)
        fun center(): Pair<Double, Double> = helper.readableDatabase.rawQuery("SELECT center_lat, center_lon FROM home_zone", null).use {
            assertTrue(it.moveToFirst()); it.getDouble(0) to it.getDouble(1)
        }
        val stored = center()
        assertNotEquals(point.latitude to point.longitude, stored)
        assertTrue(point.distanceTo(PrivatePoint(stored.first, stored.second)) <= 125.001)
        repo.changeRadius(HomeZoneRadius.NEAR)
        assertEquals(stored, center())
        repo.bindAccount("b")
        assertEquals(HomeZoneStatus.NOT_SET, repo.state.value.status)
        repo.bindAccount("a")
        assertEquals(HomeZoneStatus.LOCATION_UNKNOWN, repo.state.value.status)
        var checkedRows = 0
        helper.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name != 'home_zone'", null).use { tables ->
            while (tables.moveToNext()) helper.readableDatabase.rawQuery("SELECT * FROM \"${tables.getString(0)}\"", null).use { rows ->
                while (rows.moveToNext()) {
                    checkedRows++
                    repeat(rows.columnCount) { index ->
                        val value = rows.getString(index).orEmpty()
                        listOf("latitude", "longitude", "home", "radius", "37.123456", "127.654321", stored.first.toString(), stored.second.toString()).forEach { assertFalse(value.contains(it)) }
                    }
                }
            }
        }
        assertTrue(checkedRows >= 4)
        val diagnostics = ShadowLog.getLogs().map { "log:${it.tag}" to it.msg } + listOf(
            "repository" to repo.toString(), "point" to point.toString(), "fix" to locations.location.value.toString(),
            "state" to repo.state.value.toString(), "radii" to HomeZoneRadius.entries.toString(), "statuses" to HomeZoneStatus.entries.toString())
        diagnostics.forEach { (label, text) ->
            listOf("latitude", "longitude", "radius", "home", "inside", "outside", "medium", "wide",
                "37.123456", "127.654321", stored.first.toString(), stored.second.toString()).forEach {
                assertFalse("Private content in $label", text.lowercase().contains(it))
            }
        }
        assertTrue(repo.clearLocalData() is AppResult.Success)
        repo.bindAccount("a")
        assertEquals(HomeZoneStatus.NOT_SET, repo.state.value.status)
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM home_zone", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        repo.close(); signature.close(); helper.close()
    }

    @Test fun restartPermissionLossSyntheticLabellingAndDrivingCommandGuards() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        val locations = Locations()
        var policy = DrivingPolicy.ALLOWED
        fun repository(demo: Boolean = true) = LocalHomeZoneRepository(context, locations, { policy }, backgroundScope, demo,
            { currentTime }, UnconfinedTestDispatcher(testScheduler), name)
        var repo = repository()
        repo.bindAccount("a")
        repo.updateLocationConsent("a", true)
        assertTrue(repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC) is AppResult.Success)
        assertEquals(LocationSource.SYNTHETIC, repo.state.value.source)
        repo.setSyntheticPosition(false)
        advanceTimeBy(2_001); runCurrent()
        assertEquals(HomeZoneStatus.OUTSIDE, repo.state.value.status)
        repo.close()
        repo = repository()
        repo.bindAccount("a")
        assertEquals(HomeZoneStatus.LOCATION_UNKNOWN, repo.state.value.status)
        repo.updateLocationConsent("a", true)
        policy = DrivingPolicy.RESTRICTED
        assertTrue(repo.clear() is AppResult.Failure)
        assertTrue(repo.changeRadius(HomeZoneRadius.WIDE) is AppResult.Failure)
        assertTrue(repo.setSyntheticPosition(false) is AppResult.Failure)
        policy = DrivingPolicy.ALLOWED
        assertTrue(repo.clear() is AppResult.Success)
        locations.location.value = ZoneLocation.Fix(PrivatePoint(0.0, 0.0), currentTime, accuracyMeters = 500.0)
        assertTrue(repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC) is AppResult.Success)
        repo.clear()
        locations.location.value = ZoneLocation.Fix(PrivatePoint(0.0, 0.0), currentTime)
        assertTrue(repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC) is AppResult.Failure)
        assertTrue(repo.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.DEVICE) is AppResult.Success)
        locations.permission = false
        advanceTimeBy(1_001); runCurrent()
        assertEquals(HomeZoneStatus.PERMISSION_OFF, repo.state.value.status)
        repo.clearLocalData(); repo.close()
        val release = repository(false)
        release.bindAccount("a")
        locations.location.value = ZoneLocation.Unknown
        assertTrue(release.setFromCurrentPosition(HomeZoneRadius.MEDIUM, LocationSource.SYNTHETIC) is AppResult.Failure)
        release.close()
    }
}
