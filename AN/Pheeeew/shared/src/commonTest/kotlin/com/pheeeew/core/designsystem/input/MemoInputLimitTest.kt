package com.pheeeew.core.designsystem.input

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoInputLimitTest {
    @Test
    fun overflowFromKoreanCompositionKeepsTheFiftiethCharacter() {
        val prefix = "가".repeat(49)
        assertEquals(prefix + "가", limitMemoText(prefix + "가나"))
    }

    @Test
    fun longPasteKeepsTheAcceptedPrefix() {
        assertEquals("1234567890".repeat(5), limitMemoText("1234567890".repeat(6)))
    }

    @Test
    fun emojiAtTheBoundaryIsNotSplitIntoAnInvalidSurrogate() {
        val prefix = "가".repeat(49)
        assertEquals(prefix, limitMemoText(prefix + "😀"))
    }

    @Test
    fun completeEmojiWithinTheLimitIsPreserved() {
        val text = "가".repeat(48) + "😀"
        assertEquals(text, limitMemoText(text))
    }
}
