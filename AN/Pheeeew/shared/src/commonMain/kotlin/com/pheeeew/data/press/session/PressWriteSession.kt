package com.pheeeew.data.press.session

import com.pheeeew.data.press.delivery.PressWritePhase
import com.pheeeew.data.press.delivery.PressWriteState
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.press.settlement.PressReconciler
import com.pheeeew.domain.press.settlement.PressWriteCounts
import com.pheeeew.domain.press.settlement.PressWriteLedger
import com.pheeeew.domain.press.settlement.PressWriteResolution
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis

/** Owner-confined transitions keep delivery status and domain accounting consistent. */
internal class PressWriteSession {
    private val ledger = PressWriteLedger()
    private var pendingWrite: PressWriteState? = null

    fun exceedsAcceptanceLimit(
        myServerTotal: Long,
        allServerTotal: Long,
    ): Boolean = ledger.exceedsAcceptanceLimit(myServerTotal, allServerTotal)

    fun accept(emotion: EmotionState) {
        ledger.accept(emotion)
    }

    fun hasOutstanding(): Boolean = ledger.hasOutstanding()

    fun hasPendingWrite(): Boolean = pendingWrite != null

    fun isOutcomeUnknown(): Boolean = pendingWrite?.uncertain == true

    fun isAwaitingSnapshot(): Boolean = pendingWrite?.phase == PressWritePhase.AwaitingSnapshot

    fun isInFlight(): Boolean = pendingWrite?.phase == PressWritePhase.InFlight

    fun acceptedPressCoverageAtReadStart(): Long =
        if (isOutcomeUnknown()) 0L else ledger.acceptedPressCoverageAtReadStart()

    fun canRetryUnknown(
        now: PressMonotonicMillis,
        idempotencyWindow: PressDelayMillis?,
    ): Boolean {
        val write = pendingWrite ?: return false
        val window = idempotencyWindow ?: return false
        return write.uncertain && window.value > 0L && now.value - write.createdAtMonotonicMillis.value < window.value
    }

    fun prepareForSend(
        currentBaseline: MyDailyPressSnapshot,
        createNewWrite: () -> PressWriteState?,
    ): PressWriteState? {
        val existing = pendingWrite
        val write =
            when {
                existing?.uncertain == true -> existing
                existing != null -> existing.copy(pending = existing.pending.copy(baseline = currentBaseline))
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
        ledger.confirm(write.pending.batch)
        pendingWrite = null
    }

    fun reject(write: PressWriteState) {
        ledger.reject(write.pending.batch)
        pendingWrite = null
    }

    fun resolveUnknown(
        snapshot: MyDailyPressSnapshot,
        canRetryUnknown: Boolean,
    ): PressWriteResolution? {
        val write = pendingWrite?.takeIf { it.uncertain } ?: return null
        val resolution = PressReconciler.resolveUnknown(write.pending, snapshot, canRetryUnknown)
        when (resolution) {
            PressWriteResolution.Confirmed -> {
                confirm(write)
            }

            PressWriteResolution.Retired -> {
                reject(write)
            }

            is PressWriteResolution.Pending -> {
                pendingWrite = write.copy(pending = resolution.write, phase = PressWritePhase.RetryPending)
            }
        }
        return resolution
    }

    fun reconcileAllSnapshot(
        snapshot: AllDailyPressSnapshot,
        acceptedPressesCoveredAtReadStart: Long,
    ): Boolean = ledger.reconcileAllSnapshot(snapshot, acceptedPressesCoveredAtReadStart)

    fun canDisplayMyTotal(total: Long): Boolean = ledger.canDisplayMyTotal(total)

    fun canDisplayAllTotal(total: Long): Boolean = ledger.canDisplayAllTotal(total)

    fun counts(): PressWriteCounts = ledger.snapshot()
}
