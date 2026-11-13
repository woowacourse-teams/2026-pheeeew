package com.pheeeew.core.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun FlowBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS has no system back button; the flow's visible back action uses the same callback.
}
