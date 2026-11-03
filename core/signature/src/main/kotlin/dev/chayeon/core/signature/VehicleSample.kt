package dev.chayeon.core.signature

import dev.chayeon.core.model.SignalKind

/** Ephemeral input only. Timestamps use the same monotonic clock, never the wall clock. */
sealed interface VehicleSample {
    val elapsedRealtimeMillis: Long

    data class Speed(override val elapsedRealtimeMillis: Long, val metersPerSecond: Double) : VehicleSample
    data class Gear(override val elapsedRealtimeMillis: Long, val value: dev.chayeon.core.model.Gear) : VehicleSample
    data class Brake(override val elapsedRealtimeMillis: Long, val pressed: Boolean) : VehicleSample
    data class Steering(override val elapsedRealtimeMillis: Long, val degrees: Double) : VehicleSample
    data class TurnIndicator(override val elapsedRealtimeMillis: Long, val active: Boolean) : VehicleSample
    data class Cruise(override val elapsedRealtimeMillis: Long, val active: Boolean) : VehicleSample
    data class SeatBelt(override val elapsedRealtimeMillis: Long, val fastened: Boolean) : VehicleSample
    data class Clock(override val elapsedRealtimeMillis: Long, val localHour: Int) : VehicleSample
    data class TirePressure(
        override val elapsedRealtimeMillis: Long,
        val kilopascals: Double,
        val lowPressureThresholdKpa: Double,
    ) : VehicleSample
    data class TireWarning(override val elapsedRealtimeMillis: Long, val lowPressure: Boolean) : VehicleSample
    data class FuelLevel(override val elapsedRealtimeMillis: Long, val percent: Double) : VehicleSample
    data class BatteryLevel(override val elapsedRealtimeMillis: Long, val percent: Double) : VehicleSample
    data class ChargeLimit(override val elapsedRealtimeMillis: Long, val percent: Double) : VehicleSample
    data class OilWarning(override val elapsedRealtimeMillis: Long, val active: Boolean) : VehicleSample
    data class BrakeFluidWarning(override val elapsedRealtimeMillis: Long, val active: Boolean) : VehicleSample
    data class WasherFluidWarning(override val elapsedRealtimeMillis: Long, val active: Boolean) : VehicleSample
    data class AirCleaning(override val elapsedRealtimeMillis: Long, val active: Boolean) : VehicleSample
    data class Unsupported(override val elapsedRealtimeMillis: Long, val signal: SignalKind) : VehicleSample
}
