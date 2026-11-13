package com.pheeeew.core.navigation

import androidx.compose.runtime.Composable

@Composable
expect fun FlowBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
