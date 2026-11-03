package dev.chayeon.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.annotation.SuppressLint
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import dev.chayeon.core.model.Gear
import dev.chayeon.core.model.SignalKind
import dev.chayeon.core.signature.VehicleSample
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/** No values or SDK exception messages are logged: they may contain platform-private details. */
@SuppressLint("MissingPermission")
internal class AutomotiveBackend(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sample: (SignalKind, VehicleSample) -> Unit,
    private val unsupported: (SignalKind) -> Unit,
    private val safety: MutableStateFlow<SafetyObservation>,
) : AutoCloseable {
    internal var safetyFailure: String? = null
        private set
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    @Volatile private var carReady = false
    private var vehicle: Vehicle? = null
    private val jobs = mutableListOf<Job>()
    @Volatile private var recording = false
    private var speedRegistered = false

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (!recording) return
            if (!hasSignalPermission(context, SignalKind.SPEED)) {
                unsupported(SignalKind.SPEED)
                return
            }
            val speed = (value.value as? Number)?.toDouble()
            val timestamp = value.timestamp / 1_000_000
            if (value.status == CarPropertyValue.STATUS_AVAILABLE && speed != null && speed.isFinite() && timestamp > 0) {
                sample(SignalKind.SPEED, VehicleSample.Speed(timestamp, abs(speed)))
            } else unsupported(SignalKind.SPEED)
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { unsupported(SignalKind.SPEED) }
    }

    fun start() {
        try {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                carReady = ready
                safety.value = SafetyObservation()
                if (ready) {
                    try {
                        properties = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                        restrictions = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                        restrictions?.registerListener(::updateRestrictions)
                        updateRestrictions(restrictions?.currentCarUxRestrictions)
                        speedRegistered = false
                        setRecordingEnabled(recording)
                    } catch (_: Exception) {
                        carReady = false
                        safety.value = SafetyObservation()
                    }
                } else {
                    properties = null
                    restrictions = null
                    speedRegistered = false
                }
            }
        } catch (_: Exception) {
            safety.value = SafetyObservation()
        } catch (_: LinkageError) {
            safety.value = SafetyObservation()
        }
        try { vehicle = Vehicle(context).also { it.initialize() } }
        catch (_: Exception) { } catch (_: LinkageError) { }
        jobs += scope.launch {
            while (isActive) {
                pollSafety()
                delay(500)
            }
        }
        val sdk = vehicle ?: return
        poll(SignalKind.BRAKE, 200) {
            readable { ok, fail -> sdk.getBrake().checkBrakePedalStatusCapabilities(ok, fail) } &&
                emitValue(SignalKind.BRAKE, { ok, fail -> sdk.getBrake().getCurrentBrakePedalStatus(ok, fail) }) { t, v: Boolean -> VehicleSample.Brake(t, v) }
        }
        poll(SignalKind.STEERING_MAGNITUDE, 200) {
            readable { ok, fail -> sdk.getSteeringWheel().checkSteeringWheelAngleCapabilities({ config, _ -> ok(config) }, fail) } &&
                emitValue(SignalKind.STEERING_MAGNITUDE, { ok, fail -> sdk.getSteeringWheel().getCurrentSteeringWheelAngle(ok, fail) }) { t, v: Float -> VehicleSample.Steering(t, abs(v.toDouble())) }
        }
        poll(SignalKind.TURN_INDICATOR, 200) {
            readable { ok, fail -> sdk.getTurnSignal().checkTurnSignalStateCapabilities(ok, fail) } &&
                emitValue(SignalKind.TURN_INDICATOR, { ok, fail -> sdk.getTurnSignal().getCurrentTurnSignalState(ok, fail) }) { t, v: TurnSignalValue -> VehicleSample.TurnIndicator(t, v != TurnSignalValue.OFF) }
        }
        poll(SignalKind.CRUISE, 1_000) {
            readable { ok, fail -> sdk.getDrivingMode().checkCruiseControlStateCapabilities(ok, fail) } &&
                emitValue(SignalKind.CRUISE, { ok, fail -> sdk.getDrivingMode().getCruiseControlState(ok, fail) }) { t, v: CruiseControl -> VehicleSample.Cruise(t, v == CruiseControl.ACTIVATED) }
        }
        poll(SignalKind.SEAT_BELT, 500) {
            val driver = driverSeat() ?: return@poll false
            val areas = read<List<SeatArea>> { ok, fail ->
                sdk.getSeat().checkSeatBeltBuckledCapabilities({ config, list -> ok(if (config.canRead()) list else emptyList()) }, fail)
            }
            areas?.contains(driver) == true && emitValue(SignalKind.SEAT_BELT, { ok, fail ->
                sdk.getSeat().getCurrentSeatBeltBuckledState(driver, ok, fail)
            }) { t, v: Boolean -> VehicleSample.SeatBelt(t, v) }
        }
        poll(SignalKind.FUEL_LEVEL, 5_000) {
            readable { ok, fail -> sdk.getFuel().checkFuelPercentCapabilities(ok, fail) } &&
                emitValue(SignalKind.FUEL_LEVEL, { ok, fail -> sdk.getFuel().getFuelPercent(ok, fail) }) { t, v: Int -> VehicleSample.FuelLevel(t, v.toDouble()) }
        }
        poll(SignalKind.BATTERY_LEVEL, 5_000) {
            readable { ok, fail -> sdk.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, fail) } &&
                emitValue(SignalKind.BATTERY_LEVEL, { ok, fail -> sdk.getEvBattery().getEvBatteryStateOfCharge(ok, fail) }) { t, v: Int -> VehicleSample.BatteryLevel(t, v.toDouble()) }
        }
        // The SDK has no percent-limit getter. Check its capability, then use the same standard property.
        poll(SignalKind.CHARGE_LIMIT, 5_000) {
            read<CapabilityConfig> { ok, fail ->
                sdk.getEvBattery().checkEvChargePercentLimitCapabilities({ config, _ -> ok(config) }, fail)
            } ?: return@poll false
            val manager = properties ?: return@poll false
            val propertyId = 0x11600F40
            val config = manager.getCarPropertyConfig(propertyId) ?: return@poll false
            if (config.access != 1 && config.access != 3) return@poll false
            val value = manager.getFloatProperty(propertyId, 0).toDouble()
            if (!value.isFinite() || value !in 0.0..100.0) return@poll false
            sample(SignalKind.CHARGE_LIMIT, VehicleSample.ChargeLimit(now(), value))
            true
        }
        poll(SignalKind.OIL_WARNING, 5_000) {
            readable { ok, fail -> sdk.getSafety().checkEngineOilLevelStateCapabilities({ c, _ -> ok(c) }, fail) } &&
                emitOil(SignalKind.OIL_WARNING, { ok, fail -> sdk.getSafety().getEngineOilLevelState(ok, fail) }, VehicleSample::OilWarning)
        }
        poll(SignalKind.BRAKE_FLUID_WARNING, 5_000) {
            readable { ok, fail -> sdk.getSafety().checkBrakeFluidLevelStateCapabilities({ c, _ -> ok(c) }, fail) } &&
                emitOil(SignalKind.BRAKE_FLUID_WARNING, { ok, fail -> sdk.getSafety().getBrakeFluidLevelState(ok, fail) }, VehicleSample::BrakeFluidWarning)
        }
        poll(SignalKind.WASHER_FLUID_WARNING, 5_000) {
            readable { ok, fail -> sdk.getSafety().checkLowWasherFluidWarningStateCapabilities(ok, fail) } &&
                emitValue(SignalKind.WASHER_FLUID_WARNING, { ok, fail -> sdk.getSafety().hasLowWasherFluidWarning(ok, fail) }) { t, v: Boolean -> VehicleSample.WasherFluidWarning(t, v) }
        }
        poll(SignalKind.AIR_CLEANING, 5_000) {
            val areas = read<List<SeatArea>> { ok, fail ->
                sdk.getHVAC().checkHvacAirCleanerCapabilities({ config, list -> ok(if (config.canRead()) list else emptyList()) }, fail)
            }
            val area = areas?.firstOrNull() ?: return@poll false
            emitValue(SignalKind.AIR_CLEANING, { ok, fail -> sdk.getHVAC().getHvacAirCleanerState(area, ok, fail) }) { t, v: Boolean -> VehicleSample.AirCleaning(t, v) }
        }
        poll(SignalKind.TIRE_PRESSURE, 5_000) {
            val areas = read<List<WheelArea>> { ok, fail ->
                sdk.getTire().checkTirePressureCapabilities({ c, list -> ok(if (c.canRead()) list else emptyList()) }, fail)
            } ?: return@poll false
            val lowAreas = read<List<WheelArea>> { ok, fail ->
                sdk.getTire().checkLowTirePressureCapabilities({ c, list -> ok(if (c.canRead()) list else emptyList()) }, fail)
            } ?: return@poll false
            if (areas.isEmpty() || !lowAreas.containsAll(areas)) return@poll false
            var lowestMargin = Double.POSITIVE_INFINITY
            var weakestPressure = 0.0
            var weakestThreshold = 0.0
            for (area in areas) {
                val pressure = read<Float> { ok, fail -> sdk.getTire().getTirePressure(area, ok, fail) } ?: return@poll false
                val threshold = read<Float> { ok, fail -> sdk.getTire().getLowTirePressure(area, ok, fail) } ?: return@poll false
                if (!pressure.isFinite() || !threshold.isFinite() || pressure <= 0 || threshold <= 0) return@poll false
                val margin = (pressure - threshold).toDouble()
                if (margin < lowestMargin) {
                    lowestMargin = margin
                    weakestPressure = pressure.toDouble()
                    weakestThreshold = threshold.toDouble()
                }
            }
            val time = now()
            sample(SignalKind.TIRE_PRESSURE, VehicleSample.TirePressure(time, weakestPressure, weakestThreshold))
            sample(SignalKind.TIRE_WARNING, VehicleSample.TireWarning(time, lowestMargin <= 0))
            true
        }
        poll(SignalKind.CLOCK, 1_000) {
            sample(SignalKind.CLOCK, VehicleSample.Clock(now(), ZonedDateTime.now().hour))
            true
        }
    }

    private fun updateRestrictions(value: CarUxRestrictions?) {
        val state = if (!carReady || value == null) null else if (value.isRequiresDistractionOptimization) 1 else 0
        safety.update { it.copy(drivingState = state, drivingReceivedAtMillis = if (state != null) now() else null) }
    }

    private suspend fun pollSafety() {
        if (!carReady || car?.isConnected != true) {
            safety.value = SafetyObservation()
            return
        }
        val manager = properties
        try {
            updateRestrictions(restrictions?.currentCarUxRestrictions)
            safetyFailure = if (safety.value.drivingState == null) "NoUxRestrictionsService" else null
        } catch (_: Exception) {
            safetyFailure = "UxRestrictionsUnavailable"
            safety.value = SafetyObservation()
            return
        }
        try {
            if (manager?.getCarPropertyConfig(VehiclePropertyIds.GEAR_SELECTION) == null) throw IllegalStateException()
            val sdk = vehicle ?: throw IllegalStateException()
            val value = read<VehicleGear> { ok, fail -> sdk.getGear().getCurrentGearSelectionState(ok, fail) }
            val gear = when (value) {
                VehicleGear.GEAR_PARK -> Gear.PARK
                VehicleGear.GEAR_DRIVE -> Gear.DRIVE
                VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
                VehicleGear.GEAR_REVERSE -> Gear.REVERSE
                else -> Gear.UNKNOWN
            }
            val time = now()
            safety.update { it.copy(gear = gear, gearReceivedAtMillis = if (gear != Gear.UNKNOWN) time else null) }
            if (recording && gear != Gear.UNKNOWN) sample(SignalKind.GEAR, VehicleSample.Gear(time, gear))
        } catch (_: Exception) {
            safety.update { it.copy(gear = Gear.UNKNOWN, gearReceivedAtMillis = null) }
            unsupported(SignalKind.GEAR)
        }
    }

    private fun driverSeat(): SeatArea? = try {
        when (properties?.getIntProperty(VehiclePropertyIds.INFO_DRIVER_SEAT, 0)) {
            1 -> SeatArea(SeatValue.ROW_1_LEFT)
            4 -> SeatArea(SeatValue.ROW_1_RIGHT)
            else -> null
        }
    } catch (_: Exception) { null }

    fun setRecordingEnabled(enabled: Boolean) {
        recording = enabled
        val manager = properties ?: return
        try {
            if (enabled && !speedRegistered) {
                speedRegistered = manager.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) != null &&
                    manager.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f)
                if (!speedRegistered) unsupported(SignalKind.SPEED)
            } else if (!enabled && speedRegistered) {
                manager.unregisterCallback(speedCallback)
                speedRegistered = false
            }
        } catch (_: Exception) {
            speedRegistered = false
            unsupported(SignalKind.SPEED)
        }
    }

    private fun poll(kind: SignalKind, periodMillis: Long, block: suspend () -> Boolean) {
        jobs += scope.launch {
            delay(3_000)
            while (isActive) {
                if (recording) {
                    val success = try { hasSignalPermission(context, kind) && block() } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false } catch (_: LinkageError) { false }
                    if (!success) unsupported(kind)
                }
                delay(periodMillis)
            }
        }
    }

    private suspend fun emitOil(kind: SignalKind, block: ((OilLevelState?) -> Unit, (Exception) -> Unit) -> Unit,
        create: (Long, Boolean) -> VehicleSample): Boolean {
        val value = read(block) ?: return false
        if (value == OilLevelState.ERROR) return false
        sample(kind, create(now(), value != OilLevelState.NORMAL))
        return true
    }

    private suspend fun <T> emitValue(kind: SignalKind, block: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
        create: (Long, T) -> VehicleSample): Boolean {
        val value = read(block) ?: return false
        sample(kind, create(now(), value))
        return true
    }

    private suspend fun readable(block: ((CapabilityConfig?) -> Unit, (Exception) -> Unit) -> Unit): Boolean =
        read(block)?.canRead() == true

    private suspend fun <T> read(block: ((T?) -> Unit, (Exception) -> Unit) -> Unit): T? = withTimeoutOrNull(1_000) {
        suspendCancellableCoroutine { continuation ->
            try {
                block({ if (continuation.isActive) continuation.resume(it) }, { if (continuation.isActive) continuation.resume(null) })
            } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
        }
    }

    private fun CapabilityConfig.canRead() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE
    private fun now() = SystemClock.elapsedRealtime()

    override fun close() {
        recording = false
        carReady = false
        try { restrictions?.unregisterListener() } catch (_: Exception) { }
        jobs.forEach { it.cancel() }
        jobs.clear()
        try { properties?.unregisterCallback(speedCallback) } catch (_: Exception) { }
        try { vehicle?.release() } catch (_: Exception) { }
        try { car?.disconnect() } catch (_: Exception) { }
        safety.value = SafetyObservation()
    }
}

internal fun hasSignalPermission(context: Context, kind: SignalKind): Boolean {
    val permission = when (kind) {
        SignalKind.SPEED, SignalKind.TRIP_LENGTH -> "android.car.permission.CAR_SPEED"
        SignalKind.FUEL_LEVEL, SignalKind.BATTERY_LEVEL, SignalKind.CHARGE_LIMIT -> "android.car.permission.CAR_ENERGY"
        else -> return true
    }
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
