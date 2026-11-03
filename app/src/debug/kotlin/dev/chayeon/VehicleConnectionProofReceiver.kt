package dev.chayeon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
        }
    }
}
