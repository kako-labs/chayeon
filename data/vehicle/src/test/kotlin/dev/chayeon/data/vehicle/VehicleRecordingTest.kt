package dev.chayeon.data.vehicle

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.VehicleSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRecordingTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `signal permissions are checked again after runtime denial and restoration`() {
        val application = shadowOf(context as Application)
        application.denyPermissions("android.car.permission.CAR_SPEED", "android.car.permission.CAR_ENERGY")
        val energy = listOf(SignalKind.FUEL_LEVEL, SignalKind.BATTERY_LEVEL, SignalKind.CHARGE_LIMIT)
        energy.forEach { assertFalse(hasSignalPermission(context, it)) }
        assertFalse(hasSignalPermission(context, SignalKind.SPEED))
        assertTrue(hasSignalPermission(context, SignalKind.GEAR))
        application.grantPermissions("android.car.permission.CAR_ENERGY")
        energy.forEach { assertTrue(hasSignalPermission(context, it)) }
        assertFalse(hasSignalPermission(context, SignalKind.SPEED))
        application.denyPermissions("android.car.permission.CAR_ENERGY")
        energy.forEach { assertFalse(hasSignalPermission(context, it)) }
        application.grantPermissions("android.car.permission.CAR_SPEED")
        assertTrue(hasSignalPermission(context, SignalKind.SPEED))
    }

    @Test fun `stream to parking stores only derived metrics and consent revocation drops the partial trip`() = runTest {
        var epoch = 1_800_000_000_000L
        val gate = MutableRecordingGate().apply { update("driver", true) }
        val gateway = FakeGateway()
        val name = "stream-${UUID.randomUUID()}.db"
        val repository = LocalSignatureRepository(context, gate, { DrivingPolicy.ALLOWED }, backgroundScope,
            epochMillis = { epoch }, dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = name)
        val recorder = TripRecorder(gateway, repository, gate, backgroundScope) { epoch }
        recorder.start()
        runCurrent()
        gateway.samples.emit(VehicleSample.Gear(0, Gear.PARK))
        gateway.samples.emit(VehicleSample.SeatBelt(1, true))
        gateway.samples.emit(VehicleSample.Gear(2, Gear.DRIVE))
        for (time in 2L..240_002L step 200) {
            gateway.samples.emit(VehicleSample.Speed(time, 11.1234567))
            gateway.samples.emit(VehicleSample.Steering(time, 17.7654321))
        }
        epoch += 240_002
        gateway.samples.emit(VehicleSample.Gear(240_003, Gear.PARK))
        runCurrent()
        assertEquals(1, (repository.recaps.first() as LoadState.Ready).value.size)
        assertTrue((repository.recaps.first() as LoadState.Ready).value.single().included)
        VehicleDatabase(context, name).use { helper ->
            helper.readableDatabase.rawQuery("SELECT derived_metrics FROM trips", null).use {
                assertTrue(it.moveToFirst())
                val stored = it.getString(0)
                assertFalse(stored.contains("11.1234567"))
                assertFalse(stored.contains("17.7654321"))
                assertFalse(stored.contains("elapsedRealtimeMillis"))
                assertEquals(TripEligibility.ELIGIBLE, VehicleStorageCodec.trip(stored).eligibility)
            }
        }
        gateway.samples.emit(VehicleSample.Gear(245_000, Gear.DRIVE))
        gateway.samples.emit(VehicleSample.Gear(245_010, Gear.PARK))
        runCurrent()
        assertEquals(1, (repository.recaps.first() as LoadState.Ready).value.size)
        gateway.samples.emit(VehicleSample.Gear(250_000, Gear.DRIVE))
        gate.update("driver", false)
        runCurrent()
        gate.update("driver", true)
        runCurrent()
        gateway.samples.emit(VehicleSample.Gear(250_001, Gear.PARK))
        runCurrent()
        assertEquals(1, (repository.recaps.first() as LoadState.Ready).value.size)
        recorder.close()
        repository.close()
    }

    @Test fun `three accelerated care scenarios yield a synthetic signature with tire evidence`() = runTest {
        val gate = MutableRecordingGate().apply { update("demo", true) }
        val gateway = SimulatedVehicleGateway(backgroundScope) { testScheduler.currentTime }
        val driving = VehicleDrivingStateProvider(gateway, backgroundScope, { testScheduler.currentTime })
        gateway.start()
        gateway.setGear(Gear.PARK)
        val epoch = 1_800_000_000_000L
        val repository = LocalSignatureRepository(context, gate, { driving.current().policy }, backgroundScope,
            epochMillis = { epoch }, dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = "replay-${UUID.randomUUID()}.db")
        val console = DefaultVehicleConsole(context, gateway, driving, repository, gate, backgroundScope, enabled = true, epochMillis = { epoch })
        runCurrent()
        repeat(3) { index ->
            assertTrue(console.replay(ReplayDriveRequest(CommandMeta("replay-$index"), DriveScenario.CARE)) is AppResult.Success)
            advanceTimeBy(12_000)
            runCurrent()
            assertEquals(ReplayStatus.FINISHED, console.consoleState.value.status)
        }
        assertEquals(3, (repository.recaps.first() as LoadState.Ready).value.size)
        assertTrue((repository.recaps.first() as LoadState.Ready).value.all { it.included })
        assertTrue(repository.updateVisibility(UpdateVisibilityRequest(CommandMeta("publish"), TraitId.entries.toSet(), true)) is AppResult.Success)
        val signature = (repository.signature.first() as LoadState.Ready).value
        assertEquals(DataSource.SYNTHETIC, signature.publicSignature!!.source)
        assertEquals(TraitAvailability.AVAILABLE, signature.evidence.single { it.trait == TraitId.TIRE_CARE }.availability)
        assertTrue(signature.publicSignature!!.traits.isNotEmpty())
        console.close()
        gateway.close()
        repository.close()
    }

    @Test fun `missing automotive runtime is unsupported without crashing or pretending to park`() = runTest {
        val gateway = PleosVehicleGateway(context, backgroundScope)
        gateway.start()
        runCurrent()
        assertEquals(Gear.UNKNOWN, gateway.safety.value.gear)
        assertNull(gateway.safety.value.drivingState)
        assertTrue(gateway.capabilities.value.values.all { it == SignalCapability.UNSUPPORTED })
        gateway.setRecordingEnabled(true)
        gateway.close()
        assertEquals(SafetyObservation(), gateway.safety.value)
    }

    @Test fun `gear console cannot override a non simulated gateway`() = runTest {
        val gate = MutableRecordingGate().apply { update("driver", true) }
        val gateway = FakeGateway()
        gateway.safety.value = SafetyObservation(Gear.PARK, 0, 0, 0)
        val driving = VehicleDrivingStateProvider(gateway, backgroundScope, { testScheduler.currentTime })
        val repository = LocalSignatureRepository(context, gate, { driving.current().policy }, backgroundScope,
            dispatcher = UnconfinedTestDispatcher(testScheduler), databaseName = "gear-${UUID.randomUUID()}.db")
        val console = DefaultVehicleConsole(context, gateway, driving, repository, gate, backgroundScope, enabled = true)
        val result = console.setGear(SetGearRequest(CommandMeta("gear"), Gear.DRIVE))
        assertEquals(AppResult.Failure(AppError.Restricted), result)
        assertEquals(Gear.PARK, gateway.safety.value.gear)
        assertEquals(DrivingPolicy.ALLOWED, driving.current().policy)
        console.close()
        repository.close()
    }

    private class FakeGateway : VehicleGateway {
        override val source = DataSource.SYNTHETIC
        override val isRealVehicle = false
        override val samples = MutableSharedFlow<VehicleSample>()
        override val capabilities = MutableStateFlow(SignalKind.entries.associateWith { SignalCapability.AVAILABLE })
        override val safety = MutableStateFlow(SafetyObservation())
        override fun start() = Unit
        override fun setRecordingEnabled(enabled: Boolean) = Unit
        override fun close() = Unit
    }
}
