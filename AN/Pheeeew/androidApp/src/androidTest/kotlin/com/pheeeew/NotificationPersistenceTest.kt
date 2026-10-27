package com.pheeeew

import androidx.test.platform.app.InstrumentationRegistry
import com.pheeeew.core.notification.NotificationClick
import com.pheeeew.core.notification.NotificationClickInbox
import com.pheeeew.core.notification.NotificationClickSnapshot
import com.pheeeew.core.notification.NotificationClickTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NotificationPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun latestPendingClickSurvivesStoreRecreationAndOldLaunchIntent() {
        val environment = "test-${UUID.randomUUID()}"
        try {
            val original = NotificationClickInbox(AndroidNotificationClickStore(context, environment))
            val first = NotificationClick("launch", NotificationClickTarget.Revisit)
            val latest = NotificationClick("new-intent", NotificationClickTarget.DailyPressRanking("2026-10-21", 5, 6))
            original.receive(first)
            original.receive(latest)
            val restored = NotificationClickInbox(AndroidNotificationClickStore(context, environment))
            restored.receive(first)
            restored.setReady(true)
            assertEquals(latest, restored.pendingClick.value)
            restored.acknowledge(latest.notificationId)
            val completed = NotificationClickInbox(AndroidNotificationClickStore(context, environment))
            completed.setReady(true)
            completed.receive(latest)
            assertNull(completed.pendingClick.value)
        } finally {
            context.deleteSharedPreferences("notification-clicks-$environment")
        }
    }

    @Test
    fun clickStorageRoundTripsEveryNormalizedTarget() {
        val environment = "test-${UUID.randomUUID()}"
        try {
            val targets =
                listOf(
                    NotificationClickTarget.OpenApp,
                    NotificationClickTarget.Revisit,
                    NotificationClickTarget.Collection("123"),
                    NotificationClickTarget.CollectionClaimable("2026-10-21"),
                    NotificationClickTarget.DailyPressRanking("2026-10-21", 5, 6),
                )
            targets.forEachIndexed { index, target ->
                val id = "event-$index"
                val snapshot = NotificationClickSnapshot(listOf(id), NotificationClick(id, target))
                AndroidNotificationClickStore(context, environment).write(snapshot)
                assertEquals(snapshot, AndroidNotificationClickStore(context, environment).read())
            }
        } finally {
            context.deleteSharedPreferences("notification-clicks-$environment")
        }
    }

    @Test
    fun newerTokenRemainsPendingWhenOldSynchronizationCompletes() =
        runBlocking {
            val environment = "test-${UUID.randomUUID()}"
            try {
                val store = AndroidPushTokenStore(context, environment)
                store.recordReceived("old")
                val oldVersion = store.version()
                store.recordReceived("new")
                store.write("old")
                store.acknowledge(oldVersion)
                val restored = AndroidPushTokenStore(context, environment)
                assertEquals("new", restored.read())
                assertTrue(restored.isPending())
                restored.acknowledge(restored.version())
                assertFalse(restored.isPending())
            } finally {
                context.deleteSharedPreferences("push-token-$environment")
            }
        }

    @Test
    fun revisitClaimsSurviveRestartAndResetForNewVisit() {
        val environment = "test-${UUID.randomUUID()}"
        try {
            val store = AndroidRevisitNotificationStore(context, environment)
            store.saveLastAccess(100L)
            assertEquals(AndroidRevisitNotificationStore.Claim.CLAIMED, store.claim(0))
            val restored = AndroidRevisitNotificationStore(context, environment)
            assertEquals(AndroidRevisitNotificationStore.Claim.ALREADY_CLAIMED, restored.claim(0))
            assertEquals(AndroidRevisitNotificationStore.Claim.CLAIMED, restored.claim(29))
            restored.saveLastAccess(200L)
            assertEquals(AndroidRevisitNotificationStore.Claim.CLAIMED, restored.claim(0))
        } finally {
            context.deleteSharedPreferences("revisit-notifications-$environment")
        }
    }
}
