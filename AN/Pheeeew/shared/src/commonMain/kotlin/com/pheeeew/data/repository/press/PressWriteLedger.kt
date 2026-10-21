package com.pheeeew.data.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSessionState

/** Owns accepted-but-unsettled input counts and the fixed write awaiting a server outcome. */
internal class PressWriteLedger {
    private val optimisticCounts = EmotionState.entries.associateWith { 0L }.toMutableMap()
    private var outstandingCount = 0L
    private var optimisticAllCount = 0L
    private var acceptedSinceAllSnapshot = 0L
    private var pendingWrite: PressWriteState? = null

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

    fun hasPendingWrite(): Boolean = pendingWrite != null

    fun isOutcomeUnknown(): Boolean = pendingWrite?.uncertain == true

    fun isAwaitingSnapshot(): Boolean = pendingWrite?.phase == PressWritePhase.AwaitingSnapshot

    fun isInFlight(): Boolean = pendingWrite?.phase == PressWritePhase.InFlight

    fun acceptedCoverageAtReadStart(): Long = if (isOutcomeUnknown()) 0L else acceptedSinceAllSnapshot

    fun canRetryUnknown(
        nowMillis: Long,
        idempotencyWindowMillis: Long?,
    ): Boolean {
        val write = pendingWrite ?: return false
        val window = idempotencyWindowMillis ?: return false
        return write.uncertain && window > 0L && nowMillis - write.createdAtMillis < window
    }

    fun prepareForSend(
        currentBaseline: MyDailyPressSnapshot,
        createNewWrite: () -> PressWriteState?,
    ): PressWriteState? {
        val existing = pendingWrite
        val write =
            when {
                existing?.uncertain == true -> existing
                existing != null -> existing.copy(baseline = currentBaseline)
                else -> createNewWrite() ?: return null
            }.copy(phase = PressWritePhase.InFlight)
        pendingWrite = write
        return write
    }

    fun markRetryPending(
        write: PressWriteState,
        uncertain: Boolean = write.uncertain,
    ) {
        pendingWrite = write.copy(phase = PressWritePhase.RetryPending, uncertain = uncertain)
    }

    fun markAwaitingSnapshot(write: PressWriteState) {
        pendingWrite = write.copy(phase = PressWritePhase.AwaitingSnapshot, uncertain = true)
    }

    fun confirm(write: PressWriteState) {
        removeOptimistic(write.batch)
        acceptedSinceAllSnapshot += write.batch.totalCount
        pendingWrite = null
    }

    fun reject(write: PressWriteState) {
        removeOptimistic(write.batch)
        removeOptimisticAll(write.batch.totalCount.toLong())
        pendingWrite = null
    }

    fun resolveUnknown(
        snapshot: MyDailyPressSnapshot,
        canRetryUnknown: Boolean,
    ): PressWriteResolution? {
        val write = pendingWrite?.takeIf { it.uncertain } ?: return null
        val resolution = PressReconciler.resolveUnknown(write, snapshot, canRetryUnknown)
        when (resolution) {
            PressWriteResolution.Confirmed -> confirm(write)
            PressWriteResolution.Retired -> reject(write)
            is PressWriteResolution.Pending -> pendingWrite = resolution.write
        }
        return resolution
    }

    fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedCoveredAtReadStart: Long,
    ): Boolean {
        val counts =
            PressReconciler.reconcileAll(
                snapshot.total,
                acceptedSinceAllSnapshot,
                optimisticAllCount,
                acceptedCoveredAtReadStart,
            ) ?: return false
        acceptedSinceAllSnapshot = counts.acceptedSinceSnapshot
        optimisticAllCount = counts.optimisticCount
        return true
    }

    fun canDisplayMyTotal(total: Long): Boolean = total <= Long.MAX_VALUE - outstandingCount

    fun canDisplayAllTotal(total: Long): Boolean = total <= Long.MAX_VALUE - optimisticAllCount

    fun canApplyAggregate(
        requestedGeneration: Long,
        currentGeneration: Long,
    ): Boolean = PressReconciler.canApplyAggregate(requestedGeneration, currentGeneration, pendingWrite)

    fun applyTo(state: PressSessionState): PressSessionState =
        state.copy(
            optimisticCounts = optimisticCounts.filterValues { it > 0L },
            optimisticAllPressCount = optimisticAllCount,
            pendingPressCount = outstandingCount,
            isOutcomeUnknown = isOutcomeUnknown(),
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
