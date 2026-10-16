package com.pheeeew.core.utils

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fast requests finish immediately; an indicator that appears remains visible for its minimum time. */
internal suspend fun <T> withDelayedLoadingIndicator(
    onVisibleChanged: (Boolean) -> Unit,
    delayMillis: Long = 150L,
    minimumDisplayMillis: Long = 300L,
    block: suspend () -> T,
): T =
    coroutineScope {
        val operationScope = this
        var minimumDisplay: Job? = null
        val indicator =
            launch(start = CoroutineStart.UNDISPATCHED) {
                delay(delayMillis)
                onVisibleChanged(true)
                minimumDisplay =
                    operationScope.launch(start = CoroutineStart.UNDISPATCHED) { delay(minimumDisplayMillis) }
            }
        try {
            try {
                block()
            } finally {
                indicator.cancelAndJoin()
                minimumDisplay?.join()
            }
        } finally {
            indicator.cancel()
            minimumDisplay?.cancel()
            onVisibleChanged(false)
        }
    }
