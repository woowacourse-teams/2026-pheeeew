package com.pheeeew.data.config

import com.pheeeew.domain.model.config.isValidNotice
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoticeValidationTest {
    @Test
    fun validatesCodePointLimitAndRejectsUnpairedSurrogates() {
        assertTrue("".isValidNotice())
        assertTrue("가".repeat(200).isValidNotice())
        assertFalse("가".repeat(201).isValidNotice())
        assertTrue("😀".repeat(200).isValidNotice())
        assertFalse("😀".repeat(201).isValidNotice())
        assertFalse("\uDC00".isValidNotice())
        assertFalse("\uD800".isValidNotice())
        assertFalse(("a".repeat(200) + "\uDC00").isValidNotice())
    }
}
