package com.pheeeew.data.press.race

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSessionNotice
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.domain.repository.PressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Main-confined input owner; synchronous accept and all observer callbacks use the same dispatcher. */
internal class PressRaceInputCoordinator(
    private val port: PressRaceInputPort,
    private val scope: CoroutineScope,
    private val now: () -> Long = monotonicClock(),
    private val newId: () -> String = { Uuid.random().toString() },
    private val measurements: PressRaceMeasurements = PressRaceMeasurements(),
) : PressRepository,
    PressRaceInputObserver {
    private val ownerContext = scope.coroutineContext.minusKey(Job)
    private val ledger = PressRaceInputLedger()
    private val mutableState = MutableStateFlow(PressSessionState(isLoadingAll = false, isInputEnabled = false))
    override val state = mutableState.asStateFlow()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var buffered: PressRaceInputBatch? = null
    private var flushAt: Long? = null
    private var lastStatus: Long? = null
    private var baselineGeneration: String? = null
    private val queued = mutableSetOf<String>()
    private var statusQueued = false
    private var rejected = false
    private var requestedRecovery: String? = null

    init {
        port.observeInputs(this)
        scope.launch {
            port.state.collect { session ->
                if (session.phase != PressRacePhase.Ready || session.view != PressRaceView.PRESS) {
                    discardUnsent()
                }
                publish()
                wake.trySend(Unit)
            }
        }
        scope.launch {
            while (true) {
                tick()
                val wait = nextWait()
                if (wait == null) wake.receive() else withTimeoutOrNull(wait) { wake.receive() }
            }
        }
    }

    /** Called on the UI dispatcher, just like the HTTP repository's accept contract. */
    override fun accept(emotion: EmotionState): PressAcceptance {
        val session = port.state.value
        if (!canInput(session)) return PressAcceptance.Unavailable
        val previous = buffered
        val input =
            PressRacePendingInput(
                previous?.input?.requestId ?: newId(),
                session.ready!!.connectionGeneration,
                previous
                    ?.input
                    ?.counts
                    .orEmpty()
                    .toMutableMap()
                    .apply { put(emotion.name, (get(emotion.name) ?: 0) + 1) },
            )
        val candidate = PressRaceInputBatch(input, PressRaceProtocol.submit(input))
        if (!ledger.canStore(candidate) || !numericCapacity(emotion)) return PressAcceptance.NumericLimit
        buffered = candidate
        measurements.tap(input.requestId)
        if (flushAt == null) flushAt = now() + PressRaceInputPolicy.BATCH_MS
        rejected = false
        if (input.counts.values.sum() == 100) freeze()
        publish()
        wake.trySend(Unit)
        return PressAcceptance.Accepted
    }

    override suspend fun pending(): List<PressRacePendingInput> =
        withContext(ownerContext) {
            discardUnsent()
            publish()
            ledger.pending
        }

    override suspend fun ready(value: PressRaceReady) =
        withContext(ownerContext) {
            val outstanding = ledger.batches.map { it.input.requestId }.toSet()
            ledger.ready(value)
            value.results.filter { it.requestId in outstanding }.forEach {
                measurements.finalResult(it.requestId, it.status, recovered = true)
            }
            discardUnsent()
            baselineGeneration = value.connectionGeneration
            lastStatus = null
            requestedRecovery = null
            publish()
            wake.trySend(Unit)
            Unit
        }

    override suspend fun result(value: PressRaceInputResult) =
        withContext(ownerContext) {
            if (ledger.settle(value)) {
                measurements.finalResult(value.requestId, value.status)
                if (value.status == "REJECTED") rejected = true
            }
            publish()
            wake.trySend(Unit)
            Unit
        }

    // WS counts come from ready/results, never HTTP aggregate inference or a fallback sender.
    override fun refreshToday(force: Boolean) = Unit

    override fun retryUnsent() {
        wake.trySend(Unit)
    }

    override fun onForeground() {
        wake.trySend(Unit)
    }

    private fun discardUnsent() {
        buffered?.let { measurements.discard(it.input.requestId) }
        ledger.batches.filter { it.firstAttempt == null }.forEach { measurements.discard(it.input.requestId) }
        buffered = null
        flushAt = null
        ledger.discardUnsent()
    }

    private fun freeze() {
        buffered?.let { ledger.add(it) }
        buffered = null
        flushAt = null
    }

    private fun active(): Boolean =
        port.state.value.let {
            it.phase == PressRacePhase.Ready && it.view != null && it.ready?.connectionGeneration == baselineGeneration
        }

    private fun canInput(session: PressRaceSessionState): Boolean =
        active() && session.view == PressRaceView.PRESS && !ledger.expired(now())

    private fun numericCapacity(emotion: EmotionState): Boolean {
        val confirmed = ledger.confirmed.values.sum()
        val pending = outstanding().values.sum()
        return confirmed < Long.MAX_VALUE - pending &&
            (ledger.confirmed[emotion.name] ?: 0L) < Long.MAX_VALUE - (outstanding()[emotion.name] ?: 0L)
    }

    private fun tick() {
        val time = now()
        if (flushAt?.let { time >= it } == true) freeze()
        if (!active() || ledger.expired(time)) {
            if (ledger.expired(time)) {
                discardUnsent()
                val generation =
                    port.state.value.ready
                        ?.connectionGeneration
                if (active() && generation != null && generation != requestedRecovery) {
                    requestedRecovery = generation
                    port.requestRecovery(generation)
                }
                publish()
            }
            return
        }
        val generation =
            port.state.value.ready!!
                .connectionGeneration
        ledger.batches.filter { it.input.requestId !in queued }.forEach { batch ->
            val age = batch.firstAttempt?.let { time - it }
            if (age == null || (age < PressRaceInputPolicy.STATUS_MS && time >= batch.nextAttempt)) {
                val id = batch.input.requestId
                queued.add(id)
                scope.launch {
                    try {
                        port.sendInput(batch.text, generation) {
                            withContext(ownerContext) {
                                val current = ledger.batches.firstOrNull { it.input.requestId == id }
                                val allowed =
                                    active() && !ledger.expired(now()) && current != null &&
                                        (
                                            current.firstAttempt?.let { now() - it < PressRaceInputPolicy.STATUS_MS } !=
                                                false
                                        )
                                if (allowed) {
                                    ledger.attempted(id, now())
                                    measurements.firstSend(id)
                                    publish()
                                    wake.trySend(Unit)
                                }
                                allowed
                            }
                        }
                    } finally {
                        queued.remove(id)
                        wake.trySend(Unit)
                    }
                }
            }
        }
        val ids =
            ledger.batches
                .filter {
                    it.firstAttempt?.let { first ->
                        time - first in
                            PressRaceInputPolicy.STATUS_MS until PressRaceInputPolicy.RECOVERY_MS
                    } ==
                        true
                }.map { it.input.requestId }
        if (ids.isNotEmpty() && !statusQueued && (lastStatus?.let { time - it >= 1_000L } != false)) {
            statusQueued = true
            scope.launch {
                try {
                    port.sendInput(PressRaceProtocol.status(ids), generation) {
                        withContext(ownerContext) {
                            val allowed =
                                active() && !ledger.expired(now()) &&
                                    ids.any { id ->
                                        ledger.batches.any {
                                            it.input.requestId == id &&
                                                it.firstAttempt?.let { first ->
                                                    now() - first in 10_000L until 20_000L
                                                } ==
                                                true
                                        }
                                    } && (lastStatus?.let { now() - it >= 1_000L } != false)
                            if (allowed) lastStatus = now()
                            allowed
                        }
                    }
                } finally {
                    statusQueued = false
                    wake.trySend(Unit)
                }
            }
        }
        publish()
    }

    private fun nextWait(): Long? {
        val time = now()
        val deadlines = mutableListOf<Long>()
        flushAt?.let { deadlines.add(it) }
        if (active() && !ledger.expired(time)) {
            ledger.batches.forEach { batch ->
                val first = batch.firstAttempt
                if (first == null) {
                    if (batch.input.requestId !in queued) deadlines.add(time)
                } else {
                    if (time < first + 10_000L && batch.input.requestId !in queued) deadlines.add(batch.nextAttempt)
                    if (time < first + 10_000L) deadlines.add(first + 10_000L)
                    if (time < first + 20_000L) {
                        deadlines.add(first + 20_000L)
                        if (time >= first + 10_000L && !statusQueued) deadlines.add(lastStatus?.plus(1_000L) ?: time)
                    }
                }
            }
        }
        return deadlines.minOrNull()?.let { (it - time).coerceAtLeast(1L) }
    }

    private fun outstanding(): Map<String, Long> =
        buildMap {
            (ledger.batches + listOfNotNull(buffered)).forEach { batch ->
                batch.input.counts.forEach { (emotion, count) -> put(emotion, (get(emotion) ?: 0L) + count) }
            }
        }

    private fun publish() {
        val counts = outstanding()
        val unknown = ledger.expired(now())
        val dateIsCurrent =
            port.state.value.dateFloor
                ?.let { floor -> ledger.date?.let { it >= floor } == true } != false
        val visibleCounts = if (dateIsCurrent) counts else emptyMap()
        val snapshot =
            ledger.date?.takeIf { dateIsCurrent }?.let { date ->
                val confirmed = EmotionState.entries.associateWith { ledger.confirmed[it.name] ?: 0L }
                MyDailyPressSnapshot(date, confirmed, confirmed.values.sum())
            }
        mutableState.value =
            PressSessionState(
                myToday = snapshot,
                isLoadingMy = snapshot == null,
                isLoadingAll = false,
                optimisticCounts = EmotionState.entries.associateWith { visibleCounts[it.name] ?: 0L },
                pendingPressCount = counts.values.sum(),
                isSending = active() && ledger.batches.isNotEmpty(),
                isOutcomeUnknown = unknown,
                isInputEnabled = canInput(port.state.value) && ledger.batches.size < PressRaceInputPolicy.MAX_BATCHES,
                notice =
                    when {
                        unknown -> PressSessionNotice.OutcomeUnknown
                        rejected -> PressSessionNotice.Rejected
                        else -> null
                    },
            )
    }
}

private fun monotonicClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}
