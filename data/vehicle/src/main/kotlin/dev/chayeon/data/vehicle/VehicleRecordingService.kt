package dev.chayeon.data.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class VehicleRecordingService : Service() {
    private var graph: VehicleGraph? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_MIN),
        )
        startForeground(FOREGROUND_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("차연이 주행을 기록하고 있어요")
            .setOngoing(true).setShowWhen(false).build())
        graph = (application as? VehicleGraphOwner)?.vehicleGraph
        if (graph?.gateInput?.permission?.value?.canRecord == true) graph?.recorder?.start()
        else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        graph?.recorder?.close()
        graph = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "chayeon-trip-recording"
        private const val FOREGROUND_ID = 41
    }
}
