package com.pheeeew.feature.screens.map.record.group

import androidx.compose.ui.window.DialogProperties

internal actual fun groupSelectorDialogProperties(): DialogProperties =
    DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )
