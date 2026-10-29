package com.pheeeew.core.designsystem.input

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class TextEditorTest {
    @Test
    fun echoedTextPreservesMiddleSelection() {
        val editor = TextEditor("")
        editor.state.edit {
            append("가나다")
            selection = TextRange(1)
        }
        editor.publish("가나다")
        editor.synchronize("가나다")
        assertEquals(TextRange(1), editor.state.selection)
    }

    @Test
    fun delayedEchoDoesNotRollBackNewerInput() {
        val editor = TextEditor("")
        editor.state.edit { append("ㄱ") }
        editor.publish("ㄱ")
        editor.state.edit { replace(0, length, "가") }
        editor.publish("가")
        editor.synchronize("ㄱ")
        assertEquals("가", editor.state.text.toString())
        editor.synchronize("가")
        assertEquals("가", editor.state.text.toString())
    }

    @Test
    fun externalClearAfterAcknowledgementClearsTheEditor() {
        val editor = TextEditor("")
        editor.state.edit { append("메모") }
        editor.publish("메모")
        editor.synchronize("메모")
        editor.synchronize("")
        assertEquals("", editor.state.text.toString())
        assertEquals(TextRange.Zero, editor.state.selection)
    }
}
