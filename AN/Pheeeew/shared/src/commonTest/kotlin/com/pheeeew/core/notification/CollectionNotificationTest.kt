package com.pheeeew.core.notification

import com.pheeeew.core.navigation.NotificationClickRouter
import com.pheeeew.core.navigation.NotificationDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionNotificationTest {
    @Test
    fun `두 서버 타입은 카드 ID 없이 날짜로 컬렉션에 이동한다`() {
        listOf("COLLECTION_CLAIMABLE", "COLLECTION_CLAIM_REMINDER").forEach { type ->
            val click = parseNotificationClick(mapOf("type" to type, "date" to "2026-10-26"), type)
            assertEquals(NotificationClickTarget.CollectionClaimable("2026-10-26"), click.target)
            assertEquals(
                NotificationDestination.Collection("2026-10-26"),
                NotificationClickRouter.destinationFor(click.target),
            )
        }
    }

    @Test
    fun `누락과 불가능한 날짜는 기본 화면으로 처리한다`() {
        listOf("", "2026-02-29", "2026-13-01", "2026-04-31", "26-10-26").forEach { date ->
            assertEquals(
                NotificationClickTarget.OpenApp,
                parseNotificationClick(
                    mapOf(
                        "type" to "COLLECTION_CLAIMABLE",
                        "date" to date,
                    ),
                    "invalid",
                ).target,
            )
        }
        assertTrue(isCollectionNotificationDate("2028-02-29"))
        assertFalse(isCollectionNotificationDate("1900-02-29"))
    }

    @Test
    fun `포그라운드 수신 중복은 한번만 전달하고 클릭은 별도로 처리한다`() {
        val data = mapOf("type" to "COLLECTION_CLAIMABLE", "date" to "2026-10-26")
        assertTrue(CollectionNotificationEvents.receive(data, "foreground-collection-test"))
        val revision = CollectionNotificationEvents.event.value?.revision
        assertTrue(CollectionNotificationEvents.receive(data, "foreground-collection-test"))
        assertEquals(revision, CollectionNotificationEvents.event.value?.revision)
        assertFalse(CollectionNotificationEvents.receive(mapOf("type" to "UNKNOWN"), "unknown"))
        val inbox = NotificationClickInbox()
        inbox.setReady(true)
        inbox.receive(parseNotificationClick(data, "foreground-collection-test"))
        assertEquals("foreground-collection-test", inbox.pendingClick.value?.notificationId)
    }
}
