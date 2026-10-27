package com.pheeeew.core.notification

import com.pheeeew.core.navigation.NotificationClickRouter
import com.pheeeew.core.navigation.NotificationDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationClickRouterTest {
    @Test
    fun `순위 알림은 날짜별 순위로 이동하고 나머지는 기존 목적지를 사용한다`() {
        val targets =
            listOf(
                NotificationClickTarget.OpenApp,
                NotificationClickTarget.Revisit,
                NotificationClickTarget.Collection("collection-1"),
                NotificationClickTarget.Ranking("2026-10-21", 5, 6),
            )
        targets.forEach { target ->
            if (target is NotificationClickTarget.Ranking) {
                assertEquals(
                    NotificationDestination.DailyRanking("2026-10-21"),
                    NotificationClickRouter.destinationFor(target),
                )
            } else {
                assertEquals(NotificationDestination.Map, NotificationClickRouter.destinationFor(target))
            }
        }
    }

    @Test
    fun `미지원 payload도 안전한 지도 목적지로 연결한다`() {
        val click = parseNotificationClick(mapOf("type" to "UNKNOWN"), "request-1")
        assertEquals(NotificationDestination.Map, NotificationClickRouter.destinationFor(click.target))
    }
}
