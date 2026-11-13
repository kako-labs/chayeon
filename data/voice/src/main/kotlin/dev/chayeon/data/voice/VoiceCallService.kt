package dev.chayeon.data.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidCallService(context: Context) : CallService {
    private val context = context.applicationContext
    private val serviceIntent = Intent(this.context, VoiceCallService::class.java)
    override suspend fun start(microphone: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { continuation ->
                val result = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (continuation.isActive) continuation.resume(resultCode == 1)
                    }
                }
                try {
                    context.startForegroundService(Intent(serviceIntent)
                        .putExtra(EXTRA_MICROPHONE, microphone).putExtra(EXTRA_RESULT, result))
                } catch (_: RuntimeException) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } ?: false
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        context.stopService(serviceIntent)
        Unit
    }
}

class VoiceCallService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "차연 통화", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            (application as? VoiceServiceOwner)?.endVoiceCall()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val microphone = intent.getBooleanExtra(EXTRA_MICROPHONE, false) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val end = PendingIntent.getService(this, 0,
            Intent(this, VoiceCallService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("차연 통화 중")
            .setContentText("연결된 상대와 통화를 이어가요")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true).setShowWhen(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "통화 끊기", end).build())
            .build()
        var captureAllowed = microphone
        try {
            val types = if (Build.VERSION.SDK_INT >= 30 && microphone)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, FOREGROUND_ID, notification, types)
        } catch (_: RuntimeException) {
            captureAllowed = false
            try {
                ServiceCompat.startForeground(this, FOREGROUND_ID, notification,
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            } catch (_: RuntimeException) { stopSelf() }
        }
        val result = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT, ResultReceiver::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT)
        result?.send(if (captureAllowed) 1 else 0, null)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_END = "dev.chayeon.voice.END"
        private const val CHANNEL = "chayeon-voice-call"
        private const val FOREGROUND_ID = 43
    }
}

private const val EXTRA_MICROPHONE = "microphone"
private const val EXTRA_RESULT = "result"
