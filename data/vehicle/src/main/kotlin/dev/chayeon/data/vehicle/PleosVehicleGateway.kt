package dev.chayeon.data.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.SignalKind
import dev.chayeon.core.signature.VehicleSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The optional automotive classes are loaded only after the runtime library check succeeds. */
class PleosVehicleGateway(
    private val context: Context,
    private val scope: CoroutineScope,
) : VehicleGateway {
    override val source = DataSource.VEHICLE
    override val isRealVehicle = true
    private val mutableSamples = MutableSharedFlow<VehicleSample>(extraBufferCapacity = 128)
    override val samples = mutableSamples.asSharedFlow()
    private val mutableCapabilities = MutableStateFlow(SignalKind.entries.associateWith { SignalCapability.UNSUPPORTED })
    override val capabilities = mutableCapabilities.asStateFlow()
    private val mutableSafety = MutableStateFlow(SafetyObservation())
    override val safety = mutableSafety.asStateFlow()
    private var backend: AutomotiveBackend? = null
    internal val safetyFailure: String? get() = backend?.safetyFailure
    private var started = false
    private var enabled = false

    @SuppressLint("PrivateApi")
    override fun start() {
        if (started) return
        started = true
        try {
            Class.forName("android.car.Car")
            Class.forName("ai.pleos.playground.vehicle.Vehicle")
            backend = AutomotiveBackend(context, scope, ::sample, ::unsupported, mutableSafety).also {
                it.start()
                it.setRecordingEnabled(enabled)
            }
        } catch (_: Exception) {
            unavailable()
        } catch (_: LinkageError) {
            unavailable()
        }
    }

    private fun sample(kind: SignalKind, sample: VehicleSample) {
        if (!enabled) return
        mutableCapabilities.update {
            if (kind == SignalKind.SPEED) it + (kind to SignalCapability.AVAILABLE) + (SignalKind.TRIP_LENGTH to SignalCapability.AVAILABLE)
            else it + (kind to SignalCapability.AVAILABLE)
        }
        if (!mutableSamples.tryEmit(sample)) unsupported(kind)
    }

    private fun unsupported(kind: SignalKind) {
        mutableCapabilities.update {
            if (kind == SignalKind.SPEED) it + (kind to SignalCapability.UNSUPPORTED) + (SignalKind.TRIP_LENGTH to SignalCapability.UNSUPPORTED)
            else it + (kind to SignalCapability.UNSUPPORTED)
        }
        if (enabled) mutableSamples.tryEmit(VehicleSample.Unsupported(SystemClock.elapsedRealtime(), kind))
    }

    private fun unavailable() {
        backend?.close()
        backend = null
        mutableSafety.value = SafetyObservation()
        mutableCapabilities.value = SignalKind.entries.associateWith { SignalCapability.UNSUPPORTED }
    }

    override fun setRecordingEnabled(enabled: Boolean) {
        this.enabled = enabled
        backend?.setRecordingEnabled(enabled)
    }

    override fun close() {
        enabled = false
        unavailable()
        started = false
    }
}
