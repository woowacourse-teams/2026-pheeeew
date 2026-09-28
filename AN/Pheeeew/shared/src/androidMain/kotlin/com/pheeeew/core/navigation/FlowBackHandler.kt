package com.pheeeew.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun FlowBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
