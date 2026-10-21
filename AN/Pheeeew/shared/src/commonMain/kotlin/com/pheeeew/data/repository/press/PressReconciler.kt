package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.press.MyDailyPressSnapshot

/** Pure decisions. This object never changes counters, publishes state, retries, or performs I/O. */
internal object PressReconciler {
    fun resolveUnknown(
        write: PressWriteState,
        snapshot: MyDailyPressSnapshot,
        canRetryUnknown: Boolean,
    ): PressWriteResolution {
        if (isCovered(write, snapshot)) return PressWriteResolution.Confirmed
        val misses = write.reconciliationMisses + 1
        return if (snapshot.pressDate != write.baseline.pressDate ||
            (!canRetryUnknown && misses >= MAX_UNKNOWN_RECONCILIATION_READS)
        ) {
            PressWriteResolution.Retired
        } else {
            PressWriteResolution.Pending(
                write.copy(phase = PressWritePhase.RetryPending, reconciliationMisses = misses),
            )
        }
    }

    fun canApplyAggregate(
        requestedGeneration: Long,
        currentGeneration: Long,
        write: PressWriteState?,
    ): Boolean =
        requestedGeneration == currentGeneration &&
            write?.phase != PressWritePhase.InFlight && write?.uncertain != true

    /** Only successful writes already covered at read start may leave the optimistic total. */
    fun reconcileAll(
        serverTotal: Long,
        acceptedPressesSinceSnapshot: Long,
        optimisticAggregateCount: Long,
        acceptedPressesCoveredAtReadStart: Long,
    ): PressAggregateCounts? {
        if (serverTotal > Long.MAX_VALUE - (optimisticAggregateCount - acceptedPressesCoveredAtReadStart)) return null
        check(acceptedPressesSinceSnapshot >= acceptedPressesCoveredAtReadStart)
        check(optimisticAggregateCount >= acceptedPressesCoveredAtReadStart)
        return PressAggregateCounts(
            acceptedPressesSinceSnapshot - acceptedPressesCoveredAtReadStart,
            optimisticAggregateCount - acceptedPressesCoveredAtReadStart,
        )
    }

    private fun isCovered(
        write: PressWriteState,
        snapshot: MyDailyPressSnapshot,
    ): Boolean {
        if (snapshot.pressDate != write.baseline.pressDate) return false
        if (write.baseline.total > Long.MAX_VALUE - write.batch.totalCount) return false
        if (snapshot.total < write.baseline.total + write.batch.totalCount) return false
        return write.batch.counts.all { (emotion, count) ->
            val before = write.baseline.counts.getValue(emotion)
            before <= Long.MAX_VALUE - count && snapshot.counts.getValue(emotion) >= before + count
        }
    }

    private const val MAX_UNKNOWN_RECONCILIATION_READS = 3
}

internal sealed interface PressWriteResolution {
    data object Confirmed : PressWriteResolution

    data object Retired : PressWriteResolution

    data class Pending(
        val write: PressWriteState,
    ) : PressWriteResolution
}

internal data class PressAggregateCounts(
    val acceptedPressesSinceSnapshot: Long,
    val optimisticAggregateCount: Long,
)
