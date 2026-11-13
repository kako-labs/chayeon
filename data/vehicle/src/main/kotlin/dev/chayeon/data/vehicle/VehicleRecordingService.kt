package dev.chayeon.data.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat

class VehicleRecordingService : Service() {
    private var graph: VehicleGraph? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_MIN),
        )
        graph = (application as? VehicleGraphOwner)?.vehicleGraph
        promote()
    }

    private fun promote() {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
            (if (graph?.locationServiceAllowed() == true) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) else 0
        ServiceCompat.startForeground(this, FOREGROUND_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("차연이 참여 상태를 유지하고 있어요")
            .setOngoing(true).setShowWhen(false).build(), types)
        graph?.serviceStarted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { promote(); return START_NOT_STICKY }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        graph?.serviceStopped()
        graph = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "chayeon-trip-recording"
        private const val FOREGROUND_ID = 41
    }
}
