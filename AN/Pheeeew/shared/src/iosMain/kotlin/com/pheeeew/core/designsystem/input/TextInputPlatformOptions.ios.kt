package com.pheeeew.core.designsystem.input

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun defaultTextInputPlatformOptions(): PlatformImeOptions? =
    PlatformImeOptions { usingNativeTextInput(true) }
