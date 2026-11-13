package dev.chayeon

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private val notificationRoute = MutableStateFlow<String?>(null)
    private val vehiclePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        (application as ChayeonApplication).vehicleGraph.permissionsChanged()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var microphoneResult: CompletableDeferred<Boolean>? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        (application as ChayeonApplication).microphonePermissionChanged()
        microphoneResult?.complete(granted)
        microphoneResult = null
    }
    private var locationResult: CompletableDeferred<Boolean>? = null
    private val locationPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val app = application as ChayeonApplication
        app.vehicleGraph.prepareHomeLocation()
        locationResult?.complete(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        locationResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ChayeonApplication
        app.graph.prepareMicrophone = {
            if (app.graph.currentPolicy() != DrivingPolicy.ALLOWED || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
            else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) true
            else {
                val result = CompletableDeferred<Boolean>()
                microphoneResult?.complete(false)
                microphoneResult = result
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                result.await()
            }
        }
        app.graph.prepareHomeLocation = {
            if (app.graph.currentPolicy() != DrivingPolicy.ALLOWED) false
            else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                app.vehicleGraph.prepareHomeLocation()
                true
            } else {
                val result = CompletableDeferred<Boolean>()
                locationResult?.complete(false)
                locationResult = result
                locationPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                result.await()
            }
        }
        notificationRoute.value = intent.getStringExtra(REQUEST_DESTINATION)
        setContent { ChayeonApp(app.graph, events = app.events, publisherFailure = app.publisher.failure,
            recordingFailure = app.vehicleGraph.recordingFailure, notificationRoute = notificationRoute,
            consumeNotification = { notificationRoute.value = null }) }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationRoute.value = intent.getStringExtra(REQUEST_DESTINATION)
    }

    override fun onResume() {
        super.onResume()
        (application as ChayeonApplication).microphonePermissionChanged()
        (application as ChayeonApplication).vehicleGraph.setForeground(true)
    }

    override fun onPause() {
        (application as ChayeonApplication).vehicleGraph.setForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        microphoneResult?.complete(false)
        (application as ChayeonApplication).graph.prepareMicrophone = { false }
        locationResult?.complete(false)
        (application as ChayeonApplication).graph.prepareHomeLocation = { false }
        super.onDestroy()
    }
}
