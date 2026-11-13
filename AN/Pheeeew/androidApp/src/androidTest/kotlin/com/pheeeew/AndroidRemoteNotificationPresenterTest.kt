package com.pheeeew

import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Run on an isolated emulator with POST_NOTIFICATIONS granted. Only this test's tags are removed. */
class AndroidRemoteNotificationPresenterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun foregroundNotificationUsesSystemTrayAndDistinctTapIdentities() {
        assumeTrue(manager.areNotificationsEnabled())
        val channel = context.getString(R.string.remote_notification_channel_id)
        assumeTrue(manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE)
        val prefix = "review-${UUID.randomUUID()}"
        val firstId = "$prefix-a"
        val secondId = "$prefix-b"
        try {
            val presenter = AndroidRemoteNotificationPresenter(context)
            presenter.show(message(firstId))
            presenter.show(message(secondId))
            presenter.show(message(firstId))
            val notifications = awaitNotifications(prefix)
            assertEquals(2, notifications.size)
            val first = notifications.single { it.tag == "remote-$firstId" }.notification
            val second = notifications.single { it.tag == "remote-$secondId" }.notification
            assertEquals(channel, first.channelId)
            assertEquals("순위 변경", first.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("현재 순위를 확인해요.", first.extras.getString(Notification.EXTRA_TEXT))
            assertNotNull(first.contentIntent)
            assertNotEquals(first.contentIntent, second.contentIntent)
            assertTrue(first.flags and Notification.FLAG_AUTO_CANCEL != 0)
        } finally {
            manager.cancel("remote-$firstId", 0)
            manager.cancel("remote-$secondId", 0)
        }
    }

    @Test
    fun dataOnlyMessageDoesNotCreateAnExtraSystemNotification() {
        val id = "review-${UUID.randomUUID()}"
        AndroidRemoteNotificationPresenter(context).show(
            RemoteMessage(
                Bundle().apply {
                    putString("google.message_id", id)
                    putString("type", "RANK_DROPPED")
                },
            ),
        )
        assertTrue(manager.activeNotifications.none { it.tag == "remote-$id" })
    }

    private fun message(id: String) =
        RemoteMessage(
            Bundle().apply {
                putString("google.message_id", id)
                putString("gcm.n.e", "1")
                putString("gcm.n.title", "순위 변경")
                putString("gcm.n.body", "현재 순위를 확인해요.")
                putString("type", "RANK_DROPPED")
                putString("date", "2026-10-21")
                putString("previousRank", "5")
                putString("currentRank", "6")
            },
        )

    private fun awaitNotifications(prefix: String): List<StatusBarNotification> {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val notifications = manager.activeNotifications.filter { it.tag?.startsWith("remote-$prefix") == true }
            if (notifications.size == 2) return notifications
            SystemClock.sleep(50)
        }
        error("System notifications were not posted")
    }
}
