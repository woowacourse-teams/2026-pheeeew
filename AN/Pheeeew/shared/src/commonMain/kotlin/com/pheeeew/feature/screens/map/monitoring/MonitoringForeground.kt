package com.pheeeew.feature.screens.map.monitoring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberMonitoringForeground(): Boolean {
    val owner = LocalLifecycleOwner.current
    var foreground by remember(
        owner,
    ) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer =
            LifecycleEventObserver { _, _ ->
                foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return foreground
}
