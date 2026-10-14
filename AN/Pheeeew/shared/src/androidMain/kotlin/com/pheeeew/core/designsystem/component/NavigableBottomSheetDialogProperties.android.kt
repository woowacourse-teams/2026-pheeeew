package com.pheeeew.core.designsystem.component

import androidx.compose.ui.window.DialogProperties

internal actual fun navigableBottomSheetDialogProperties(): DialogProperties =
    DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )
