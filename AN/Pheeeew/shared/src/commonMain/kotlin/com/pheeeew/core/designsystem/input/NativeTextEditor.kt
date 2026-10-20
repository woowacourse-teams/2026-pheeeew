package com.pheeeew.core.designsystem.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal expect fun dismissNativeTextKeyboard()

internal expect val usesNativeTextEditor: Boolean

@Composable
internal expect fun NativeTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier,
    label: String,
    normalizeText: (TextFieldValue) -> TextFieldValue,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    onSearch: () -> Unit = {},
)

internal fun limitMemoText(text: String): String = TextFieldValue(text, TextRange(text.length)).limitMemoValue().text

internal fun TextFieldValue.limitMemoValue(): TextFieldValue {
    if (text.length <= 50) return this
    // UIKit reports the caret after the inserted text. Remove overflow there,
    // preserving the existing suffix when an edit is made in the middle.
    var end = selection.max.coerceIn(0, text.length)
    var start = (end - (text.length - 50)).coerceAtLeast(0)
    if (start > 0 && start < text.length && text[start - 1].isHighSurrogate() && text[start].isLowSurrogate()) start--
    if (end > 0 && end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end++
    val limited = text.removeRange(start, end)

    fun adjusted(offset: Int): Int =
        when {
            offset <= start -> offset
            offset < end -> start
            else -> offset - (end - start)
        }.coerceIn(0, limited.length)
    return copy(
        text = limited,
        selection = TextRange(adjusted(selection.start), adjusted(selection.end)),
        composition = composition?.let { TextRange(adjusted(it.start), adjusted(it.end)) },
    )
}
