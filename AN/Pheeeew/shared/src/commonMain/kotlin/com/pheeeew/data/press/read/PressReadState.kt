package com.pheeeew.data.press.read

import com.pheeeew.data.press.delivery.PressRetryPolicy
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis

/** Owns paired-read freshness, generation, coalescing, and aggregate-refresh bookkeeping. */
internal class PressReadState {
    private val readCycle = ReadCycle()
    private val snapshots = SnapshotFreshness()
    private val aggregateRefresh = AggregateRefreshState()

    fun requestAnotherRead() {
        readCycle.requestAnotherRead()
    }

    fun consumeAnotherRead(ownerIsActive: Boolean): Boolean = readCycle.consumeAnotherRead(ownerIsActive)

    fun beginRead(): Long = readCycle.advanceGeneration()

    fun currentGeneration(): Long = readCycle.currentGeneration()

    fun isStale(generation: Long): Boolean = readCycle.isStale(generation)

    fun personalWriteSucceeded(now: PressMonotonicMillis) {
        readCycle.advanceGeneration()
        snapshots.markMySnapshotSuccessful(now)
        aggregateRefresh.markDirty()
    }

    fun markAggregateRefreshPending() {
        aggregateRefresh.markDirty()
    }

    fun hasAggregateRefreshPending(): Boolean = aggregateRefresh.isDirty()

    fun clearAggregateRefreshPending() {
        aggregateRefresh.clearDirty()
    }

    fun recordMySnapshotSuccess(now: PressMonotonicMillis) {
        snapshots.markMySnapshotSuccessful(now)
    }

    fun recordAllSnapshotSuccess(now: PressMonotonicMillis) {
        snapshots.markAllSnapshotSuccessful(now)
    }

    fun invalidateAllSnapshot() {
        snapshots.invalidateAllSnapshot()
    }

    fun hasFreshMySnapshot(
        now: PressMonotonicMillis,
        ttl: PressDelayMillis,
    ): Boolean = snapshots.hasFreshMySnapshot(now, ttl)

    fun hasFreshAllSnapshot(
        now: PressMonotonicMillis,
        ttl: PressDelayMillis,
    ): Boolean = snapshots.hasFreshAllSnapshot(now, ttl)

    fun nextStatisticsRetryDelay(retryPolicy: PressRetryPolicy): PressDelayMillis =
        aggregateRefresh.nextRetryDelay(retryPolicy)

    fun resetStatisticsFailures() {
        aggregateRefresh.resetFailures()
    }

    private class ReadCycle {
        private var generation = 0L
        private var anotherReadRequested = false

        fun requestAnotherRead() {
            anotherReadRequested = true
        }

        fun consumeAnotherRead(ownerIsActive: Boolean): Boolean {
            if (!anotherReadRequested || !ownerIsActive) return false
            anotherReadRequested = false
            return true
        }

        fun advanceGeneration(): Long {
            generation++
            return generation
        }

        fun currentGeneration(): Long = generation

        fun isStale(candidate: Long): Boolean = candidate < generation
    }

    private class SnapshotFreshness {
        private val mySnapshot = SnapshotTimestamp()
        private val allSnapshot = SnapshotTimestamp()

        fun markMySnapshotSuccessful(now: PressMonotonicMillis) {
            mySnapshot.markSuccessful(now)
        }

        fun markAllSnapshotSuccessful(now: PressMonotonicMillis) {
            allSnapshot.markSuccessful(now)
        }

        fun invalidateAllSnapshot() {
            allSnapshot.invalidate()
        }

        fun hasFreshMySnapshot(
            now: PressMonotonicMillis,
            ttl: PressDelayMillis,
        ): Boolean = mySnapshot.isFresh(now, ttl)

        fun hasFreshAllSnapshot(
            now: PressMonotonicMillis,
            ttl: PressDelayMillis,
        ): Boolean = allSnapshot.isFresh(now, ttl)
    }

    private class SnapshotTimestamp {
        private var lastSuccessfulReadAt: PressMonotonicMillis? = null

        fun markSuccessful(now: PressMonotonicMillis) {
            lastSuccessfulReadAt = now
        }

        fun invalidate() {
            lastSuccessfulReadAt = null
        }

        fun isFresh(
            now: PressMonotonicMillis,
            ttl: PressDelayMillis,
        ): Boolean = lastSuccessfulReadAt?.let { now.value - it.value < ttl.value } == true
    }

    private class AggregateRefreshState {
        private var dirtyAfterWrite = false
        private var statisticsFailures = 0

        fun markDirty() {
            dirtyAfterWrite = true
        }

        fun isDirty(): Boolean = dirtyAfterWrite

        fun clearDirty() {
            dirtyAfterWrite = false
        }

        fun nextRetryDelay(retryPolicy: PressRetryPolicy): PressDelayMillis {
            statisticsFailures = (statisticsFailures + 1).coerceAtMost(64)
            return retryPolicy.delayMillis(statisticsFailures)
        }

        fun resetFailures() {
            statisticsFailures = 0
        }
    }
}
