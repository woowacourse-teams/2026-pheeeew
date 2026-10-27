package com.pheeeew.core.notification

import com.pheeeew.core.navigation.NotificationClickRouter
import com.pheeeew.core.navigation.NotificationDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationClickRouterTest {
    @Test
    fun `기본 알림과 기존 컬렉션 타입은 지도 이동 정책을 사용한다`() {
        val targets =
            listOf(
                NotificationClickTarget.OpenApp,
                NotificationClickTarget.Revisit,
                NotificationClickTarget.Collection("collection-1"),
            )
        targets.forEach { target ->
            assertEquals(NotificationDestination.Map, NotificationClickRouter.destinationFor(target))
        }
    }

    @Test
    fun `순위 하락 알림은 바로 누를 수 있는 프레스 화면에 연결한다`() {
        assertEquals(
            NotificationDestination.Press,
            NotificationClickRouter.destinationFor(NotificationClickTarget.DailyPressRanking("2026-10-26", 5, 6)),
        )
    }

    @Test
    fun `미지원 payload도 안전한 지도 목적지로 연결한다`() {
        val click = parseNotificationClick(mapOf("type" to "UNKNOWN"), "request-1")
        assertEquals(NotificationDestination.Map, NotificationClickRouter.destinationFor(click.target))
    }
}
