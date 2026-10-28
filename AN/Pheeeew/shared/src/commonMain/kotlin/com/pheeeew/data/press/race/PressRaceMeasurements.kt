package com.pheeeew.data.press.race

import kotlinx.coroutines.flow.MutableStateFlow

internal data class PressRaceFrameKey(
    val server: String,
    val connection: String,
    val date: String,
    val view: PressRaceView,
    val version: String,
)

internal fun PressRaceSnapshot.frameKey() =
    PressRaceFrameKey(serverGeneration, connectionGeneration, date, view, snapshotVersion)

internal enum class PressRaceMeasureKind {
    FirstTapToSend,
    LastTapToSend,
    FinalAck,
    RecoveryResult,
    SnapshotToUi,
    SnapshotToDraw,
}

internal enum class PressRaceMeasureOutcome { Applied, Rejected }

/** Public diagnostic value has no credential, request ID, frame key, name or raw payload. */
internal data class PressRaceMeasureSample(
    val sample: Long,
    val kind: PressRaceMeasureKind,
    val millis: Long,
    val presses: Int = 0,
    val outcome: PressRaceMeasureOutcome? = null,
)

/** Debug-only bounded recorder. CAS makes UI/input and socket/frame callbacks share one monotonic clock safely. */
internal class PressRaceMeasurements(
    private val enabled: Boolean = false,
    private val clock: () -> Long = newPressRaceClock(),
    private val sink: (PressRaceMeasureSample) -> Unit = {},
) {
    private data class Input(
        val sample: Long,
        val first: Long,
        val last: Long,
        val count: Int,
        val sent: Long? = null,
    )

    private data class Frame(
        val sample: Long,
        val received: Long,
        val ui: Boolean = false,
        val drawn: Boolean = false,
    )

    private data class State(
        val sequence: Long = 0,
        val inputs: Map<String, Input> = emptyMap(),
        val frames: Map<PressRaceFrameKey, Frame> = emptyMap(),
        val samples: List<PressRaceMeasureSample> = emptyList(),
        val emitted: Int = 0,
    )

    private data class Change(
        val state: State,
        val added: List<PressRaceMeasureSample> = emptyList(),
    )

    private val state = MutableStateFlow(State())
    val samples: List<PressRaceMeasureSample> get() = state.value.samples
    val isEnabled: Boolean get() = enabled

    fun nowMillis(): Long = clock()

    fun tap(id: String) =
        change { old ->
            val time = clock()
            val previous = old.inputs[id]
            if (previous == null && old.inputs.size >= 20) return@change Change(old)
            val input =
                previous?.copy(last = time, count = previous.count + 1)
                    ?: Input(old.sequence + 1, time, time, 1)
            Change(old.copy(sequence = maxOf(old.sequence, input.sample), inputs = old.inputs + (id to input)))
        }

    fun discard(id: String) = change { old -> Change(old.copy(inputs = old.inputs - id)) }

    fun firstSend(id: String) =
        change { old ->
            val input = old.inputs[id] ?: return@change Change(old)
            if (input.sent != null) return@change Change(old)
            val time = clock()
            Change(
                old.copy(inputs = old.inputs + (id to input.copy(sent = time))),
                listOf(
                    PressRaceMeasureSample(
                        input.sample,
                        PressRaceMeasureKind.FirstTapToSend,
                        time - input.first,
                        input.count,
                    ),
                    PressRaceMeasureSample(
                        input.sample,
                        PressRaceMeasureKind.LastTapToSend,
                        time - input.last,
                        input.count,
                    ),
                ),
            )
        }

    fun finalResult(
        id: String,
        status: String,
        recovered: Boolean = false,
    ) = change { old ->
        val input = old.inputs[id] ?: return@change Change(old)
        val outcome =
            when (status) {
                "APPLIED" -> PressRaceMeasureOutcome.Applied
                "REJECTED" -> PressRaceMeasureOutcome.Rejected
                else -> return@change Change(old)
            }
        val sent = input.sent
        val sample =
            sent
                ?.let {
                    listOf(
                        PressRaceMeasureSample(
                            input.sample,
                            if (recovered) PressRaceMeasureKind.RecoveryResult else PressRaceMeasureKind.FinalAck,
                            clock() - it,
                            input.count,
                            outcome,
                        ),
                    )
                }.orEmpty()
        Change(old.copy(inputs = old.inputs - id), sample)
    }

    fun received(
        key: PressRaceFrameKey,
        time: Long,
    ) = change { old ->
        if (key in old.frames) return@change Change(old)
        val kept =
            old.frames.entries
                .toList()
                .takeLast(3)
                .associate { it.toPair() }
        val sample = old.sequence + 1
        Change(old.copy(sequence = sample, frames = kept + (key to Frame(sample, time))))
    }

    fun uiApplied(key: PressRaceFrameKey) = frame(key, draw = false)

    fun drawn(key: PressRaceFrameKey) = frame(key, draw = true)

    private fun frame(
        key: PressRaceFrameKey,
        draw: Boolean,
    ) = change { old ->
        val frame = old.frames[key] ?: return@change Change(old)
        if (if (draw) frame.drawn else frame.ui) return@change Change(old)
        val updated = if (draw) frame.copy(drawn = true) else frame.copy(ui = true)
        Change(
            old.copy(frames = old.frames + (key to updated)),
            listOf(
                PressRaceMeasureSample(
                    frame.sample,
                    if (draw) PressRaceMeasureKind.SnapshotToDraw else PressRaceMeasureKind.SnapshotToUi,
                    clock() - frame.received,
                ),
            ),
        )
    }

    private fun change(block: (State) -> Change) {
        if (!enabled) return
        while (true) {
            val old = state.value
            val changed = block(old)
            val valid = changed.added.filter { it.millis >= 0 }
            val emitted = valid.take((512 - old.emitted).coerceAtLeast(0))
            val next =
                changed.state.copy(
                    samples = (old.samples + valid).takeLast(128),
                    emitted = old.emitted + emitted.size,
                )
            if (state.compareAndSet(old, next)) {
                emitted.forEach { sample -> runCatching { sink(sample) } }
                return
            }
        }
    }
}
