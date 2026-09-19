package com.pheeeew.core.monitoring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns periodic monitoring maintenance and exposes cancellation for the platform lifecycle. */
class MonitoringTicker(
    private val scope: CoroutineScope,
    private val tick: () -> Unit,
    private val intervalMs: Long = 1_000L,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    delay(intervalMs)
                    tick()
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
