package dev.chayeon.data.vehicle

import dev.chayeon.core.model.*
import kotlin.math.*
import kotlin.random.Random

internal class PrivatePoint(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    override fun toString() = "[redacted]"

    fun distanceTo(other: PrivatePoint): Double {
        val lat = Math.toRadians(other.latitude - latitude)
        val lon = Math.toRadians(other.longitude - longitude)
        val a = sin(lat / 2).pow(2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun offset(meters: Double, bearing: Double): PrivatePoint {
        val angular = meters / 6_371_000.0
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        val shiftedLat = asin(sin(lat) * cos(angular) + cos(lat) * sin(angular) * cos(bearing))
        val shiftedLon = lon + atan2(sin(bearing) * sin(angular) * cos(lat), cos(angular) - sin(lat) * sin(shiftedLat))
        return PrivatePoint(Math.toDegrees(shiftedLat), (Math.toDegrees(shiftedLon) + 540) % 360 - 180)
    }
}

internal class HomeRecord(val center: PrivatePoint, val radius: HomeZoneRadius, val source: LocationSource) {
    override fun toString() = "[redacted]"
    fun withRadius(value: HomeZoneRadius) = HomeRecord(center, value, source)

    companion object {
        fun jitter(point: PrivatePoint, radius: HomeZoneRadius, source: LocationSource, random: Random): HomeRecord {
            require(point.valid) { "Invalid position" }
            return HomeRecord(point.offset(sqrt(random.nextDouble()) * radius.meters * 0.25, random.nextDouble() * 2 * PI), radius, source)
        }
    }
}

internal sealed interface ZoneLocation {
    class Fix(val point: PrivatePoint, val observedAtMillis: Long, val accuracyMeters: Double = 0.0) : ZoneLocation {
        override fun toString() = "[redacted]"
    }
    data object Unknown : ZoneLocation
    data object PermissionOff : ZoneLocation
}

/** Monotonic time only. Any uncertainty resets the continuous-outside interval. */
internal class HomeZoneEvaluator(
    private val exitDelayMillis: () -> Long = { Random.nextLong(30_000, 90_001) },
    private val freshMillis: Long = 30_000,
) {
    private var home: HomeRecord? = null
    private var outsideSince: Long? = null
    private var exitDelay = 0L
    private var outside = false

    fun reset(record: HomeRecord?) {
        home = record
        outsideSince = null
        outside = false
    }

    fun evaluate(location: ZoneLocation, now: Long): HomeZoneState {
        val record = home ?: return HomeZoneState()
        fun state(status: HomeZoneStatus) = HomeZoneState(status, record.radius, record.source)
        val fix = location as? ZoneLocation.Fix
        if (fix == null || !fix.point.valid || !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0 ||
            now < fix.observedAtMillis || now - fix.observedAtMillis > freshMillis) {
            outsideSince = null
            outside = false
            return state(if (location == ZoneLocation.PermissionOff) HomeZoneStatus.PERMISSION_OFF else HomeZoneStatus.LOCATION_UNKNOWN)
        }
        // Accuracy is a bound: only a fix whose whole circle is outside can release protection.
        val distance = record.center.distanceTo(fix.point) - fix.accuracyMeters
        if (distance <= record.radius.meters) {
            outside = false
            outsideSince = null
        } else if (!outside) {
            if (distance > record.radius.meters + 50) {
                val began = outsideSince ?: now.also {
                    outsideSince = it
                    exitDelay = exitDelayMillis().coerceAtLeast(0)
                }
                if (now - began >= exitDelay) outside = true
            } else outsideSince = null
        }
        return state(if (outside) HomeZoneStatus.OUTSIDE else HomeZoneStatus.INSIDE)
    }
}
