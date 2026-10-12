package com.pheeeew.feature.screens.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay as coroutineDelay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Coalesces map snapshots while leaving page requests free to continue. */
internal class MapPinSnapshotPublisher<T>(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val wait: suspend (Long) -> Unit = { coroutineDelay(it) },
    private val sameSnapshot: (T, T) -> Boolean,
    private val publish: (T) -> Unit,
    private val discard: (T) -> Unit,
) {
    private var generation: Long? = null
    private var active = true
    private var hasPublishedFirstSnapshot = false
    private var morePagesExpected = false
    private var pendingSnapshot: T? = null
    private var pendingHasVisiblePins = false
    private var pendingIsFinal = false
    private var lastPublishedSnapshot: T? = null
    private var ticker: Job? = null

    init {
        require(intervalMillis > 0) { "intervalMillis must be positive" }
    }

    fun begin(nextGeneration: Long) {
        if (generation == nextGeneration) return
        cancelTicker()
        pendingSnapshot?.let(discard)
        pendingSnapshot = null
        pendingHasVisiblePins = false
        pendingIsFinal = false
        generation = nextGeneration
        hasPublishedFirstSnapshot = false
        morePagesExpected = false
        lastPublishedSnapshot = null
    }

    /** Records an already visible cache snapshot without consuming the first network publication. */
    fun seed(
        forGeneration: Long,
        snapshot: T,
    ) {
        if (generation == forGeneration) lastPublishedSnapshot = snapshot
    }

    fun offer(
        forGeneration: Long,
        snapshot: T,
        hasVisiblePins: Boolean,
        hasNextPage: Boolean,
    ) {
        if (generation != forGeneration) {
            discard(snapshot)
            return
        }
        morePagesExpected = hasNextPage
        if (active && !hasPublishedFirstSnapshot && hasVisiblePins) {
            replacePending(null)
            publishIfChanged(snapshot)
            hasPublishedFirstSnapshot = true
            scheduleTicker()
            return
        }
        replacePending(snapshot, hasVisiblePins = hasVisiblePins)
        if (active && morePagesExpected) scheduleTicker()
    }

    /** Publishes the final or error snapshot and cancels any scheduled duplicate. */
    fun finish(
        forGeneration: Long,
        snapshot: T,
    ) {
        if (generation != forGeneration) {
            discard(snapshot)
            return
        }
        morePagesExpected = false
        cancelTicker()
        if (!active) {
            replacePending(snapshot, hasVisiblePins = true, isFinal = true)
            return
        }
        replacePending(null)
        publishIfChanged(snapshot)
        hasPublishedFirstSnapshot = true
    }

    /** Flushes the newest received page when the following page fails or has an invalid cursor. */
    fun flush(forGeneration: Long) {
        if (generation != forGeneration) return
        morePagesExpected = false
        cancelTicker()
        if (!active) return
        pendingSnapshot?.let {
            if (hasPublishedFirstSnapshot || pendingHasVisiblePins) {
                clearPending()
                publishIfChanged(it)
                hasPublishedFirstSnapshot = true
            } else {
                replacePending(null)
            }
        }
    }

    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        if (!active) {
            cancelTicker()
            return
        }
        val pending = pendingSnapshot
        if (pending != null) {
            if (hasPublishedFirstSnapshot || pendingHasVisiblePins || pendingIsFinal) {
                clearPending()
                publishIfChanged(pending)
                hasPublishedFirstSnapshot = true
            } else {
                replacePending(null)
            }
        }
        scheduleTicker()
    }

    fun cancel() {
        cancelTicker()
        pendingSnapshot?.let(discard)
        pendingSnapshot = null
        pendingHasVisiblePins = false
        pendingIsFinal = false
        generation = null
        morePagesExpected = false
        hasPublishedFirstSnapshot = false
        lastPublishedSnapshot = null
    }

    private fun replacePending(
        snapshot: T?,
        hasVisiblePins: Boolean = false,
        isFinal: Boolean = false,
    ) {
        val previous = pendingSnapshot
        if (previous === snapshot) {
            pendingHasVisiblePins = hasVisiblePins
            pendingIsFinal = isFinal
            return
        }
        pendingSnapshot = snapshot
        pendingHasVisiblePins = hasVisiblePins
        pendingIsFinal = isFinal
        previous?.let(discard)
    }

    private fun clearPending() {
        pendingSnapshot = null
        pendingHasVisiblePins = false
        pendingIsFinal = false
    }

    private fun publishIfChanged(snapshot: T) {
        val previous = lastPublishedSnapshot
        if (previous != null && sameSnapshot(previous, snapshot)) {
            discard(snapshot)
            return
        }
        lastPublishedSnapshot = snapshot
        publish(snapshot)
    }

    private fun scheduleTicker() {
        if (!active || !morePagesExpected || ticker?.isActive == true) return
        val scheduledGeneration = generation ?: return
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    while (currentCoroutineContext().isActive && active && generation == scheduledGeneration && morePagesExpected) {
                        wait(intervalMillis)
                        if (!active || generation != scheduledGeneration) break
                        pendingSnapshot?.let {
                            if (hasPublishedFirstSnapshot || pendingHasVisiblePins) {
                                clearPending()
                                publishIfChanged(it)
                                hasPublishedFirstSnapshot = true
                            }
                        }
                    }
                } finally {
                    if (ticker === currentCoroutineContext()[Job]) ticker = null
                }
            }
        ticker = job
        job.start()
    }

    private fun cancelTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 300L
    }
}
