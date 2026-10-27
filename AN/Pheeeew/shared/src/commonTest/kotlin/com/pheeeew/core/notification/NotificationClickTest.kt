package com.pheeeew.core.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationClickTest {
    @Test
    fun `custom data 없는 콘솔 알림은 앱 열기로 처리한다`() {
        assertEquals(
            NotificationClick("request-1", NotificationClickTarget.OpenApp),
            parseNotificationClick(emptyMap(), "request-1"),
        )
    }

    @Test
    fun `컬렉션 ID와 서버 알림 ID를 보존한다`() {
        assertEquals(
            NotificationClick("event-1", NotificationClickTarget.Collection("123")),
            parseNotificationClick(
                mapOf("notificationId" to "event-1", "type" to "COLLECTION_ISSUED", "collectionId" to "123"),
                "request-1",
            ),
        )
    }

    @Test
    fun `일별 프레스 랭킹은 서버 날짜와 이전 현재 순위를 보존한다`() {
        val data = rankingData()
        assertEquals(
            NotificationClickTarget.DailyPressRanking("2026-10-26", 5, 6),
            parseNotificationClick(data, "request-1").target,
        )
        assertEquals(
            NotificationClickTarget.DailyPressRanking("2026-10-26", 5, 6),
            parseNotificationClick(data + ("unused" to "ignored"), "request-1").target,
        )
    }

    @Test
    fun `누락 필드와 잘못된 순위는 안전한 앱 열기로 처리한다`() {
        for (field in listOf("date", "previousRank", "currentRank")) {
            assertEquals(NotificationClickTarget.OpenApp, parseNotificationClick(rankingData() - field, "r").target)
        }
        for (rank in listOf("0", "-1", "not-a-number", "999999999999")) {
            assertEquals(
                NotificationClickTarget.OpenApp,
                parseNotificationClick(rankingData() + ("currentRank" to rank), "r").target,
            )
        }
        assertEquals(
            NotificationClickTarget.OpenApp,
            parseNotificationClick(mapOf("type" to "COLLECTION_ISSUED", "collectionId" to " "), "r").target,
        )
    }

    @Test
    fun `순위 알림의 누락 날짜와 잘못된 날짜는 기본 화면으로 처리한다`() {
        for (date in listOf("", "2026-02-29", "2026-13-01", "2026-04-31", "2026-1-1")) {
            assertEquals(
                NotificationClickTarget.OpenApp,
                parseNotificationClick(rankingData() + ("date" to date), "r").target,
            )
        }
    }

    @Test
    fun `미지원 타입과 버전은 앱 열기로 처리한다`() {
        assertEquals(NotificationClickTarget.OpenApp, parseNotificationClick(mapOf("type" to "UNKNOWN"), "r").target)
        assertEquals(
            NotificationClickTarget.OpenApp,
            parseNotificationClick(rankingData() + ("schemaVersion" to "2"), "r").target,
        )
    }

    @Test
    fun `초기화 전 클릭은 준비 완료 후에만 노출하고 최신 클릭을 유지한다`() {
        val inbox = NotificationClickInbox()
        inbox.receive(click("first"))
        inbox.receive(click("latest"))
        assertNull(inbox.pendingClick.value)
        assertEquals(click("latest"), inbox.queuedClick.value)
        inbox.setReady(true)
        assertEquals(click("latest"), inbox.pendingClick.value)
        inbox.setReady(false)
        assertNull(inbox.pendingClick.value)
        inbox.setReady(true)
        assertEquals(click("latest"), inbox.pendingClick.value)
    }

    @Test
    fun `같은 알림 재클릭은 소비 후에도 중복 처리하지 않는다`() {
        val inbox = NotificationClickInbox()
        inbox.setReady(true)
        inbox.receive(click("same"))
        inbox.acknowledge("same")
        inbox.receive(click("same"))
        assertNull(inbox.pendingClick.value)
        assertNull(inbox.queuedClick.value)
    }

    @Test
    fun `이전 클릭 완료가 더 최근 클릭을 지우지 않는다`() {
        val inbox = NotificationClickInbox()
        inbox.setReady(true)
        inbox.receive(click("first"))
        inbox.receive(click("latest"))
        inbox.acknowledge("first")
        assertEquals(click("latest"), inbox.pendingClick.value)
        assertEquals(click("latest"), inbox.queuedClick.value)
    }

    @Test
    fun `복원 시 완료한 클릭만 중복을 막고 대기 중 클릭은 다시 처리한다`() {
        val saved = ClickStore()
        val original = NotificationClickInbox(saved)
        original.setReady(true)
        original.receive(click("done"))
        original.acknowledge("done")
        original.receive(click("waiting"))

        val restored = NotificationClickInbox(saved)
        assertEquals(click("waiting"), restored.queuedClick.value)
        assertNull(restored.pendingClick.value)
        restored.setReady(true)
        assertEquals(click("waiting"), restored.pendingClick.value)
        restored.receive(click("done"))
        assertEquals(click("waiting"), restored.pendingClick.value)
        restored.acknowledge("waiting")
        val completed = NotificationClickInbox(saved)
        assertNull(completed.queuedClick.value)
        completed.setReady(true)
        completed.receive(click("waiting"))
        assertNull(completed.pendingClick.value)
    }

    @Test
    fun `이전 launch Intent가 복원한 최신 대기 클릭을 덮어쓰지 않는다`() {
        val saved = ClickStore()
        val original = NotificationClickInbox(saved)
        original.receive(click("launch"))
        original.receive(click("new-intent"))
        val restored = NotificationClickInbox(saved)
        restored.receive(click("launch"))
        restored.setReady(true)
        assertEquals(click("new-intent"), restored.pendingClick.value)
    }

    private class ClickStore : NotificationClickStore {
        var snapshot = NotificationClickSnapshot()

        override fun read() = snapshot

        override fun write(snapshot: NotificationClickSnapshot) {
            this.snapshot = snapshot
        }
    }

    private fun click(id: String) = NotificationClick(id, NotificationClickTarget.OpenApp)

    private fun rankingData() =
        mapOf(
            "type" to "RANK_DROPPED",
            "date" to "2026-10-26",
            "previousRank" to "5",
            "currentRank" to "6",
        )
}
