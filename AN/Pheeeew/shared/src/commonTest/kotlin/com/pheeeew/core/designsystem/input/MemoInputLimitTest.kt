package com.pheeeew.core.designsystem.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

    @Test
    fun insertionIntoFullMemoPreservesExistingSuffix() {
        val original = "0123456789".repeat(5)
        val result = TextFieldValue(original.take(10) + "가" + original.drop(10), TextRange(11)).limitMemoValue()
        assertEquals(original, result.text)
        assertEquals(TextRange(10), result.selection)
    }

    @Test
    fun middlePasteAcceptsOnlyAvailableSpace() {
        val original = "0123456789".repeat(4)
        val result =
            TextFieldValue(
                original.take(10) + "가".repeat(20) + original.drop(10),
                TextRange(30),
            ).limitMemoValue()
        assertEquals(original.take(10) + "가".repeat(10) + original.drop(10), result.text)
        assertEquals(TextRange(20), result.selection)
    }

    @Test
    fun middleEmojiOverflowPreservesSuffixAndValidUnicode() {
        val original = "가".repeat(49)
        val result = TextFieldValue(original.take(10) + "😀" + original.drop(10), TextRange(12)).limitMemoValue()
        assertEquals(original, result.text)
        assertEquals(TextRange(10), result.selection)
    }
}
