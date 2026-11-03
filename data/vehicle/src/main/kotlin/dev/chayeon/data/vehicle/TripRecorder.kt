package dev.chayeon.data.vehicle

import dev.chayeon.core.model.AppResult
import dev.chayeon.core.model.CommandMeta
import dev.chayeon.core.model.Gear
import dev.chayeon.core.model.RecordTripRequest
import dev.chayeon.core.signature.TripAccumulator
import dev.chayeon.core.signature.VehicleSample
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class RecordingStatus { STOPPED, ACTIVE, STORAGE_UNAVAILABLE }

/** Accumulators live only in this coroutine. Restarting the process cannot resume a partial trip. */
class TripRecorder(
    private val gateway: VehicleGateway,
    private val repository: LocalSignatureRepository,
    private val gate: RecordingGate,
    private val scope: CoroutineScope,
    private val epochMillis: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private var job: Job? = null
    private val mutableStatus = MutableStateFlow(RecordingStatus.STOPPED)
    val status = mutableStatus.asStateFlow()

    fun start() {
        if (job != null) return
        job = scope.launch {
            gate.permission.collectLatest { permission ->
                if (!permission.canRecord) return@collectLatest
                coroutineScope {
                    var accumulator: TripAccumulator? = null
                    var belt: VehicleSample.SeatBelt? = null
                    var previousGear = Gear.UNKNOWN
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        gateway.samples.collect { sample ->
                            if (gate.permission.value != permission) {
                                accumulator = null
                                belt = null
                                return@collect
                            }
                            if (sample is VehicleSample.SeatBelt) belt = sample
                            if (sample is VehicleSample.Gear) {
                                if (sample.value == Gear.DRIVE && accumulator == null && previousGear != Gear.DRIVE) {
                                    val beltBeforeDrive = belt?.takeIf { sample.elapsedRealtimeMillis - it.elapsedRealtimeMillis in 1..2_500 }
                                    accumulator = TripAccumulator(
                                        tripId = UUID.randomUUID().toString(), startedAtEpochMillis = epochMillis(),
                                        source = gateway.source,
                                        startedAtMonotonicMillis = beltBeforeDrive?.elapsedRealtimeMillis ?: sample.elapsedRealtimeMillis,
                                    ).also { trip -> beltBeforeDrive?.let(trip::accept) }
                                }
                                accumulator?.accept(sample)
                                if (sample.value == Gear.PARK && accumulator != null) {
                                    val trip = accumulator!!.finish(epochMillis())
                                    accumulator = null
                                    if (gate.permission.value == permission) {
                                        val result = repository.recordTripFor(permission, RecordTripRequest(CommandMeta("trip-${trip.id}"), trip))
                                        if (result is AppResult.Failure) mutableStatus.value = RecordingStatus.STORAGE_UNAVAILABLE
                                    }
                                }
                                previousGear = sample.value
                            } else accumulator?.accept(sample)
                        }
                    }
                    gateway.setRecordingEnabled(true)
                    mutableStatus.value = RecordingStatus.ACTIVE
                    try { awaitCancellation() } finally {
                        gateway.setRecordingEnabled(false)
                        accumulator = null
                        belt = null
                        mutableStatus.value = RecordingStatus.STOPPED
                    }
                }
            }
        }
    }

    override fun close() {
        job?.cancel()
        job = null
        gateway.setRecordingEnabled(false)
        mutableStatus.value = RecordingStatus.STOPPED
    }
}
