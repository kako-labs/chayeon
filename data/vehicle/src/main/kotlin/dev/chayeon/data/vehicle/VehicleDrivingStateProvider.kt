package dev.chayeon.data.vehicle

import android.os.SystemClock
import dev.chayeon.core.model.DrivingPolicy
import dev.chayeon.core.model.DrivingState
import dev.chayeon.core.model.DrivingStateProvider
import dev.chayeon.core.model.Gear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class VehicleDrivingStateProvider(
    private val gateway: VehicleGateway,
    scope: CoroutineScope,
    private val monotonicMillis: () -> Long = SystemClock::elapsedRealtime,
    private val staleAfterMillis: Long = 2_500,
) : DrivingStateProvider {
    private val ticks = flow {
        while (true) {
            emit(monotonicMillis())
            delay(100)
        }
    }

    override val drivingState = combine(gateway.safety, ticks) { observation, now ->
        evaluate(observation, now)
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0),
        DrivingState(source = gateway.source),
    )

    /** Commands evaluate the source and clock again, even without an active UI collector. */
    fun current(): DrivingState = evaluate(gateway.safety.value, monotonicMillis())

    private fun evaluate(observation: SafetyObservation, now: Long): DrivingState {
        fun fresh(at: Long?) = at != null && now >= at && now - at <= staleAfterMillis
        val policy = when {
            !fresh(observation.gearReceivedAtMillis) || !fresh(observation.drivingReceivedAtMillis) -> DrivingPolicy.UNKNOWN
            observation.drivingState == null || observation.drivingState < 0 || observation.gear == Gear.UNKNOWN -> DrivingPolicy.UNKNOWN
            observation.gear != Gear.PARK || observation.drivingState >= 1 -> DrivingPolicy.RESTRICTED
            observation.drivingState == 0 -> DrivingPolicy.ALLOWED
            else -> DrivingPolicy.UNKNOWN
        }
        return DrivingState(
            version = maxOf(observation.gearReceivedAtMillis ?: 0, observation.drivingReceivedAtMillis ?: 0),
            policy = policy, gear = observation.gear, source = gateway.source,
        )
    }
}
