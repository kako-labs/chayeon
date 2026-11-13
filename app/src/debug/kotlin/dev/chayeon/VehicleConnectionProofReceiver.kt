package dev.chayeon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Disconnects only this app's client, never the shared car service or emulator. */
class VehicleConnectionProofReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as ChayeonApplication).vehicleGraph
        when (intent.getStringExtra("operation")) {
            "disconnect" -> graph.gateway.close()
            "reconnect" -> {
                graph.gateway.start()
                graph.permissionsChanged()
            }
            "synthetic-home-inside", "synthetic-home-outside" -> if (BuildConfig.DEMO_CONTROLS_ENABLED) {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    try { graph.homeZoneRepository.setSyntheticPosition(intent.getStringExtra("operation") == "synthetic-home-inside", externalProof = true) }
                    finally { pending.finish() }
                }
            }
        }
    }
}
