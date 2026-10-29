package com.pheeeew.core.designsystem.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun navigableBottomSheetDialogProperties(keyboardAware: Boolean): DialogProperties =
    if (keyboardAware) {
        DialogProperties(
            usePlatformDefaultWidth = false,
            usePlatformInsets = false,
            // The record sheet reads IME visibility and applies imePadding inside its own window.
            useSoftwareKeyboardInset = false,
            scrimColor = Color.Transparent,
            animateTransition = false,
        )
    } else {
        DialogProperties(
            usePlatformDefaultWidth = false,
            usePlatformInsets = false,
        )
    }
