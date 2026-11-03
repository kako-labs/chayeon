package dev.chayeon.data.vehicle

import android.os.SystemClock
import dev.chayeon.core.model.DataSource
import dev.chayeon.core.model.Gear
import dev.chayeon.core.model.SignalKind
import dev.chayeon.core.signature.VehicleSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SimulatedVehicleGateway(
    private val scope: CoroutineScope,
    private val monotonicMillis: () -> Long = SystemClock::elapsedRealtime,
) : VehicleGateway {
    override val source = DataSource.SYNTHETIC
    override val isRealVehicle = false
    private val mutableSamples = MutableSharedFlow<VehicleSample>(extraBufferCapacity = 32)
    override val samples = mutableSamples.asSharedFlow()
    private val mutableCapabilities = MutableStateFlow(SignalKind.entries.associateWith { SignalCapability.UNSUPPORTED })
    override val capabilities = mutableCapabilities.asStateFlow()
    private val mutableSafety = MutableStateFlow(SafetyObservation())
    override val safety = mutableSafety.asStateFlow()
    private var job: Job? = null
    private var selectedGear = Gear.UNKNOWN
    private var recording = false

    override fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                publishGear()
                delay(500)
            }
        }
    }

    fun setGear(gear: Gear) {
        selectedGear = gear
        publishGear()
    }

    private fun publishGear() {
        val now = monotonicMillis()
        mutableSafety.value = SafetyObservation(
            selectedGear, when (selectedGear) { Gear.UNKNOWN -> null; Gear.PARK -> 0; else -> 1 }, now, now,
        )
        if (recording && selectedGear != Gear.UNKNOWN) mutableSamples.tryEmit(VehicleSample.Gear(now, selectedGear))
    }

    override fun setRecordingEnabled(enabled: Boolean) { recording = enabled }
    override fun close() {
        job?.cancel()
        job = null
        selectedGear = Gear.UNKNOWN
        mutableSafety.value = SafetyObservation()
    }
}
