package com.pheeeew.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Apply inside each window's content because a new Compose root may replace LocalDensity. */
@Composable
internal fun AppFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val appDensity = remember(density) { Density(density = density, fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides appDensity, content = content)
}
