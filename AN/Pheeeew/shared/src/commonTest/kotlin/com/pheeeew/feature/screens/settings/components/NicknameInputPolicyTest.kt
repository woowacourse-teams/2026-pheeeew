package com.pheeeew.feature.screens.settings.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pheeeew.core.designsystem.input.TextEditor
import com.pheeeew.domain.model.device.DeviceNicknamePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NicknameInputPolicyTest {
    @Test
    fun normalizesLineSeparatorsAndPreservesOrdinarySpaces() {
        assertEquals(
            "Sunny 고양이 이름",
            normalizeNicknameInput("Sunny\r\n고양이\u2028이름"),
        )
        assertEquals("  Sunny 고양이  ", normalizeNicknameInput("  Sunny 고양이  "))
        assertEquals("a b c d e", normalizeNicknameInput("a\rb\nc\u0085d\u2029e"))
    }

    @Test
    fun appliesExistingLengthLimitAfterNormalizationWithoutTruncating() {
        val nickname = acceptedNicknameInput("12345\r\n6789")

        assertEquals("12345 6789", nickname)
        assertEquals(10, DeviceNicknamePolicy.count(nickname.orEmpty()))
        assertNull(acceptedNicknameInput("12345678901"))
    }

    @Test
    fun normalizingPastedLineBreaksKeepsSelectionAndCompositionOffsets() {
        val value = TextFieldValue("가\r\n나다", TextRange(5, 3), TextRange(3, 5)).normalizeNicknameValue()

        assertEquals("가 나다", value.text)
        assertEquals(TextRange(4, 2), value.selection)
        assertEquals(TextRange(2, 4), value.composition)
    }

    @Test
    fun unchangedKoreanCompositionKeepsItsSelectionAndMarkedRange() {
        val composing = TextFieldValue("왈라랄", TextRange(3), TextRange(2, 3))

        assertEquals(composing, composing.normalizeNicknameValue())
    }

    @Test
    fun delayedNicknameEchoDoesNotRollBackFastKoreanInputOrMiddleCaret() {
        val editor = TextEditor("")
        editor.state.edit { append("왈") }
        editor.publish("왈")
        editor.state.edit {
            append("라랄")
            selection = TextRange(1)
        }
        editor.publish("왈라랄")

        editor.synchronize("왈")
        editor.synchronize("왈라랄")

        assertEquals("왈라랄", editor.state.text.toString())
        assertEquals(TextRange(1), editor.state.selection)
    }

    @Test
    fun rejectsOverLimitCandidatesAndAcceptsSelectionReplacement() {
        val text = "가나다라마바사아자차"

        assertFalse(acceptsNicknameEdit(text, TextRange(3), "입력"))
        assertFalse(acceptsNicknameEdit(text, TextRange(3), "긴붙여넣기"))
        assertTrue(acceptsNicknameEdit(text, TextRange(3, 5), "수정"))
        assertTrue(acceptsNicknameEdit(text, TextRange(3, 5), ""))
    }

    @Test
    fun acceptsTenCodePointsIncludingEmojiAndNormalizesCrLfBeforeCheckingLength() {
        assertTrue(acceptsNicknameEdit("123456789", TextRange(9), "😀"))
        assertFalse(acceptsNicknameEdit("123456789😀", TextRange(11), "가"))
        assertTrue(acceptsNicknameEdit("123456789", TextRange(9), "\r\n"))
    }

    private fun acceptsNicknameEdit(
        text: String,
        selection: TextRange,
        replacement: String,
    ): Boolean = acceptedNicknameInput(text.replaceRange(selection.min, selection.max, replacement)) != null
}
