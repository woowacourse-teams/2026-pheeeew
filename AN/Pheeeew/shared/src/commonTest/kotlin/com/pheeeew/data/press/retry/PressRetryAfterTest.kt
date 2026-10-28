package com.pheeeew.data.press.retry

import kotlin.test.Test
import kotlin.test.assertEquals

class PressRetryAfterTest {
    @Test
    fun `seconds and HTTP dates preserve the server minimum`() {
        assertEquals(5_000L, pressRetryAfter(" 5 ", 0L).value)
        assertEquals(120_000L, pressRetryAfter("120", 0L).value)
        assertEquals(5_000L, pressRetryAfter("Thu, 01 Jan 1970 00:00:05 GMT", 0L).value)
        assertEquals(0L, pressRetryAfter("Thu, 01 Jan 1970 00:00:05 GMT", 6_000L).value)
    }

    @Test
    fun `invalid values use backoff and oversized seconds do not overflow`() {
        listOf(null, "", "invalid", "-1", "1.5", "+5").forEach {
            assertEquals(0L, pressRetryAfter(it, 0L).value, it)
        }
        assertEquals(Long.MAX_VALUE, pressRetryAfter(Long.MAX_VALUE.toString(), 0L).value)
        assertEquals(Long.MAX_VALUE, pressRetryAfter("999999999999999999999", 0L).value)
    }
}
