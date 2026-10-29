package com.pheeeew.core.designsystem.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue

internal actual fun dismissNativeTextKeyboard() = Unit

internal actual val usesNativeTextEditor: Boolean = false

@Composable
internal actual fun NativeTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier,
    label: String,
    normalizeText: (TextFieldValue) -> TextFieldValue,
    singleLine: Boolean,
    enabled: Boolean,
    onSearch: () -> Unit,
    keyboardOptions: KeyboardOptions?,
    containerColor: Color?,
    acceptText: (String) -> Boolean,
) = Unit
