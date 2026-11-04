package dev.chayeon.data.vehicle

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The Application supplies its one graph to the recording service. */
interface VehicleGraphOwner { val vehicleGraph: VehicleGraph }

class VehicleGraph(
    context: Context,
    demoEnabled: Boolean = false,
    gatewayFactory: (Context, CoroutineScope) -> VehicleGateway = { app, scope ->
        if (app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) PleosVehicleGateway(app, scope)
        else SimulatedVehicleGateway(scope)
    },
) : AutoCloseable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val gateInput = MutableRecordingGate()
    val gateway = gatewayFactory(app, scope)
    val drivingStateProvider = VehicleDrivingStateProvider(gateway, scope)
    private val homeLocations = PlatformHomeLocationProvider(app)
    val homeZoneRepository = LocalHomeZoneRepository(app, homeLocations, { drivingStateProvider.current().policy }, scope, demoEnabled)
    private val presenceNeeded = MutableStateFlow(false)
    private val locationRequested = MutableStateFlow(false)
    private var locationPreparation: Job? = null
    val signatureRepository = LocalSignatureRepository(app, gateInput, { drivingStateProvider.current().policy }, scope)
    val vehicleConsole = DefaultVehicleConsole(app, gateway, drivingStateProvider, signatureRepository, gateInput, scope, enabled = demoEnabled)
    val recorder = TripRecorder(gateway, signatureRepository, gateInput, scope)
    private var started = false
    private var serviceStarting = false
    private var serviceRunning = false
    private var serviceNeeded = false
    private val foreground = MutableStateFlow(false)
    val recordingFailure = MutableStateFlow(false)

    fun setForeground(resumed: Boolean) { foreground.value = resumed }

    fun setPresenceNeeded(needed: Boolean) { presenceNeeded.value = needed }

    suspend fun updateLocationConsent(accountId: String?, granted: Boolean?) {
        val wasAllowed = locationServiceAllowed()
        if (granted != true) homeLocations.stop()
        homeZoneRepository.updateLocationConsent(accountId, granted)
        if (!locationServiceAllowed()) homeLocations.stop()
        else if (!wasAllowed) permissionsChanged()
    }

    fun prepareHomeLocation() {
        if (homeZoneRepository.locationConsent.value != true) return
        locationRequested.value = true
        permissionsChanged()
        locationPreparation?.cancel()
        locationPreparation = scope.launch {
            delay(12_000)
            if (homeZoneRepository.state.value.radius == null) clearHomeLocationRequest()
        }
    }

    fun clearHomeLocationRequest() { locationRequested.value = false; homeLocations.stop() }

    internal fun serviceStarted() {
        serviceStarting = false
        serviceRunning = true
        if (!serviceNeeded) {
            app.stopService(recordingIntent)
            return
        }
        if (gateInput.permission.value.canRecord) recorder.start() else recorder.close()
        if (locationServiceAllowed()) homeLocations.start() else homeLocations.stop()
    }

    internal fun serviceStopped() {
        serviceStarting = false
        serviceRunning = false
        recorder.close()
        homeLocations.stop()
    }

    internal fun locationServiceAllowed() = homeZoneRepository.locationConsent.value == true && locationRequested.value && homeLocations.hasPermission()

    fun permissionsChanged() {
        if (gateInput.permission.value.canRecord) gateway.setRecordingEnabled(true)
        if (serviceRunning && foreground.value) app.startService(recordingIntent)
    }
    private val recordingIntent = Intent(app, VehicleRecordingService::class.java)

    fun start() {
        if (started) return
        started = true
        gateway.start()
        scope.launch {
            var hadDeviceHome = false
            homeZoneRepository.state.collect { home ->
                val deviceHome = home.radius != null && home.source == dev.chayeon.core.model.LocationSource.DEVICE
                if (deviceHome) locationRequested.value = true
                else if (hadDeviceHome || home.source == dev.chayeon.core.model.LocationSource.SYNTHETIC) clearHomeLocationRequest()
                hadDeviceHome = deviceHome
            }
        }
        scope.launch {
            val permittedLocation = combine(locationRequested, homeZoneRepository.locationConsent) { requested, granted -> requested && granted == true }
            combine(gateInput.permission, foreground, presenceNeeded, permittedLocation, homeZoneRepository.state) { permission, visible, presence, location, home ->
                if (home.radius != null && home.source == dev.chayeon.core.model.LocationSource.DEVICE) locationRequested.value = true
                Triple(permission.canRecord || presence || location || home.suppressed || home.radius != null, visible, permission.canRecord)
            }.distinctUntilChanged().collect { (enabled, visible, recording) ->
                    serviceNeeded = enabled
                    if (!enabled) {
                        recorder.close()
                        // A pending FGS must promote itself before it can be stopped.
                        if (serviceRunning) app.stopService(recordingIntent)
                        recordingFailure.value = false
                    } else if (visible) {
                        try {
                            if (serviceRunning) app.startService(recordingIntent)
                            else if (!serviceStarting) {
                                ContextCompat.startForegroundService(app, recordingIntent)
                                serviceStarting = true
                            }
                            recordingFailure.value = false
                        } catch (_: IllegalStateException) {
                            serviceStarting = false
                            recordingFailure.value = true
                        } catch (_: SecurityException) {
                            serviceStarting = false
                            recordingFailure.value = true
                        }
                    }
                    if (!recording) recorder.close()
                }
        }
    }

    override fun close() {
        gateInput.update(null, false)
        recorder.close()
        app.stopService(recordingIntent)
        vehicleConsole.close()
        gateway.close()
        signatureRepository.close()
        homeZoneRepository.close()
        homeLocations.stop()
        scope.cancel()
    }
}
