package dev.chayeon.data.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal interface HomeLocationProvider {
    val location: StateFlow<ZoneLocation>
    fun start()
    fun stop()
    fun hasPermission(): Boolean
}

/** Exact fixes live only here and in the evaluator; nothing is forwarded to social. */
internal class PlatformHomeLocationProvider(context: Context, private val now: () -> Long = SystemClock::elapsedRealtime) : HomeLocationProvider, LocationListener {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    override val location = MutableStateFlow<ZoneLocation>(ZoneLocation.Unknown)
    private var running = false

    override fun hasPermission() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!hasPermission()) {
            stop()
            location.value = ZoneLocation.PermissionOff
            return
        }
        if (running) return
        try {
            val providers = manager?.getProviders(true).orEmpty().filter { it != LocationManager.PASSIVE_PROVIDER }
            if (providers.isEmpty()) { location.value = ZoneLocation.Unknown; return }
            providers.forEach { manager?.requestLocationUpdates(it, 1_000L, 0f, this, Looper.getMainLooper()) }
            running = true
        } catch (_: Exception) {
            stop()
            location.value = if (hasPermission()) ZoneLocation.Unknown else ZoneLocation.PermissionOff
        }
    }

    override fun stop() {
        try { manager?.removeUpdates(this) } catch (_: Exception) { }
        running = false
        location.value = ZoneLocation.Unknown
    }

    override fun onLocationChanged(location: Location) {
        if (!hasPermission()) { stop(); this.location.value = ZoneLocation.PermissionOff; return }
        val next = if (location.hasAccuracy()) ZoneLocation.Fix(
            PrivatePoint(location.latitude, location.longitude), location.elapsedRealtimeNanos / 1_000_000, location.accuracy.toDouble(),
        ) else ZoneLocation.Unknown
        val current = this.location.value as? ZoneLocation.Fix
        if (current != null && current.point.valid && current.accuracyMeters.isFinite() && current.accuracyMeters >= 0 &&
            now() - current.observedAtMillis in 0 until 5_000 && next is ZoneLocation.Fix &&
            next.accuracyMeters > current.accuracyMeters) return
        this.location.value = next
    }

    override fun onProviderDisabled(provider: String) { location.value = ZoneLocation.Unknown }
    override fun onProviderEnabled(provider: String) { stop(); start() }
    @Deprecated("Legacy provider callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { location.value = ZoneLocation.Unknown }
    override fun toString() = "[redacted]"
}
