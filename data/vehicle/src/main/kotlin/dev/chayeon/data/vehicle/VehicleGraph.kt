package dev.chayeon.data.vehicle

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val signatureRepository = LocalSignatureRepository(app, gateInput, { drivingStateProvider.current().policy }, scope)
    val vehicleConsole = DefaultVehicleConsole(app, gateway, drivingStateProvider, signatureRepository, gateInput, scope, enabled = demoEnabled)
    val recorder = TripRecorder(gateway, signatureRepository, gateInput, scope)
    private var started = false
    private var serviceStarted = false
    private val foreground = MutableStateFlow(false)
    val recordingFailure = MutableStateFlow(false)

    fun setForeground(resumed: Boolean) { foreground.value = resumed }

    fun permissionsChanged() {
        if (gateInput.permission.value.canRecord) gateway.setRecordingEnabled(true)
    }
    private val recordingIntent = Intent(app, VehicleRecordingService::class.java)

    fun start() {
        if (started) return
        started = true
        gateway.start()
        scope.launch {
            combine(gateInput.permission, foreground) { permission, visible -> permission.canRecord to visible }
                .distinctUntilChanged().collect { (enabled, visible) ->
                    if (!enabled) {
                        recorder.close()
                        app.stopService(recordingIntent)
                        serviceStarted = false
                        recordingFailure.value = false
                    } else if (visible && !serviceStarted) {
                        try {
                            ContextCompat.startForegroundService(app, recordingIntent)
                            serviceStarted = true
                            recordingFailure.value = false
                        } catch (_: IllegalStateException) {
                            recordingFailure.value = true
                        } catch (_: SecurityException) {
                            recordingFailure.value = true
                        }
                    }
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
        scope.cancel()
    }
}
