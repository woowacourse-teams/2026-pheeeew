package com.pheeeew.feature.monitoring.product

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.pheeeew.feature.screens.map.monitoring.rememberMonitoringForeground

val LocalProductMonitoringVisible = staticCompositionLocalOf { true }

@Composable
fun ProductScreen(
    telemetry: ProductMonitoring,
    visible: Boolean = true,
    event: String? = null,
) {
    val foreground = rememberMonitoringForeground() && LocalProductMonitoringVisible.current
    LaunchedEffect(telemetry, visible, foreground) {
        if (visible && foreground) {
            withFrameNanos { }
            telemetry.screenShown()
            if (event != null) telemetry.emit(event)
        }
    }
}
