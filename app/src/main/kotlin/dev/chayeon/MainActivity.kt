package dev.chayeon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.chayeon.core.model.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vehiclePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        (application as ChayeonApplication).vehicleGraph.permissionsChanged()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ChayeonApplication
        setContent { ChayeonApp(app.graph, events = app.events, publisherFailure = app.publisher.failure, recordingFailure = app.vehicleGraph.recordingFailure) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(app.graph.profileRepository.settings, app.graph.drivingStateProvider.drivingState) { settings, driving ->
                    (settings as? LoadState.Ready)?.value to driving.policy
                }.collect { (settings, policy) ->
                    if (policy != DrivingPolicy.ALLOWED) return@collect
                    val granted = settings?.consents?.firstOrNull { it.scope == ConsentScope.VEHICLE_DATA && it.status == ConsentStatus.GRANTED }
                    val preferences = getSharedPreferences("permission-requests", MODE_PRIVATE)
                    if (granted != null && preferences.getString("vehicle-consent", null) != granted.id + granted.recordedAtEpochMillis) {
                        preferences.edit { putString("vehicle-consent", granted.id + granted.recordedAtEpochMillis) }
                        val missing = arrayOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_ENERGY")
                            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isNotEmpty()) vehiclePermissions.launch(missing.toTypedArray())
                    }
                    val publicGranted = settings?.consents?.any { it.scope == ConsentScope.PUBLIC_PROFILE && it.status == ConsentStatus.GRANTED } == true
                    if (publicGranted && Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("notifications", false)) {
                        preferences.edit { putBoolean("notifications", true) }
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as ChayeonApplication).vehicleGraph.setForeground(true)
    }

    override fun onPause() {
        (application as ChayeonApplication).vehicleGraph.setForeground(false)
        super.onPause()
    }
}
