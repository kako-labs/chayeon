package dev.chayeon

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNotificationsTest {
    @Test fun cancellingUsesThePostedIdAndKeepsOtherNoDetailNotifications() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AppNotifications(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        notifier.notifyMessage("viewed")
        notifier.notifyMessage("unread")
        assertEquals(2, manager.activeNotifications.size)
        manager.activeNotifications.forEach { posted ->
            assertEquals(Notification.CATEGORY_MESSAGE, posted.notification.category)
            assertEquals("차연에 새 소식이 있어요", posted.notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("주차하면 확인할 수 있어요", posted.notification.extras.getString(Notification.EXTRA_TEXT))
        }
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.cancelMessage("viewed")
        assertEquals(listOf("unread".hashCode()), manager.activeNotifications.map { it.id })
        notifier.cancelMessage("viewed")
        assertEquals(1, manager.activeNotifications.size)
        notifier.cancelMessage("unread")
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
