package com.pheeeew.data.press.race

import com.pheeeew.core.time.koreaCalendarDate
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressBatch

/** Debug-only correlation between accepted HTTP inputs and the queue's fixed batch objects. */
internal class PressHttpInputMeasurements(
    private val measurements: PressRaceMeasurements,
    private val pendingQueuePressCount: (() -> Long)? = null,
    private val calendarDate: (Long) -> String = { koreaCalendarDate(it) },
) {
    private data class Tap(
        val emotion: EmotionState,
        val date: String,
        val monotonicMillis: Long,
    )

    private data class Write(
        val batch: PressBatch,
        val inputId: String,
    )

    private val pendingTaps = mutableListOf<Tap>()
    private val writes = mutableListOf<Write>()
    private var sequence = 0L
    private var capturePaused = false
    private var acceptedPendingPresses = 0L

    fun tap(
        emotion: EmotionState,
        occurredAtMillis: Long,
    ) {
        if (!measurements.isEnabled) return
        acceptedPendingPresses = saturatingAdd(acceptedPendingPresses, 1L)
        if (capturePaused) {
            return
        }
        if (pendingTaps.size >= MAX_PENDING_TAPS) {
            val pending = untrackedPendingPresses()
            pauseUntilQueueDrains(pending, pending)
            return
        }
        val date =
            runCatching { calendarDate(occurredAtMillis) }
                .getOrNull()
                ?: run {
                    val pending = untrackedPendingPresses()
                    pauseUntilQueueDrains(pending, pending)
                    return
                }
        val monotonicMillis =
            measurements.nowMillis()
                ?: run {
                    val pending = untrackedPendingPresses()
                    pauseUntilQueueDrains(pending, pending)
                    return
                }
        pendingTaps += Tap(emotion, date, monotonicMillis)
    }

    /** Called only after the existing queue returns its actual HTTP batch. */
    fun batch(batch: PressBatch) {
        if (!measurements.isEnabled) return
        acceptedPendingPresses = (acceptedPendingPresses - batch.totalCount).coerceAtLeast(0L)
        val pendingAfterBatch = untrackedPendingPresses()
        if (capturePaused) {
            if (pendingAfterBatch == 0L) capturePaused = false
            return
        }
        val remaining = batch.counts.toMutableMap()
        val batchDate =
            runCatching { calendarDate(batch.occurredAt.value) }
                .getOrNull()
                ?: run {
                    val skipped = saturatingAdd(batch.totalCount.toLong(), pendingAfterBatch)
                    pauseUntilQueueDrains(pendingAfterBatch, skipped)
                    return
                }
        val selectedIndices = mutableListOf<Int>()
        val selectedTaps = mutableListOf<Tap>()

        for (index in pendingTaps.indices) {
            val tap = pendingTaps[index]
            val needed = remaining[tap.emotion] ?: 0
            if (tap.date == batchDate && needed > 0) {
                selectedIndices += index
                selectedTaps += tap
                remaining[tap.emotion] = needed - 1
                if (remaining.values.all { it == 0 }) break
            }
        }

        // The fixed batch has already left the queue. Quarantine its remaining queue until drained.
        if (remaining.values.any { it != 0 }) {
            val skipped = saturatingAdd(batch.totalCount.toLong(), pendingAfterBatch)
            pauseUntilQueueDrains(pendingAfterBatch, skipped)
            return
        }
        for (index in selectedIndices.asReversed()) pendingTaps.removeAt(index)

        while (writes.size >= MAX_TRACKED_WRITES) {
            measurements.discard(writes.removeAt(0).inputId)
        }
        val inputId = "http-${++sequence}"
        selectedTaps.forEach { tap ->
            measurements.tap(inputId, PressRaceMeasureSource.Http, tap.monotonicMillis)
        }
        writes += Write(batch, inputId)
    }

    /** PressSession invokes this immediately before PressSender.send. Retries keep the first time. */
    fun firstSend(batch: PressBatch) {
        writeFor(batch)?.let { measurements.sendAttemptStarted(it.inputId) }
    }

    fun attemptResult(
        batch: PressBatch,
        result: PressRaceHttpAttemptResult,
        retryAfterMillis: Long? = null,
    ) {
        writeFor(batch)?.let { measurements.sendAttemptResult(it.inputId, result, retryAfterMillis) }
    }

    fun finalResult(
        batch: PressBatch,
        status: String,
        recovered: Boolean = false,
    ) {
        val index = writes.indexOfLast { it.batch === batch }
        if (index < 0) return
        val write = writes[index]
        measurements.finalResult(write.inputId, status, recovered)
        if (status == "APPLIED" || status == "REJECTED") writes.removeAt(index)
    }

    fun discard(batch: PressBatch) {
        val index = writes.indexOfLast { it.batch === batch }
        if (index < 0) return
        measurements.discard(writes.removeAt(index).inputId)
    }

    fun recoveryResult(
        batch: PressBatch,
        result: PressRaceRecoveryOutcome,
    ) {
        val index = writes.indexOfLast { it.batch === batch }
        if (index < 0) return
        val write = writes[index]
        measurements.recoveryResult(write.inputId, result)
        if (result != PressRaceRecoveryOutcome.Pending) writes.removeAt(index)
    }

    private fun writeFor(batch: PressBatch): Write? = writes.lastOrNull { it.batch === batch }

    private fun untrackedPendingPresses(): Long =
        runCatching { pendingQueuePressCount?.invoke() }
            .getOrNull()
            ?.coerceAtLeast(0L)
            ?: acceptedPendingPresses

    /** Clear ambiguous taps and resume only after the authoritative unbatched queue is empty. */
    private fun pauseUntilQueueDrains(
        pendingPresses: Long,
        skippedPresses: Long,
    ) {
        pendingTaps.clear()
        capturePaused = pendingPresses > 0L
        measurements.coverageGap(PressRaceMeasureSource.Http, skippedPresses)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private companion object {
        const val MAX_PENDING_TAPS = 10_000
        const val MAX_TRACKED_WRITES = 20
    }
}
