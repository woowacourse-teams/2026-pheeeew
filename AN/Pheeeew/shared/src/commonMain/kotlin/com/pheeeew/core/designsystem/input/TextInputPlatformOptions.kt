package com.pheeeew.core.designsystem.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.PlatformImeOptions

/** Native iOS editing for memo and invite code; Android keeps its existing IME backend. */
@Composable
internal fun rememberTextInputPlatformOptions(): PlatformImeOptions? = remember { defaultTextInputPlatformOptions() }

internal expect fun defaultTextInputPlatformOptions(): PlatformImeOptions?
