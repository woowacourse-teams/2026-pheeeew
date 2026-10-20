package com.pheeeew.feature.screens.group.join

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class InviteCodeEditingTest {
    @Test
    fun normalizationKeepsMiddleCursorInsteadOfMovingToEnd() {
        val result = TextFieldValue("ao1234", TextRange(2)).normalizeInviteCode()
        assertEquals("A01234", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun normalizationKeepsCompositionRange() {
        val result = TextFieldValue("ab", TextRange(2), TextRange(1, 2)).normalizeInviteCode()
        assertEquals("AB", result.text)
        assertEquals(TextRange(1, 2), result.composition)
    }

    @Test
    fun invalidInternalSpaceDoesNotMoveTheCursorBackward() {
        val result = TextFieldValue("ab cd ", TextRange(3)).normalizeInviteCode()
        assertEquals("AB CD", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun pasteTrimsAndLimitsBothSelectionOffsets() {
        val result = TextFieldValue("  ab123456  ", TextRange(2, 12)).normalizeInviteCode()
        assertEquals("AB1234", result.text)
        assertEquals(TextRange(0, 6), result.selection)
    }
}
