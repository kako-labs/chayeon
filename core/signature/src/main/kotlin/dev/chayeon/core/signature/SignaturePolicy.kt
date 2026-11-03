package dev.chayeon.core.signature

/** Product thresholds, not safety or medical advice. Units are explicit at each boundary. */
object SignaturePolicy {
    const val MIN_DRIVE_DURATION_MILLIS = 180_000L
    const val MIN_MOVEMENT_METERS = 2_000.0
    const val MIN_SPEED_COVERAGE_RATIO = .80
    const val WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1_000
    const val MIN_VALID_TRIPS = 3
    const val VISIBLE_RATIO = .60
    const val STEADY_RATIO = .75
    const val SIGNATURE_RATIO = .90
    const val ABRUPT_ACCELERATION_METERS_PER_SECOND_SQUARED = 2.5
    const val LONG_TRIP_METERS = 30_000.0
    const val NIGHT_START_HOUR = 21
    const val NIGHT_END_HOUR = 5
    const val MIN_ENERGY_PERCENT = 15.0
    const val MAX_CHARGE_LIMIT_PERCENT = 90.0

    // Explicit implementation parameters where the product specification leaves signal processing open.
    const val MAX_SIGNAL_GAP_MILLIS = 5_000L
    const val MAX_COVERED_SPEED_INTERVAL_MILLIS = 1_000L
    const val SPEED_SMOOTHING_MILLIS = 500.0
    const val ABRUPT_REARM_ACCELERATION = 2.0
    const val MAX_GENTLE_EVENTS_PER_TEN_MINUTES = 1.0
    const val TURN_START_DEGREES = 15.0
    const val TURN_END_DEGREES = 7.0
    const val TURN_MIN_SPEED_METERS_PER_SECOND = 2.0
    const val INDICATOR_WINDOW_MILLIS = 3_000L
    const val STEADY_MIN_SPEED_METERS_PER_SECOND = 5.0
    const val STEADY_MAX_ACCELERATION = .5
    const val STEADY_SEGMENT_MILLIS = 30_000L
    const val MAX_STEADY_VARIATION_COEFFICIENT = .10
    const val MIN_CRUISE_USAGE_RATIO = .60
}
