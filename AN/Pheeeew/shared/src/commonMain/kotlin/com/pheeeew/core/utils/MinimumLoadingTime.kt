package com.pheeeew.core.utils

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps loading feedback visible without adding a delay after a slow request. */
internal suspend fun <T> withMinimumLoadingTime(block: suspend () -> T): T =
    coroutineScope {
        val minimumDisplayTime = launch(start = CoroutineStart.UNDISPATCHED) { delay(1_000L) }
        try {
            block()
        } finally {
            minimumDisplayTime.join()
        }
    }
