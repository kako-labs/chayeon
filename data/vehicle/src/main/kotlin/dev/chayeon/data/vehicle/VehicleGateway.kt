package dev.chayeon.data.vehicle

import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.Gear
import dev.chayeon.core.model.SignalKind
import dev.chayeon.core.signature.VehicleSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Receipt timestamps use the same monotonic clock as the driving policy. */
data class SafetyObservation(
    val gear: Gear = Gear.UNKNOWN,
    val drivingState: Int? = null,
    val gearReceivedAtMillis: Long? = null,
    val drivingReceivedAtMillis: Long? = null,
)

enum class SignalCapability { AVAILABLE, UNSUPPORTED }

interface VehicleGateway : AutoCloseable {
    val source: DataSource
    val isRealVehicle: Boolean
    val samples: Flow<VehicleSample>
    val capabilities: StateFlow<Map<SignalKind, SignalCapability>>
    val safety: StateFlow<SafetyObservation>
    fun start()
    fun setRecordingEnabled(enabled: Boolean)
}
