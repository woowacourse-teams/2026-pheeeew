package com.pheeeew.core.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val EXIT_WINDOW_MILLIS = 2_000L

@Composable
actual fun DoubleBackToExitHandler() {
    val activity = LocalActivity.current
    var lastBackPressedAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackPressedAt <= EXIT_WINDOW_MILLIS) {
            activity?.finish()
        } else {
            lastBackPressedAt = now
        }
    }
}
