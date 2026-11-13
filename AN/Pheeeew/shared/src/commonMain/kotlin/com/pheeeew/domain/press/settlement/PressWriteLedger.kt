package com.pheeeew.domain.press.settlement

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch

/** Accounts for accepted input until personal confirmation and aggregate snapshot coverage. */
internal class PressWriteLedger {
    private val optimisticCounts = EmotionState.entries.associateWith { 0L }.toMutableMap()
    private var outstandingCount = 0L
    private var optimisticAllCount = 0L
    private var acceptedSinceAllSnapshot = 0L

    fun exceedsAcceptanceLimit(
        myServerTotal: Long,
        allServerTotal: Long,
    ): Boolean =
        outstandingCount >= Long.MAX_VALUE - myServerTotal ||
            optimisticAllCount >= Long.MAX_VALUE - allServerTotal

    fun accept(emotion: EmotionState) {
        outstandingCount++
        optimisticAllCount++
        optimisticCounts[emotion] = optimisticCounts.getValue(emotion) + 1L
    }

    fun hasOutstanding(): Boolean = outstandingCount > 0L

    fun acceptedPressCoverageAtReadStart(): Long = acceptedSinceAllSnapshot

    fun confirm(batch: PressBatch) {
        removeOptimistic(batch)
        acceptedSinceAllSnapshot += batch.totalCount
    }

    fun reject(batch: PressBatch) {
        removeOptimistic(batch)
        removeOptimisticAll(batch.totalCount.toLong())
    }

    fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedPressesCoveredAtReadStart: Long,
    ): Boolean {
        val counts =
            PressReconciler.reconcileAll(
                snapshot.total,
                acceptedSinceAllSnapshot,
                optimisticAllCount,
                acceptedPressesCoveredAtReadStart,
            ) ?: return false
        acceptedSinceAllSnapshot = counts.acceptedPressesSinceSnapshot
        optimisticAllCount = counts.optimisticAggregateCount
        return true
    }

    fun canDisplayMyTotal(total: Long): Boolean = total <= Long.MAX_VALUE - outstandingCount

    fun canDisplayAllTotal(total: Long): Boolean = total <= Long.MAX_VALUE - optimisticAllCount

    fun snapshot(): PressWriteCounts =
        PressWriteCounts(
            optimisticCounts = optimisticCounts.filterValues { it > 0L },
            optimisticAllPressCount = optimisticAllCount,
            pendingPressCount = outstandingCount,
        )

    private fun removeOptimistic(accepted: PressBatch) {
        accepted.counts.forEach { (emotion, count) ->
            check(optimisticCounts.getValue(emotion) >= count)
            optimisticCounts[emotion] = optimisticCounts.getValue(emotion) - count
        }
        outstandingCount -= accepted.totalCount
    }

    private fun removeOptimisticAll(rejectedCount: Long) {
        check(optimisticAllCount >= rejectedCount)
        optimisticAllCount -= rejectedCount
    }
}

/** Immutable accounting projection; it knows nothing about UI state or transport jobs. */
internal data class PressWriteCounts(
    val optimisticCounts: Map<EmotionState, Long>,
    val optimisticAllPressCount: Long,
    val pendingPressCount: Long,
)
