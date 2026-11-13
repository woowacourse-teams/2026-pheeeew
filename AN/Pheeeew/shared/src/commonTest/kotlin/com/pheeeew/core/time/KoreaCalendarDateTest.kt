package com.pheeeew.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class KoreaCalendarDateTest {
    @Test
    fun `date changes at Korea midnight rather than UTC midnight`() {
        val beforeKoreaMidnight = Instant.parse("2026-10-08T14:59:00Z").toEpochMilliseconds()
        val afterKoreaMidnight = beforeKoreaMidnight + 120_000L

        assertEquals("2026-10-08", koreaCalendarDate(beforeKoreaMidnight))
        assertEquals("2026-10-09", koreaCalendarDate(afterKoreaMidnight))
    }
}
