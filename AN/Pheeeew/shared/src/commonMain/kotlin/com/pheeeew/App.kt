package com.pheeeew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Entry point for the new application UI.
 *
 * The legacy application is kept separately in [com.pheeeew.legacy.LegacyApp]
 * while the new feature structure is being introduced.
 */
@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize())
}
