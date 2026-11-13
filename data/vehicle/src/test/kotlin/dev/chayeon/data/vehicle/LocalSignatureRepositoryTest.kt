package dev.chayeon.data.vehicle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.model.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSignatureRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L
    private fun trip(id: String = "trip", end: Long = now) = DerivedTripMetrics(
        id, 0, end - 240_000, end, DataSource.SYNTHETIC, TripEligibility.ELIGIBLE,
        listOf(DerivedTraitMetrics(TraitId.GENTLE_RIDE, TraitAvailability.AVAILABLE,
            listOf(PrivateMetric(MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES, 0.0, MetricUnit.EVENTS_PER_TEN_MINUTES)), 1, 1)),
        240_000, 2_500.0, 1.0,
    )

    @Test fun `trip commit creates recap and derived row with no raw signals and survives reopen`() = runTest {
        val name = "test-${UUID.randomUUID()}.db"
        VehicleDatabase(context, name).use { it.writableDatabase }
        val gate = MutableRecordingGate().apply { update("a", true) }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { now }, dispatcher = dispatcher, databaseName = name)
        runCurrent()
        val request = RecordTripRequest(CommandMeta("record"), trip())
        val first = repository.recordTrip(request)
        assertTrue(first is AppResult.Success)
        assertEquals(first, repository.recordTrip(request))
        val recaps = (repository.recaps.first() as LoadState.Ready).value
        assertEquals(1, recaps.size)
        val helper = VehicleDatabase(context, name)
        val db = helper.readableDatabase
        db.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) {
                val schema = cursor.getString(1).lowercase()
                listOf("meterspersecond", "steering", "latitude", "longitude", "vehicle_model", "raw_sample", "vin", "crn").forEach {
                    assertFalse("Raw column $it", schema.contains(it))
                }
            }
        }
        db.rawQuery("SELECT derived_metrics FROM trips", null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            val payload = cursor.getString(0)
            assertEquals(trip(), VehicleStorageCodec.trip(payload))
            listOf("metersPerSecond", "steering", "kilopascals", "elapsedRealtimeMillis", "VehicleSample").forEach { assertFalse(payload.contains(it)) }
        }
        helper.close()
        repository.close()
        val reopened = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { now }, dispatcher = dispatcher, databaseName = name)
        runCurrent()
        assertEquals(1, (reopened.recaps.first() as LoadState.Ready).value.size)
        assertEquals(first, reopened.recordTrip(request))
        reopened.close()
    }

    @Test fun `first private signature survives storage reopen and exclusion clears the milestone`() = runTest {
        val name = "milestone-${UUID.randomUUID()}.db"
        val gate = MutableRecordingGate().apply { update("a", true) }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        fun open() = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { now }, dispatcher = dispatcher, databaseName = name)
        val repository = open()
        runCurrent()
        repeat(4) { repository.recordTrip(RecordTripRequest(CommandMeta("record-$it"), trip("trip-$it", now - 4 + it))) }
        val before = (repository.recaps.first() as LoadState.Ready).value
        assertEquals("trip-2", before.single { it.firstSignature }.tripId)
        val privateSignature = (repository.signature.first() as LoadState.Ready).value
        assertNull(privateSignature.publicSignature)
        val evidence = privateSignature.evidence.single { it.trait == TraitId.GENTLE_RIDE }
        assertEquals(100, evidence.score)
        assertEquals(4, evidence.passingSampleCount)
        assertEquals(4, evidence.recentTrips.size)
        assertEquals(privateSignature, VehicleStorageCodec.signature(VehicleStorageCodec.signature(privateSignature)))
        repository.close()
        val reopened = open()
        runCurrent()
        assertEquals(before, (reopened.recaps.first() as LoadState.Ready).value)
        val restored = (reopened.signature.first() as LoadState.Ready).value.evidence.single { it.trait == TraitId.GENTLE_RIDE }
        assertEquals(evidence.score, restored.score)
        assertEquals(evidence.recentTrips, restored.recentTrips)
        val milestone = before.single { it.firstSignature }
        assertTrue((reopened.markRecapSeen(MarkRecapSeenRequest(CommandMeta("seen"), milestone.id)) as AppResult.Success).value.firstSignature)
        assertTrue(reopened.excludeTrip(ExcludeTripRequest(CommandMeta("exclude"), milestone.tripId)) is AppResult.Success)
        val excluded = (reopened.observeRecap(milestone.id).first() as LoadState.Ready).value!!
        assertFalse(excluded.firstSignature)
        assertFalse(excluded.included)
        reopened.close()
    }

    @Test fun `old recap payload defaults first signature to false`() {
        val recap = TripRecap("recap", 1, "trip", now, DataSource.SYNTHETIC, KnotSeed(1), KnotSeed(2), emptyList(), firstSignature = true)
        assertEquals(recap, VehicleStorageCodec.recap(VehicleStorageCodec.recap(recap)))
        val old = VehicleStorageCodec.recap(recap).replace(",\"firstSignature\":true", "")
        assertFalse(VehicleStorageCodec.recap(old).firstSignature)
    }

    @Test fun `account switches isolation consent restriction exclusions visibility and clear are atomic`() = runTest {
        val gate = MutableRecordingGate().apply { update("a", true) }
        var policy = DrivingPolicy.ALLOWED
        val repository = LocalSignatureRepository(context, gate, { policy }, backgroundScope,
            epochMillis = { now }, dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = "test-${UUID.randomUUID()}.db")
        runCurrent()
        repeat(3) { assertTrue(repository.recordTrip(RecordTripRequest(CommandMeta("r$it"), trip("t$it"))) is AppResult.Success) }
        val visible = repository.updateVisibility(UpdateVisibilityRequest(CommandMeta("visible"), setOf(TraitId.GENTLE_RIDE), true))
        assertTrue(visible is AppResult.Success)
        assertNotNull((repository.signature.first() as LoadState.Ready).value.publicSignature)
        policy = DrivingPolicy.UNKNOWN
        assertEquals(AppResult.Failure(AppError.Restricted), repository.excludeTrip(ExcludeTripRequest(CommandMeta("blocked"), "t0")))
        policy = DrivingPolicy.ALLOWED
        assertTrue(repository.excludeTrip(ExcludeTripRequest(CommandMeta("exclude"), "t0")) is AppResult.Success)
        val after = (repository.signature.first() as LoadState.Ready).value
        assertTrue(after.publicSignature?.traits.isNullOrEmpty())
        assertFalse((repository.recaps.first() as LoadState.Ready).value.single { it.tripId == "t0" }.included)
        gate.update("b", true)
        assertTrue((repository.signature.first() as LoadState.Ready).value.evidence.all { it.validTripCount == 0 })
        runCurrent()
        assertTrue((repository.recaps.first() as LoadState.Ready).value.isEmpty())
        gate.update("b", false)
        assertEquals(AppResult.Failure(AppError.NotAuthorized), repository.recordTrip(RecordTripRequest(CommandMeta("no-consent"), trip("no"))))
        assertTrue(repository.clearLocalData() is AppResult.Success)
        gate.update("a", true)
        runCurrent()
        assertTrue((repository.recaps.first() as LoadState.Ready).value.isEmpty())
        repository.close()
    }

    @Test fun `parallel retries create one trip and conflicting retry body cannot mutate it`() = runTest {
        val gate = MutableRecordingGate().apply { update("a", true) }
        val repository = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { now }, dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = "test-${UUID.randomUUID()}.db")
        runCurrent()
        val request = RecordTripRequest(CommandMeta("same"), trip())
        val results = (1..12).map { async { repository.recordTrip(request) } }.awaitAll()
        assertTrue(results.all { it == results.first() && it is AppResult.Success })
        assertEquals(1, (repository.recaps.first() as LoadState.Ready).value.size)
        assertEquals(AppResult.Failure(AppError.Unknown), repository.recordTrip(request.copy(metrics = trip("different"))))
        repository.close()
    }

    @Test fun `account switch during IO dispatch cannot assign a trip to the next account`() = runTest {
        val gate = MutableRecordingGate().apply { update("a", true) }
        val repository = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { now }, dispatcher = StandardTestDispatcher(testScheduler), databaseName = "race-${UUID.randomUUID()}.db")
        runCurrent()
        val result = async(start = CoroutineStart.UNDISPATCHED) { repository.recordTrip(RecordTripRequest(CommandMeta("queued"), trip())) }
        gate.update("b", true)
        runCurrent()
        assertEquals(AppResult.Failure(AppError.NotAuthorized), result.await())
        assertTrue((repository.recaps.first() as LoadState.Ready).value.isEmpty())
        repository.close()
    }

    @Test fun `clear invalidates the active recording generation so an old trip cannot return`() = runTest {
        val gate = MutableRecordingGate().apply { update("a", true) }
        val repository = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { now }, dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = "clear-${UUID.randomUUID()}.db")
        runCurrent()
        val oldPermission = gate.permission.value
        assertTrue(repository.clearLocalData() is AppResult.Success)
        assertEquals(AppResult.Failure(AppError.NotAuthorized), repository.recordTripFor(oldPermission, RecordTripRequest(CommandMeta("late"), trip())))
        gate.update("a", false)
        gate.update("a", true)
        runCurrent()
        assertTrue(repository.recordTrip(RecordTripRequest(CommandMeta("fresh"), trip())) is AppResult.Success)
        repository.close()
    }

    @Test fun `retention removes expired derived trips and recaps`() = runTest {
        var clock = now
        val gate = MutableRecordingGate().apply { update("a", true) }
        val repository = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { clock }, dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = "test-${UUID.randomUUID()}.db")
        runCurrent()
        assertTrue(repository.recordTrip(RecordTripRequest(CommandMeta("old"), trip())) is AppResult.Success)
        clock += 31L * 24 * 60 * 60 * 1_000
        gate.update("a", false)
        runCurrent()
        assertTrue((repository.recaps.first() as LoadState.Ready).value.isEmpty())
        assertTrue((repository.signature.first() as LoadState.Ready).value.evidence.all { it.validTripCount == 0 })
        repository.close()
    }
}
