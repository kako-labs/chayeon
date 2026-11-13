package dev.chayeon.data.vehicle

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.VehicleSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** Debug source set only. Emits counts and derived classifications, never raw vehicle values. */
class VehicleProbeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = MutableRecordingGate()
    private lateinit var gateway: PleosVehicleGateway
    private lateinit var driving: VehicleDrivingStateProvider
    private lateinit var repository: LocalSignatureRepository
    private lateinit var recorder: TripRecorder
    private val received = mutableMapOf<String, Int>()
    private var lastPolicy: DrivingPolicy? = null

    @SuppressLint("PrivateApi")
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("vehicle-proof", "차량 연동 확인", NotificationManager.IMPORTANCE_MIN))
        startForeground(42, Notification.Builder(this, "vehicle-proof").setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("합성 주행으로 차량 연동을 확인하고 있어요").setShowWhen(false).build())
        gateway = PleosVehicleGateway(this, scope)
        driving = VehicleDrivingStateProvider(gateway, scope)
        repository = LocalSignatureRepository(this, gate, { driving.current().policy }, scope, databaseName = "vehicle-proof.db")
        recorder = TripRecorder(gateway, repository, gate, scope)
        scope.launch {
            driving.drivingState.collect { state ->
                if (state.policy != lastPolicy) {
                    Log.i(TAG, "policy=${state.policy} gear=${state.gear} source=${state.source}")
                    lastPolicy = state.policy
                }
            }
        }
        scope.launch {
            gateway.samples.collect { sample ->
                if (sample !is VehicleSample.Unsupported) {
                    val name = sample.javaClass.simpleName
                    received[name] = (received[name] ?: 0) + 1
                }
            }
        }
        scope.launch {
            repository.recaps.filterIsInstance<LoadState.Ready<List<TripRecap>>>().collect {
                Log.i(TAG, "recaps=${it.value.size} included=${it.value.count { recap -> recap.included }}")
            }
        }
        gate.update("emulator-proof", true)
        recorder.start()
        gateway.start()
        try {
            val cls = Class.forName("android.car.drivingstate.CarDrivingStateManager")
            Log.i(TAG, "driving_methods=${cls.methods.filter { it.name.contains("Driving") }.map { it.name }}")
            val event = Class.forName("android.car.drivingstate.CarDrivingStateEvent")
            Log.i(TAG, "driving_fields=${event.fields.map { it.name }}")
        } catch (failure: Exception) { Log.i(TAG, "driving_api=${failure.javaClass.simpleName}") }
        Log.i(TAG, "started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "snapshot" -> snapshot()
            "disconnect" -> gateway.close()
            "reconnect" -> { gateway.start(); gateway.setRecordingEnabled(true) }
            "revoke" -> gate.update("emulator-proof", false)
            "grant" -> gate.update("emulator-proof", true)
            "clear" -> scope.launch { Log.i(TAG, "clear=${repository.clearLocalData() is AppResult.Success}") }
        }
        return START_NOT_STICKY
    }

    private fun snapshot() {
        Log.i(TAG, "received=$received")
        Log.i(TAG, "capabilities=${gateway.capabilities.value.mapValues { it.value.name }}")
        Log.i(TAG, "current=${driving.current().policy} recorder=${recorder.status.value} safety_failure=${gateway.safetyFailure}")
        val db = getDatabasePath("vehicle-proof.db")
        if (!db.exists()) return
        android.database.sqlite.SQLiteDatabase.openDatabase(db.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT derived_metrics FROM trips", null).use { cursor ->
                Log.i(TAG, "stored_rows=${cursor.count}")
                while (cursor.moveToNext()) {
                    val trip = VehicleStorageCodec.trip(cursor.getString(0))
                    Log.i(TAG, "trip eligibility=${trip.eligibility} source=${trip.source} duration_valid=${trip.driveDurationMillis >= 180000}")
                    Log.i(TAG, "traits=${trip.traits.associate { it.trait to it.availability }}")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        recorder.close()
        gateway.close()
        repository.close()
        scope.cancel()
        super.onDestroy()
    }
    companion object { private const val TAG = "ChayeonVehicleProof" }
}
