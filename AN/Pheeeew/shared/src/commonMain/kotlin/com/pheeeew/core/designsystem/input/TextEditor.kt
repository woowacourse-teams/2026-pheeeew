package com.pheeeew.core.designsystem.input

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange

/** The IME owns editing state. Screen state receives text, but its echoes never replace edits. */
internal class TextEditor(
    initialText: String,
) {
    val state = TextFieldState(initialText)
    private var externalText = initialText
    private var publishedText = initialText
    private val pendingTexts = mutableListOf<String>()

    fun publish(text: String): Boolean {
        if (text == publishedText) return false
        publishedText = text
        pendingTexts.add(text)
        return true
    }

    fun synchronize(text: String) {
        val echoedIndex = pendingTexts.lastIndexOf(text)
        if (echoedIndex >= 0) {
            pendingTexts.subList(0, echoedIndex + 1).clear()
            externalText = text
            return
        }
        if (text == externalText) return
        pendingTexts.clear()
        externalText = text
        publishedText = text
        if (text != state.text.toString()) {
            state.edit {
                val previous = selection
                replace(0, length, text)
                selection = TextRange(previous.start.coerceAtMost(length), previous.end.coerceAtMost(length))
            }
        }
    }
}

@Composable
internal fun rememberTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
): TextEditor {
    val editor = remember { TextEditor(text) }
    val latestOnTextChange = rememberUpdatedState(onTextChange)
    SideEffect { editor.synchronize(text) }
    LaunchedEffect(editor) {
        snapshotFlow { editor.state.text.toString() }.collect { edited ->
            if (editor.publish(edited)) latestOnTextChange.value(edited)
        }
    }
    return editor
}
