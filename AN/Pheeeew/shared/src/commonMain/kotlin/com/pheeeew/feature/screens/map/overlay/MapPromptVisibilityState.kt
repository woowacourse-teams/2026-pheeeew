package com.pheeeew.feature.screens.map.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val MAP_PROMPT_RESTORE_DELAY_MILLIS = 3_000L

internal class MapPromptVisibilityState(
    private val scope: CoroutineScope,
) {
    var isHidden by mutableStateOf(false)
        private set

    private var restoreJob: Job? = null

    fun onCameraMovingChanged(isMoving: Boolean) {
        restoreJob?.cancel()
        restoreJob = null
        if (isMoving) {
            isHidden = true
        } else if (isHidden) {
            restoreJob =
                scope.launch {
                    delay(MAP_PROMPT_RESTORE_DELAY_MILLIS)
                    isHidden = false
                }
        }
    }

    fun reset() {
        restoreJob?.cancel()
        restoreJob = null
        isHidden = false
    }
}
