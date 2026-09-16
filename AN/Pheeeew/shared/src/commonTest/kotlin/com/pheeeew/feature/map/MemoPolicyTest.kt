package com.pheeeew.feature.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoPolicyTest {
    @Test
    fun `memo is trimmed and blank input becomes null`() {
        assertEquals("한숨 기록", MemoPolicy.normalize("  한숨 기록  "))
        assertEquals(null, MemoPolicy.normalize("   "))
    }

    @Test
    fun `memo over fifty UTF-16 code units is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MemoPolicy.normalize("a".repeat(MAX_MEMO_LENGTH + 1))
        }
    }
}
