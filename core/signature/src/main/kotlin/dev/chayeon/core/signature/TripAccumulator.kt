package dev.chayeon.core.signature

import dev.chayeon.core.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** A single-trip, constant-memory reducer. No sample, collection of samples, or trajectory is retained. */
class TripAccumulator(
    private val tripId: String,
    private val startedAtEpochMillis: Long,
    private val source: DataSource,
    private val startedAtMonotonicMillis: Long = 0,
) {
    private val timestamps = LongArray(SignalKind.entries.size) { -1 }
    private val counts = IntArray(SignalKind.entries.size)
    private val unavailable = BooleanArray(SignalKind.entries.size)
    private val observations = IntArray(TraitId.entries.size)
    private val successes = IntArray(TraitId.entries.size)
    private var latest = startedAtMonotonicMillis
    private var gear = Gear.UNKNOWN
    private var duration = 0L
    private var coverage = 0L
    private var movement = 0.0
    private var speed = 0.0
    private var smoothedSpeed = 0.0
    private var speedAt = -1L
    private var acceleration = 0.0
    private var abruptDirection = 0
    private var abruptEvents = 0
    private var accelerationIntervals = 0
    private var brake = false
    private var brakeKnown = false
    private var brakeObserved = false
    private var brakeInterrupted = false
    private var brakeMaxDeceleration = 0.0
    private var brakeSegments = 0
    private var gentleBrakeSegments = 0
    private var turnStart = -1L
    private var turnSignaled = false
    private var pendingTurnDeadline = -1L
    private var indicatorAt = -1L
    private var indicator = false
    private var turnSegments = 0
    private var signaledTurnSegments = 0
    private var belt = false
    private var beltAt = -1L
    private var driveTransitions = 0
    private var beltedTransitions = 0
    private var cruise = false
    private var steadyStart = -1L
    private var steadyCount = 0L
    private var steadyMean = 0.0
    private var steadyM2 = 0.0
    private var segmentCruiseMillis = 0L
    private var segmentMillis = 0L
    private var steadySegments = 0
    private var steadyPassingSegments = 0
    private var variationSum = 0.0
    private var cruiseSum = 0.0
    private var hour: Int? = null
    private var clockDuration = 0L
    private var nightDuration = 0L
    private var tireCohortAt = -1L
    private var fluidCohortAt = -1L
    private var tirePressureHealthy: Boolean? = null
    private var tireWarningHealthy: Boolean? = null
    private var oilHealthy: Boolean? = null
    private var brakeFluidHealthy: Boolean? = null
    private var fuelPassing = 0
    private var batteryPassing = 0
    private var washerHealthy: Boolean? = null
    private var finished: DerivedTripMetrics? = null

    init {
        require(tripId.isNotBlank())
        require(startedAtEpochMillis >= 0 && startedAtMonotonicMillis >= 0)
    }

    fun accept(sample: VehicleSample) {
        check(finished == null) { "Trip is finished" }
        val kind = sample.kind()
        val time = sample.elapsedRealtimeMillis
        if (time < latest || time <= timestamps[kind.ordinal] || time < startedAtMonotonicMillis) return
        if (!sample.valid()) return
        advance(time)
        timestamps[kind.ordinal] = time
        if (sample is VehicleSample.Unsupported) {
            unavailable[kind.ordinal] = true
            when (kind) {
                SignalKind.SPEED -> resetMotion()
                SignalKind.BRAKE -> { brake = false; brakeKnown = false; brakeObserved = false }
                SignalKind.STEERING_MAGNITUDE, SignalKind.TURN_INDICATOR -> { turnStart = -1; pendingTurnDeadline = -1 }
                SignalKind.SEAT_BELT -> beltAt = -1
                SignalKind.GEAR -> gear = Gear.UNKNOWN
                SignalKind.CLOCK -> hour = null
                else -> Unit
            }
            return
        }
        counts[kind.ordinal] = increment(counts[kind.ordinal])
        when (sample) {
            is VehicleSample.Speed -> acceptSpeed(time, sample.metersPerSecond)
            is VehicleSample.Gear -> {
                if (sample.value == Gear.DRIVE && gear != Gear.DRIVE) {
                    resetMotion()
                    driveTransitions = increment(driveTransitions)
                    if (belt && beltAt in startedAtMonotonicMillis until time) beltedTransitions = increment(beltedTransitions)
                }
                if (sample.value != Gear.DRIVE && gear == Gear.DRIVE) {
                    closeBrake()
                    closeSteady()
                    closeTurn()
                    resetMotion()
                }
                gear = sample.value
            }
            is VehicleSample.Brake -> {
                if (brake && !sample.pressed) closeBrake()
                if (!brake && sample.pressed) { brakeObserved = false; brakeInterrupted = false; brakeMaxDeceleration = 0.0 }
                brake = sample.pressed
                brakeKnown = true
            }
            is VehicleSample.Steering -> acceptSteering(time, abs(sample.degrees))
            is VehicleSample.TurnIndicator -> {
                indicator = sample.active
                if (sample.active) {
                    indicatorAt = time
                    if (turnStart >= 0 && time - turnStart <= SignaturePolicy.INDICATOR_WINDOW_MILLIS) turnSignaled = true
                    if (pendingTurnDeadline >= time) { signaledTurnSegments = increment(signaledTurnSegments); pendingTurnDeadline = -1 }
                }
            }
            is VehicleSample.Cruise -> cruise = sample.active
            is VehicleSample.SeatBelt -> { belt = sample.fastened; beltAt = time }
            is VehicleSample.Clock -> hour = sample.localHour
            is VehicleSample.TirePressure -> {
                tirePressureHealthy = sample.kilopascals > sample.lowPressureThresholdKpa
                sampleTires()
            }
            is VehicleSample.TireWarning -> { tireWarningHealthy = !sample.lowPressure; sampleTires() }
            is VehicleSample.FuelLevel -> if (sample.percent > SignaturePolicy.MIN_ENERGY_PERCENT) fuelPassing = increment(fuelPassing)
            is VehicleSample.BatteryLevel -> if (sample.percent > SignaturePolicy.MIN_ENERGY_PERCENT) batteryPassing = increment(batteryPassing)
            is VehicleSample.ChargeLimit -> observe(TraitId.BATTERY_KIND, sample.percent <= SignaturePolicy.MAX_CHARGE_LIMIT_PERCENT)
            is VehicleSample.OilWarning -> { oilHealthy = !sample.active; sampleFluids() }
            is VehicleSample.BrakeFluidWarning -> { brakeFluidHealthy = !sample.active; sampleFluids() }
            is VehicleSample.WasherFluidWarning -> { washerHealthy = !sample.active; sampleFluids() }
            is VehicleSample.AirCleaning -> if (gear == Gear.DRIVE) observe(TraitId.CABIN_CARE, sample.active)
            is VehicleSample.Unsupported -> Unit
        }
    }

    private fun advance(time: Long) {
        val elapsed = time - latest
        if (gear == Gear.DRIVE) {
            duration += elapsed
            if (hour != null) {
                clockDuration += elapsed
                if (hour!! >= SignaturePolicy.NIGHT_START_HOUR || hour!! < SignaturePolicy.NIGHT_END_HOUR) nightDuration += elapsed
            }
        }
        if (pendingTurnDeadline >= 0 && time > pendingTurnDeadline) pendingTurnDeadline = -1
        latest = time
    }

    private fun acceptSpeed(time: Long, value: Double) {
        val elapsed = if (speedAt >= 0) time - speedAt else 0
        val connected = elapsed in 1..SignaturePolicy.MAX_SIGNAL_GAP_MILLIS && gear == Gear.DRIVE
        if (connected) {
            val previousSmoothed = smoothedSpeed
            smoothedSpeed += elapsed / (SignaturePolicy.SPEED_SMOOTHING_MILLIS + elapsed) * (value - smoothedSpeed)
            acceleration = (smoothedSpeed - previousSmoothed) / (elapsed / 1_000.0)
            coverage += minOf(elapsed, SignaturePolicy.MAX_COVERED_SPEED_INTERVAL_MILLIS)
            movement += (speed + value) * .5 * elapsed / 1_000.0
            accelerationIntervals = increment(accelerationIntervals)
            if (abs(acceleration) >= SignaturePolicy.ABRUPT_ACCELERATION_METERS_PER_SECOND_SQUARED) {
                val direction = if (acceleration > 0) 1 else -1
                if (direction != abruptDirection) abruptEvents = increment(abruptEvents)
                abruptDirection = direction
            } else if (abs(acceleration) < SignaturePolicy.ABRUPT_REARM_ACCELERATION) abruptDirection = 0
            if (brake && brakeKnown) {
                brakeObserved = true
                brakeMaxDeceleration = max(brakeMaxDeceleration, -acceleration)
            }
            if (value >= SignaturePolicy.STEADY_MIN_SPEED_METERS_PER_SECOND && abs(acceleration) <= SignaturePolicy.STEADY_MAX_ACCELERATION && !brake) {
                if (steadyStart < 0) steadyStart = speedAt
                steadyCount++
                val delta = value - steadyMean
                steadyMean += delta / steadyCount
                steadyM2 += delta * (value - steadyMean)
                segmentMillis += elapsed
                if (cruise && supported(SignalKind.CRUISE)) segmentCruiseMillis += elapsed
            } else closeSteady()
        } else {
            closeSteady()
            // A discontinuity cannot create a deceleration, successful brake, or turn.
            brakeObserved = false
            if (brake) brakeInterrupted = true
            brakeMaxDeceleration = 0.0
            turnStart = -1
            pendingTurnDeadline = -1
            acceleration = 0.0
            abruptDirection = 0
            smoothedSpeed = value
        }
        speed = value
        speedAt = time
    }

    private fun acceptSteering(time: Long, magnitude: Double) {
        if (gear != Gear.DRIVE || speedAt < 0 || time - speedAt > SignaturePolicy.MAX_SIGNAL_GAP_MILLIS || speed < SignaturePolicy.TURN_MIN_SPEED_METERS_PER_SECOND) {
            turnStart = -1
            return
        }
        if (magnitude >= SignaturePolicy.TURN_START_DEGREES && turnStart < 0) {
            turnStart = time
            turnSignaled = indicator && indicatorAt >= 0 && time - indicatorAt <= SignaturePolicy.INDICATOR_WINDOW_MILLIS
        } else if (magnitude <= SignaturePolicy.TURN_END_DEGREES && turnStart >= 0) closeTurn()
    }

    private fun closeTurn() {
        if (turnStart < 0) return
        turnSegments = increment(turnSegments)
        if (turnSignaled) signaledTurnSegments = increment(signaledTurnSegments)
        else if (latest <= turnStart + SignaturePolicy.INDICATOR_WINDOW_MILLIS) pendingTurnDeadline = turnStart + SignaturePolicy.INDICATOR_WINDOW_MILLIS
        turnStart = -1
    }

    private fun closeBrake() {
        if (brake && brakeObserved && !brakeInterrupted) {
            brakeSegments = increment(brakeSegments)
            if (brakeMaxDeceleration < SignaturePolicy.ABRUPT_ACCELERATION_METERS_PER_SECOND_SQUARED) gentleBrakeSegments = increment(gentleBrakeSegments)
        }
        brakeObserved = false
        brakeMaxDeceleration = 0.0
    }

    private fun closeSteady() {
        if (segmentMillis >= SignaturePolicy.STEADY_SEGMENT_MILLIS && steadyCount >= 2) {
            val variation = sqrt(steadyM2 / steadyCount) / steadyMean
            val cruiseRatio = segmentCruiseMillis.toDouble() / segmentMillis
            variationSum += variation
            cruiseSum += cruiseRatio
            steadySegments = increment(steadySegments)
            if (variation <= SignaturePolicy.MAX_STEADY_VARIATION_COEFFICIENT && cruiseRatio >= SignaturePolicy.MIN_CRUISE_USAGE_RATIO) steadyPassingSegments = increment(steadyPassingSegments)
        }
        steadyStart = -1
        steadyCount = 0
        steadyMean = 0.0
        steadyM2 = 0.0
        segmentCruiseMillis = 0
        segmentMillis = 0
    }

    private fun resetMotion() { speedAt = -1; acceleration = 0.0; turnStart = -1; pendingTurnDeadline = -1 }
    private fun sampleTires() {
        if (!supported(SignalKind.TIRE_PRESSURE, SignalKind.TIRE_WARNING)) return
        val pressureAt = timestamps[SignalKind.TIRE_PRESSURE.ordinal]
        val warningAt = timestamps[SignalKind.TIRE_WARNING.ordinal]
        val earliest = minOf(pressureAt, warningAt)
        val latest = maxOf(pressureAt, warningAt)
        if (earliest <= tireCohortAt || latest - earliest > SignaturePolicy.MAX_SIGNAL_GAP_MILLIS) return
        observe(TraitId.TIRE_CARE, tirePressureHealthy == true && tireWarningHealthy == true)
        tireCohortAt = latest
    }
    private fun sampleFluids() {
        if (!supported(SignalKind.OIL_WARNING, SignalKind.BRAKE_FLUID_WARNING, SignalKind.WASHER_FLUID_WARNING)) return
        val oilAt = timestamps[SignalKind.OIL_WARNING.ordinal]
        val brakeFluidAt = timestamps[SignalKind.BRAKE_FLUID_WARNING.ordinal]
        val washerAt = timestamps[SignalKind.WASHER_FLUID_WARNING.ordinal]
        val earliest = minOf(oilAt, brakeFluidAt, washerAt)
        val latest = maxOf(oilAt, brakeFluidAt, washerAt)
        if (earliest <= fluidCohortAt || latest - earliest > SignaturePolicy.MAX_SIGNAL_GAP_MILLIS) return
        observe(TraitId.FLUID_READY, oilHealthy == true && brakeFluidHealthy == true && washerHealthy == true)
        fluidCohortAt = latest
    }
    private fun observe(trait: TraitId, passing: Boolean) {
        observations[trait.ordinal] = increment(observations[trait.ordinal])
        if (passing) successes[trait.ordinal] = increment(successes[trait.ordinal])
    }
    private fun supported(kind: SignalKind): Boolean = counts[kind.ordinal] > 0 && !unavailable[kind.ordinal]
    private fun supported(vararg kinds: SignalKind): Boolean = kinds.all(::supported)

    fun finish(endedAtEpochMillis: Long): DerivedTripMetrics {
        finished?.let { return it }
        require(endedAtEpochMillis >= 0)
        closeBrake()
        closeSteady()
        closeTurn()
        val speedCoverage = if (duration > 0) (coverage.toDouble() / duration).coerceIn(0.0, 1.0) else null
        val eligibility = when {
            duration < SignaturePolicy.MIN_DRIVE_DURATION_MILLIS -> TripEligibility.TOO_SHORT
            !supported(SignalKind.SPEED) || !supported(SignalKind.GEAR) || speedCoverage == null || speedCoverage < SignaturePolicy.MIN_SPEED_COVERAGE_RATIO || endedAtEpochMillis < startedAtEpochMillis || abs((endedAtEpochMillis - startedAtEpochMillis) - (latest - startedAtMonotonicMillis)) > SignaturePolicy.MAX_SIGNAL_GAP_MILLIS -> TripEligibility.INSUFFICIENT_SIGNAL
            movement < SignaturePolicy.MIN_MOVEMENT_METERS -> TripEligibility.INSUFFICIENT_MOVEMENT
            else -> TripEligibility.ELIGIBLE
        }
        val traits = TraitId.entries.map { trait ->
            when (trait) {
                TraitId.GENTLE_RIDE -> {
                    val rate = if (coverage > 0) abruptEvents * 600_000.0 / coverage else 0.0
                    derived(trait, supported(SignalKind.SPEED), accelerationIntervals, if (rate <= SignaturePolicy.MAX_GENTLE_EVENTS_PER_TEN_MINUTES) accelerationIntervals else 0,
                        PrivateMetric(MetricId.ABRUPT_ACCELERATION_EVENTS_PER_TEN_MINUTES, rate, MetricUnit.EVENTS_PER_TEN_MINUTES))
                }
                TraitId.EARLY_BRAKE -> ratio(trait, supported(SignalKind.SPEED, SignalKind.BRAKE), brakeSegments, gentleBrakeSegments, MetricId.GENTLE_BRAKE_RATIO)
                TraitId.SIGNAL_MANNERS -> ratio(trait, supported(SignalKind.SPEED, SignalKind.STEERING_MAGNITUDE, SignalKind.TURN_INDICATOR), turnSegments, signaledTurnSegments, MetricId.SIGNAL_USAGE_RATIO)
                TraitId.STEADY_PACE -> derived(trait, supported(SignalKind.SPEED, SignalKind.CRUISE), steadySegments, steadyPassingSegments,
                    PrivateMetric(MetricId.CRUISE_VARIATION_COEFFICIENT, if (steadySegments > 0) variationSum / steadySegments else 0.0, MetricUnit.COEFFICIENT),
                    PrivateMetric(MetricId.CRUISE_USAGE_RATIO, if (steadySegments > 0) cruiseSum / steadySegments else 0.0, MetricUnit.RATIO))
                TraitId.BELT_FIRST -> ratio(trait, supported(SignalKind.SEAT_BELT, SignalKind.GEAR), driveTransitions, beltedTransitions, MetricId.BELT_BEFORE_DRIVE_RATIO)
                TraitId.LONG_ROAD -> ratio(trait, supported(SignalKind.SPEED) && !unavailable[SignalKind.TRIP_LENGTH.ordinal], 1, if (movement >= SignaturePolicy.LONG_TRIP_METERS) 1 else 0, MetricId.LONG_TRIP_RATIO)
                TraitId.NIGHT_DRIVE -> ratio(trait, supported(SignalKind.CLOCK), if (clockDuration > 0) 1 else 0, if (clockDuration > 0 && nightDuration.toDouble() / clockDuration >= SignaturePolicy.VISIBLE_RATIO) 1 else 0, MetricId.NIGHT_TRIP_RATIO)
                TraitId.TIRE_CARE -> observed(trait, supported(SignalKind.TIRE_PRESSURE, SignalKind.TIRE_WARNING), MetricId.HEALTHY_TIRE_RATIO)
                TraitId.ENERGY_PLANNER -> {
                    val fuelCount = if (supported(SignalKind.FUEL_LEVEL)) counts[SignalKind.FUEL_LEVEL.ordinal] else 0
                    val batteryCount = if (supported(SignalKind.BATTERY_LEVEL)) counts[SignalKind.BATTERY_LEVEL.ordinal] else 0
                    val count = (fuelCount.toLong() + batteryCount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val passing = ((if (fuelCount > 0) fuelPassing.toLong() else 0) + (if (batteryCount > 0) batteryPassing.toLong() else 0)).coerceAtMost(count.toLong()).toInt()
                    ratio(trait, fuelCount > 0 || batteryCount > 0, count, passing, MetricId.ENERGY_RESERVE_RATIO)
                }
                TraitId.BATTERY_KIND -> observed(trait, supported(SignalKind.CHARGE_LIMIT), MetricId.KIND_CHARGE_LIMIT_RATIO)
                TraitId.FLUID_READY -> observed(trait, supported(SignalKind.OIL_WARNING, SignalKind.BRAKE_FLUID_WARNING, SignalKind.WASHER_FLUID_WARNING), MetricId.NO_FLUID_WARNING_RATIO)
                TraitId.CABIN_CARE -> observed(trait, supported(SignalKind.AIR_CLEANING), MetricId.AIR_CLEANING_RATIO)
            }
        }
        return DerivedTripMetrics(tripId, 0, startedAtEpochMillis, endedAtEpochMillis, source, eligibility, traits, duration,
            if (supported(SignalKind.SPEED)) movement else null, speedCoverage).also { finished = it }
    }

    private fun observed(trait: TraitId, supported: Boolean, metric: MetricId) = ratio(trait, supported, observations[trait.ordinal], successes[trait.ordinal], metric)
    private fun ratio(trait: TraitId, supported: Boolean, count: Int, passing: Int, metric: MetricId) =
        derived(trait, supported, count, passing, PrivateMetric(metric, if (count > 0) passing.toDouble() / count else 0.0, MetricUnit.RATIO))
    private fun derived(trait: TraitId, supported: Boolean, count: Int, passing: Int, vararg metrics: PrivateMetric) = DerivedTraitMetrics(
        trait, when { !supported -> TraitAvailability.UNSUPPORTED; count == 0 -> TraitAvailability.INSUFFICIENT_EVIDENCE; else -> TraitAvailability.AVAILABLE },
        if (supported && count > 0) metrics.toList() else emptyList(), if (supported) count else 0, if (supported) passing else 0,
    )
    private fun increment(value: Int) = if (value < Int.MAX_VALUE) value + 1 else value
}

private fun VehicleSample.kind(): SignalKind = when (this) {
    is VehicleSample.Speed -> SignalKind.SPEED
    is VehicleSample.Gear -> SignalKind.GEAR
    is VehicleSample.Brake -> SignalKind.BRAKE
    is VehicleSample.Steering -> SignalKind.STEERING_MAGNITUDE
    is VehicleSample.TurnIndicator -> SignalKind.TURN_INDICATOR
    is VehicleSample.Cruise -> SignalKind.CRUISE
    is VehicleSample.SeatBelt -> SignalKind.SEAT_BELT
    is VehicleSample.Clock -> SignalKind.CLOCK
    is VehicleSample.TirePressure -> SignalKind.TIRE_PRESSURE
    is VehicleSample.TireWarning -> SignalKind.TIRE_WARNING
    is VehicleSample.FuelLevel -> SignalKind.FUEL_LEVEL
    is VehicleSample.BatteryLevel -> SignalKind.BATTERY_LEVEL
    is VehicleSample.ChargeLimit -> SignalKind.CHARGE_LIMIT
    is VehicleSample.OilWarning -> SignalKind.OIL_WARNING
    is VehicleSample.BrakeFluidWarning -> SignalKind.BRAKE_FLUID_WARNING
    is VehicleSample.WasherFluidWarning -> SignalKind.WASHER_FLUID_WARNING
    is VehicleSample.AirCleaning -> SignalKind.AIR_CLEANING
    is VehicleSample.Unsupported -> signal
}

private fun VehicleSample.valid(): Boolean = when (this) {
    is VehicleSample.Speed -> metersPerSecond.isFinite() && metersPerSecond in 0.0..100.0
    is VehicleSample.Steering -> degrees.isFinite()
    is VehicleSample.Clock -> localHour in 0..23
    is VehicleSample.TirePressure -> kilopascals.isFinite() && lowPressureThresholdKpa.isFinite() && kilopascals > 0 && lowPressureThresholdKpa > 0
    is VehicleSample.FuelLevel -> percent.isFinite() && percent in 0.0..100.0
    is VehicleSample.BatteryLevel -> percent.isFinite() && percent in 0.0..100.0
    is VehicleSample.ChargeLimit -> percent.isFinite() && percent in 0.0..100.0
    else -> true
}
