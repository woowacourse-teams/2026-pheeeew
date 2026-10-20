package com.pheeeew.core.designsystem.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

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
) = Unit
