package com.pheeeew.core.share

import androidx.compose.runtime.Composable

fun interface SystemTextShareLauncher {
    /** Returns false only when the platform cannot present its share UI. */
    fun shareText(text: String): Boolean
}

@Composable
expect fun rememberSystemTextShareLauncher(): SystemTextShareLauncher
