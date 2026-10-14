package com.pheeeew.feature.screens.press

import androidx.compose.runtime.staticCompositionLocalOf

/** Optional draw-submission probe, supplied only by the Android debug app. */
val LocalPressUiMeasurement = staticCompositionLocalOf<((String) -> Unit)?> { null }
