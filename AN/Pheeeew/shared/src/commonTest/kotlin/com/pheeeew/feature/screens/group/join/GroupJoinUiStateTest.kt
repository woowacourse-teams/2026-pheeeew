package com.pheeeew.feature.screens.group.join

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupJoinUiStateTest {
    @Test
    fun `shows an error as soon as the invite code exceeds six characters`() {
        val state = GroupJoinUiState(input = "ABCDEFG")

        assertTrue(state.shouldShowCodeValidationError)
    }

    @Test
    fun `does not show a length error for a six character code before searching`() {
        val state = GroupJoinUiState(input = "ABC123")

        assertFalse(state.shouldShowCodeValidationError)
    }

    @Test
    fun `keeps the short code hint until search is attempted`() {
        val state = GroupJoinUiState(input = "ABC")

        assertFalse(state.shouldShowCodeValidationError)
    }
}
