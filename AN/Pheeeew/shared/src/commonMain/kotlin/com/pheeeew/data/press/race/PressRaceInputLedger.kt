package com.pheeeew.data.press.race

import com.pheeeew.domain.model.emotion.EmotionState

/** Monotonic milliseconds only. Neither retry nor another tap extends an input's lifetime. */
internal object PressRaceInputPolicy {
    const val BATCH_MS = 200L
    const val STATUS_MS = 10_000L
    const val RECOVERY_MS = 20_000L
    const val MAX_BATCHES = 20
    const val MAX_BYTES = 64 * 1024

    fun retryDelay(attempts: Int): Long = 250L * (1L shl (attempts - 1).coerceIn(0, 3))
}

internal data class PressRaceInputBatch(
    val input: PressRacePendingInput,
    val text: String,
    val firstAttempt: Long? = null,
    val nextAttempt: Long = 0L,
    val attempts: Int = 0,
) {
    val bytes: Int get() = text.encodeToByteArray().size
}

/** Confined to the input dispatcher. Removed IDs make late duplicate results harmless. */
internal class PressRaceInputLedger {
    private val entries = linkedMapOf<String, PressRaceInputBatch>()
    var date: String? = null
        private set
    var confirmed: Map<String, Long> = emptyMap()
        private set
    val batches: List<PressRaceInputBatch> get() = entries.values.toList()
    val pending: List<PressRacePendingInput> get() = batches.filter { it.firstAttempt != null }.map { it.input }

    fun canStore(batch: PressRaceInputBatch): Boolean =
        entries.size + 1 <= PressRaceInputPolicy.MAX_BATCHES &&
            batches.sumOf { it.bytes } + batch.bytes <= PressRaceInputPolicy.MAX_BYTES

    fun add(batch: PressRaceInputBatch) {
        require(batch.input.requestId !in entries && canStore(batch))
        entries[batch.input.requestId] = batch
    }

    fun attempted(
        id: String,
        now: Long,
    ) {
        val batch = entries[id] ?: return
        val attempts = batch.attempts + 1
        entries[id] =
            batch.copy(
                firstAttempt = batch.firstAttempt ?: now,
                nextAttempt = now + PressRaceInputPolicy.retryDelay(attempts),
                attempts = attempts,
            )
    }

    fun discardUnsent() {
        entries.keys.removeAll { entries[it]?.firstAttempt == null }
    }

    fun expired(now: Long): Boolean =
        batches.any { it.firstAttempt?.let { first -> now - first >= PressRaceInputPolicy.RECOVERY_MS } == true }

    fun ready(ready: PressRaceReady) {
        validateCounts(ready.counts)
        ready.results.forEach { validateResult(it) }
        date = ready.date
        confirmed = ready.counts.toMap()
        ready.results.forEach { settle(it, includedInBaseline = true) }
    }

    fun settle(
        result: PressRaceInputResult,
        includedInBaseline: Boolean = false,
    ): Boolean {
        val batch = entries[result.requestId] ?: return false
        validateResult(result)
        if (result.status != "APPLIED" && result.status != "REJECTED") return false
        if (result.status == "APPLIED" && !includedInBaseline && result.appliedDate == date) {
            confirmed =
                confirmed.toMutableMap().apply {
                    batch.input.counts.forEach { (emotion, count) ->
                        val previous = get(emotion) ?: 0L
                        require(previous <= Long.MAX_VALUE - count)
                        put(emotion, previous + count)
                    }
                }
        }
        entries.remove(result.requestId)
        return true
    }

    private fun validateResult(result: PressRaceInputResult) {
        val batch = entries[result.requestId] ?: return
        require(result.status in setOf("APPLIED", "REJECTED", "PENDING", "NOT_FOUND"))
        if (result.status == "APPLIED") {
            require(!result.appliedDate.isNullOrBlank())
            require(result.appliedCounts == batch.input.counts.mapValues { it.value.toLong() })
        }
    }

    private fun validateCounts(counts: Map<String, Long>) {
        require(counts.keys.all { key -> EmotionState.entries.any { it.name == key } })
        var total = 0L
        counts.values.forEach { value ->
            require(value >= 0L && total <= Long.MAX_VALUE - value)
            total += value
        }
    }
}
