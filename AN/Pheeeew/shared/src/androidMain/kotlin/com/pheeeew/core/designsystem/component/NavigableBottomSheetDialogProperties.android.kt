package com.pheeeew.core.designsystem.component

import androidx.compose.ui.window.DialogProperties

internal actual fun navigableBottomSheetDialogProperties(keyboardAware: Boolean): DialogProperties =
    DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )
