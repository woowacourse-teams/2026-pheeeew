package com.pheeeew.data.repository.press

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

    fun personalWriteSucceeded(nowMillis: Long) {
        readCycle.advanceGeneration()
        snapshots.markMySnapshotSuccessful(nowMillis)
        aggregateRefresh.markDirty()
    }

    fun markAggregateRefreshPending() {
        aggregateRefresh.markDirty()
    }

    fun hasAggregateRefreshPending(): Boolean = aggregateRefresh.isDirty()

    fun clearAggregateRefreshPending() {
        aggregateRefresh.clearDirty()
    }

    fun recordMySnapshotSuccess(nowMillis: Long) {
        snapshots.markMySnapshotSuccessful(nowMillis)
    }

    fun recordAllSnapshotSuccess(nowMillis: Long) {
        snapshots.markAllSnapshotSuccessful(nowMillis)
    }

    fun invalidateAllSnapshot() {
        snapshots.invalidateAllSnapshot()
    }

    fun hasFreshMySnapshot(
        nowMillis: Long,
        ttlMillis: Long,
    ): Boolean = snapshots.hasFreshMySnapshot(nowMillis, ttlMillis)

    fun hasFreshAllSnapshot(
        nowMillis: Long,
        ttlMillis: Long,
    ): Boolean = snapshots.hasFreshAllSnapshot(nowMillis, ttlMillis)

    fun nextStatisticsRetryDelay(retryPolicy: PressRetryPolicy): Long = aggregateRefresh.nextRetryDelay(retryPolicy)

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

        fun markMySnapshotSuccessful(nowMillis: Long) {
            mySnapshot.markSuccessful(nowMillis)
        }

        fun markAllSnapshotSuccessful(nowMillis: Long) {
            allSnapshot.markSuccessful(nowMillis)
        }

        fun invalidateAllSnapshot() {
            allSnapshot.invalidate()
        }

        fun hasFreshMySnapshot(
            nowMillis: Long,
            ttlMillis: Long,
        ): Boolean = mySnapshot.isFresh(nowMillis, ttlMillis)

        fun hasFreshAllSnapshot(
            nowMillis: Long,
            ttlMillis: Long,
        ): Boolean = allSnapshot.isFresh(nowMillis, ttlMillis)
    }

    private class SnapshotTimestamp {
        private var lastSuccessfulReadAtMillis: Long? = null

        fun markSuccessful(nowMillis: Long) {
            lastSuccessfulReadAtMillis = nowMillis
        }

        fun invalidate() {
            lastSuccessfulReadAtMillis = null
        }

        fun isFresh(
            nowMillis: Long,
            ttlMillis: Long,
        ): Boolean = lastSuccessfulReadAtMillis?.let { nowMillis - it < ttlMillis } == true
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

        fun nextRetryDelay(retryPolicy: PressRetryPolicy): Long {
            statisticsFailures = (statisticsFailures + 1).coerceAtMost(64)
            return retryPolicy.delayMillis(statisticsFailures)
        }

        fun resetFailures() {
            statisticsFailures = 0
        }
    }
}
