package dev.chayeon.data.vehicle

import dev.chayeon.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleSafetyTest {
    @Test fun `startup and stale park are unknown while fresh explicit park alone permits interaction`() = runTest {
        val gateway = SimulatedVehicleGateway(backgroundScope) { testScheduler.currentTime }
        val driving = VehicleDrivingStateProvider(gateway, backgroundScope, { testScheduler.currentTime })
        assertEquals(DrivingPolicy.UNKNOWN, driving.current().policy)
        gateway.setGear(Gear.PARK)
        assertEquals(DrivingPolicy.ALLOWED, driving.current().policy)
        advanceTimeBy(2_501)
        assertEquals(DrivingPolicy.UNKNOWN, driving.current().policy)
        for (gear in listOf(Gear.DRIVE, Gear.NEUTRAL, Gear.REVERSE)) {
            gateway.setGear(gear)
            assertEquals(DrivingPolicy.RESTRICTED, driving.current().policy)
        }
    }

    @Test fun `resubscription never replays allowed from a stopped observer`() = runTest {
        val gateway = SimulatedVehicleGateway(backgroundScope) { testScheduler.currentTime }
        val driving = VehicleDrivingStateProvider(gateway, backgroundScope, { testScheduler.currentTime })
        gateway.setGear(Gear.PARK)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { driving.drivingState.collect {} }
        runCurrent()
        assertEquals(DrivingPolicy.ALLOWED, driving.drivingState.value.policy)
        collector.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(DrivingPolicy.UNKNOWN, driving.drivingState.value.policy)
        assertEquals(DrivingPolicy.UNKNOWN, driving.drivingState.first().policy)
    }

    @Test fun `revoking and restoring consent invalidates the old recording generation`() {
        val gate = MutableRecordingGate()
        gate.update("account", true)
        val before = gate.permission.value
        gate.update("account", false)
        gate.update("account", true)
        assertNotEquals(before, gate.permission.value)
        assertTrue(gate.permission.value.canRecord)
    }
}
