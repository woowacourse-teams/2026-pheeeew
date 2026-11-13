package com.pheeeew.feature.screens.map.overlay

import androidx.compose.ui.window.PopupProperties

internal actual fun mapFeedbackPopupProperties(): PopupProperties =
    PopupProperties(
        focusable = false,
        usePlatformInsets = false,
    )
