package com.pheeeew.legacy.core.monitoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MonitoringTimeTest {
    @Test
    fun seoulDayUsesCalendarDayAtUtcBoundary() {
        val beforeSeoulMidnight = Instant.parse("2026-09-16T14:59:59Z").toEpochMilliseconds()
        val afterSeoulMidnight = Instant.parse("2026-09-16T15:00:00Z").toEpochMilliseconds()

        assertEquals("2026-09-16", MonitoringTime.seoulDay(beforeSeoulMidnight))
        assertEquals("2026-09-17", MonitoringTime.seoulDay(afterSeoulMidnight))
    }

    @Test
    fun isoKeepsUtcInstantRepresentation() {
        val time = Instant.parse("2026-09-17T00:00:00Z").toEpochMilliseconds()

        assertEquals("2026-09-17T00:00:00Z", MonitoringTime.iso(time))
    }
}
