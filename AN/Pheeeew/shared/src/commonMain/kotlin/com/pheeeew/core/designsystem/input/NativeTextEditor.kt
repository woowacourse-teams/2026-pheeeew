package com.pheeeew.core.designsystem.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

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

internal fun limitMemoText(text: String): String {
    if (text.length <= 50) return text
    val end = if (text[49].isHighSurrogate() && text[50].isLowSurrogate()) 49 else 50
    return text.take(end)
}

internal fun TextFieldValue.limitMemoValue(): TextFieldValue {
    val limited = limitMemoText(text)
    return copy(
        text = limited,
        selection =
            androidx.compose.ui.text.TextRange(
                selection.start.coerceAtMost(limited.length),
                selection.end.coerceAtMost(limited.length),
            ),
    )
}
