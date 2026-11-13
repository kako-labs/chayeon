package dev.chayeon.data.voice

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoicePlatformTest {
    @Test fun deniedMicrophoneStartsReceiveOnlyCallNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildService(VoiceCallService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent(app, VoiceCallService::class.java).putExtra("microphone", true), 0, 1)
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, service.foregroundServiceType)
            val notification = shadowOf(service).lastForegroundNotification
            assertEquals(Notification.CATEGORY_CALL, notification.category)
            assertEquals(Notification.FLAG_ONGOING_EVENT, notification.flags and Notification.FLAG_ONGOING_EVENT)
            assertEquals("통화 끊기", notification.actions.single().title.toString())
            assertEquals(VoiceCallService.ACTION_END, shadowOf(notification.actions.single().actionIntent).savedIntent.action)
        } finally { controller.destroy() }
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test fun grantedMicrophoneSelectsMicrophoneForegroundType() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildService(VoiceCallService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent(app, VoiceCallService::class.java).putExtra("microphone", true), 0, 1)
            assertTrue(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0)
        } finally { controller.destroy() }
    }

    @Test fun navigationDuckingRetainsCallFocusButLossSuspendsIt() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(AudioManager::class.java)
        val audio = AndroidVoiceAudio(app)
        try {
            audio.start()
            val listener = checkNotNull(shadowOf(manager).lastAudioFocusRequest).listener
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            assertTrue(audio.focusAvailable.value)
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertFalse(audio.focusAvailable.value)
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            assertTrue(audio.focusAvailable.value)
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            assertFalse(audio.focusAvailable.value)
        } finally { audio.stop() }
    }

    @Test fun communicationModeAndAudioFocusAreReleased() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(AudioManager::class.java)
        manager.mode = AudioManager.MODE_NORMAL
        val audio = AndroidVoiceAudio(app)
        try {
            audio.start()
            assertEquals(AudioManager.MODE_IN_COMMUNICATION, manager.mode)
            assertNotNull(shadowOf(manager).lastAudioFocusRequest)
        } finally { audio.stop() }
        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        assertNotNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
        Unit
    }
}
