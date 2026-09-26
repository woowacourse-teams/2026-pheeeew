package com.pheeeew.feature.screens.map.record

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

internal fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    clickable(
        interactionSource = null,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
