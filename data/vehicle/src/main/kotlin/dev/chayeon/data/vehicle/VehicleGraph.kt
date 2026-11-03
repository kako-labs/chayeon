package dev.chayeon.data.vehicle

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The Application supplies its one graph to the recording service. */
interface VehicleGraphOwner { val vehicleGraph: VehicleGraph }

class VehicleGraph(
    context: Context,
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
    val vehicleConsole = DefaultVehicleConsole(app, gateway, drivingStateProvider, signatureRepository, gateInput, scope)
    val recorder = TripRecorder(gateway, signatureRepository, gateInput, scope)
    private var started = false
    private val recordingIntent = Intent(app, VehicleRecordingService::class.java)

    fun start() {
        if (started) return
        started = true
        gateway.start()
        scope.launch {
            gateInput.permission.map { it.canRecord }.distinctUntilChanged().collect { enabled ->
                if (enabled) {
                    try { ContextCompat.startForegroundService(app, recordingIntent) }
                    catch (_: IllegalStateException) { recorder.close() }
                    catch (_: SecurityException) { recorder.close() }
                } else {
                    recorder.close()
                    app.stopService(recordingIntent)
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
