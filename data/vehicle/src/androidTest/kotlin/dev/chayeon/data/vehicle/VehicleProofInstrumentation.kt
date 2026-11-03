package dev.chayeon.data.vehicle

import android.annotation.TargetApi
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import dev.chayeon.core.model.*
import dev.chayeon.core.signature.VehicleSample
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.ConcurrentHashMap

/** Uses the same permission-free UX restrictions adapter as the installed application. */
@TargetApi(29)
class VehicleProofInstrumentation : Instrumentation() {
    private var duration = 235_000L
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        duration = arguments?.getString("durationMillis")?.toLongOrNull() ?: duration
        start()
    }

    override fun onStart() {
        fun rowCount(name: String): Int = VehicleDatabase(targetContext, name).use { helper ->
            helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM trips", null).use { it.moveToFirst(); it.getInt(0) }
        }
        val baseline = rowCount("instrumentation-proof.db")
        val foregroundBaseline = rowCount("vehicle-proof.db")
        val foregroundIntent = Intent(targetContext, VehicleProbeService::class.java)
        targetContext.startForegroundService(foregroundIntent)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val gate = MutableRecordingGate()
        val counts = ConcurrentHashMap<String, Int>()
        val policies = ConcurrentHashMap.newKeySet<DrivingPolicy>()
        var latestRecaps = emptyList<TripRecap>()
        lateinit var gateway: PleosVehicleGateway
        lateinit var driving: VehicleDrivingStateProvider
        lateinit var repository: LocalSignatureRepository
        lateinit var recorder: TripRecorder
        runOnMainSync {
            gateway = PleosVehicleGateway(targetContext, scope)
            driving = VehicleDrivingStateProvider(gateway, scope)
            repository = LocalSignatureRepository(targetContext, gate, { driving.current().policy }, scope, databaseName = "instrumentation-proof.db")
            recorder = TripRecorder(gateway, repository, gate, scope)
            scope.launch { driving.drivingState.collect { policies.add(it.policy) } }
            scope.launch { gateway.samples.collect { if (it !is VehicleSample.Unsupported) counts.merge(it.javaClass.simpleName, 1, Int::plus) } }
            scope.launch { repository.recaps.collect { if (it is LoadState.Ready) latestRecaps = it.value } }
            gate.update("instrumentation-proof", true)
            recorder.start()
            gateway.start()
        }
        sendStatus(1, Bundle().apply { putString("status", "ready") })
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < duration) {
            SystemClock.sleep(1_000)
        }
        val report = Bundle()
        runOnMainSync {
            report.putString("policies", policies.sortedBy { it.ordinal }.joinToString())
            report.putString("counts", counts.toSortedMap().toString())
            report.putString("capabilities", gateway.capabilities.value.toString())
            report.putString("safetyFailure", gateway.safetyFailure)
            report.putInt("recaps", latestRecaps.size)
            VehicleDatabase(targetContext, "instrumentation-proof.db").use { helper ->
                helper.readableDatabase.rawQuery("SELECT derived_metrics FROM trips ORDER BY ended_at DESC", null).use { cursor ->
                    report.putInt("storedRows", cursor.count)
                    report.putInt("newRows", cursor.count - baseline)
                    if (cursor.moveToFirst()) {
                        val trip = VehicleStorageCodec.trip(cursor.getString(0))
                        report.putString("eligibility", trip.eligibility.name)
                        report.putBoolean("durationValid", trip.driveDurationMillis >= 180_000)
                        report.putString("traits", trip.traits.associate { it.trait to it.availability }.toString())
                    }
                }
            }
            VehicleDatabase(targetContext, "vehicle-proof.db").use { helper ->
                helper.readableDatabase.rawQuery("SELECT derived_metrics FROM trips ORDER BY ended_at DESC", null).use { cursor ->
                    report.putInt("foregroundStoredRows", cursor.count)
                    report.putInt("foregroundNewRows", cursor.count - foregroundBaseline)
                    if (cursor.moveToFirst()) report.putString("foregroundEligibility", VehicleStorageCodec.trip(cursor.getString(0)).eligibility.name)
                }
            }
            targetContext.stopService(foregroundIntent)
            recorder.close()
            gateway.close()
            report.putString("afterDisconnect", driving.current().policy.name)
            repository.close()
            scope.cancel()
        }
        val passed = report.getString("eligibility") == "ELIGIBLE" && report.getInt("recaps") > 0 &&
            DrivingPolicy.ALLOWED in policies && DrivingPolicy.RESTRICTED in policies && DrivingPolicy.UNKNOWN in policies &&
            report.getString("foregroundEligibility") == "ELIGIBLE" && report.getInt("newRows") == 1 && report.getInt("foregroundNewRows") == 1
        report.putBoolean("passed", passed)
        finish(if (passed) -1 else 0, report)
    }
}
