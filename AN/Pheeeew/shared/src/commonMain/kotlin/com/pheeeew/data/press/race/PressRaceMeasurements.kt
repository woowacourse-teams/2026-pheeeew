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
    SendAttemptResult,
    FinalAck,
    RecoveryResult,
    FirstViewActivationToSnapshot,
    ReentryViewActivationToSnapshot,
    NetworkPathStatusObserved,
    NetworkPathUnavailableToAvailable,
    SocketLossDetected,
    SocketRecoveryAttemptFailed,
    SocketLossToValidatedBaseline,
    SnapshotToUi,
    SnapshotToDraw,
    CoverageGap,
    SinkLimitReached,
}

internal enum class PressRaceMeasureOutcome { Applied, Rejected }

internal enum class PressRaceHttpAttemptResult {
    Applied,
    Rejected,
    NotSent,
    RetryAfter,
    ContractPending,
    OutcomeUnknown,
    Cancelled,
}

internal enum class PressRaceRecoveryOutcome { Applied, Retired, Pending }

internal enum class PressRaceMeasureSource { Http, WebSocket, NetworkPath, Recorder }

/** Observer status only; Available does not claim that the internet or the server is reachable. */
internal enum class PressRaceNetworkPathState { Unknown, Unavailable, Available }

/** Monotonic timestamp captured when the active press view is delivered by its lifecycle binding. */
internal data class PressRaceViewActivation(
    val startedAtMillis: Long?,
)

/** Public diagnostic value has no credential, request ID, frame key, name or raw payload. */
internal data class PressRaceMeasureSample(
    val sample: Long,
    val kind: PressRaceMeasureKind,
    val millis: Long,
    val presses: Int = 0,
    val outcome: PressRaceMeasureOutcome? = null,
    val source: PressRaceMeasureSource = PressRaceMeasureSource.WebSocket,
    val failedAttempts: Int = 0,
    val observedAtMillis: Long? = null,
    val networkPathState: PressRaceNetworkPathState? = null,
    val networkPathAtSocketLoss: PressRaceNetworkPathState? = null,
    val attempt: Int? = null,
    val attemptResult: PressRaceHttpAttemptResult? = null,
    val retryAfterMillis: Long? = null,
    val recoveryOutcome: PressRaceRecoveryOutcome? = null,
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
        val source: PressRaceMeasureSource,
        val sent: Long? = null,
        val attemptCount: Int = 0,
        val attemptStarted: Long? = null,
        val attemptOpen: Boolean = false,
    )

    private data class Frame(
        val sample: Long,
        val received: Long,
        val ui: Boolean = false,
        val drawn: Boolean = false,
    )

    private data class ViewActivation(
        val sample: Long,
        val view: PressRaceView,
        val startedAtMillis: Long,
        val kind: PressRaceMeasureKind,
        val epoch: Long,
    )

    private data class SocketRecovery(
        val view: PressRaceView,
        val startedAtMillis: Long,
        val epoch: Long,
        val networkPathAtLoss: PressRaceNetworkPathState,
        val failedAttempts: Int = 0,
    )

    private data class State(
        val sequence: Long = 0,
        val inputs: Map<String, Input> = emptyMap(),
        val frames: Map<PressRaceFrameKey, Frame> = emptyMap(),
        val viewActivation: ViewActivation? = null,
        val socketRecovery: SocketRecovery? = null,
        val networkPath: PressRaceNetworkPathState = PressRaceNetworkPathState.Unknown,
        val networkUnavailableAtMillis: Long? = null,
        val samples: List<PressRaceMeasureSample> = emptyList(),
        val emitted: Int = 0,
        val coverageGapSources: Set<PressRaceMeasureSource> = emptySet(),
        val sinkLimitWarningEmitted: Boolean = false,
    )

    private data class Change(
        val state: State,
        val added: List<PressRaceMeasureSample> = emptyList(),
    )

    private val state = MutableStateFlow(State())
    val samples: List<PressRaceMeasureSample> get() = state.value.samples
    val isEnabled: Boolean get() = enabled

    fun beginViewActivation(
        view: PressRaceView,
        activation: PressRaceViewActivation,
        isReentry: Boolean,
        epoch: Long,
    ) = change { old ->
        val startedAtMillis = activation.startedAtMillis ?: return@change Change(old)
        val sample = old.sequence + 1
        Change(
            old.copy(
                sequence = sample,
                viewActivation =
                    ViewActivation(
                        sample,
                        view,
                        startedAtMillis,
                        if (isReentry) {
                            PressRaceMeasureKind.ReentryViewActivationToSnapshot
                        } else {
                            PressRaceMeasureKind.FirstViewActivationToSnapshot
                        },
                        epoch,
                    ),
            ),
        )
    }

    fun cancelViewActivation() = change { old -> Change(old.copy(viewActivation = null)) }

    /** A retry replaces the eligible epoch while preserving the original visible-entry timestamp. */
    fun viewActivationEpochChanged(epoch: Long) = change { old ->
        val activation = old.viewActivation ?: return@change Change(old)
        Change(old.copy(viewActivation = activation.copy(epoch = epoch)))
    }

    /** Call only after the current epoch's snapshot passed the gate and was published to session state. */
    fun viewActivationSnapshotPublished(
        snapshot: PressRaceSnapshot,
        epoch: Long,
    ) = change { old ->
        val activation = old.viewActivation ?: return@change Change(old)
        if (!snapshot.baseline || activation.epoch != epoch || activation.view != snapshot.view) {
            return@change Change(old)
        }
        val elapsed = safeNowMillis()?.minus(activation.startedAtMillis)
        if (elapsed == null) return@change Change(old.copy(viewActivation = null))
        Change(
            old.copy(viewActivation = null),
            listOf(
                PressRaceMeasureSample(
                    sample = activation.sample,
                    kind = activation.kind,
                    millis = elapsed,
                    source = PressRaceMeasureSource.WebSocket,
                ),
            ),
        )
    }

    /** Records observer state without claiming that a satisfied path can reach the internet or server. */
    fun networkPathObserved(
        path: PressRaceNetworkPathState,
        atMillis: Long?,
    ) = change { old ->
        if (path == PressRaceNetworkPathState.Unknown || path == old.networkPath) return@change Change(old)
        val samples = mutableListOf<PressRaceMeasureSample>()
        var unavailableAt = old.networkUnavailableAtMillis
        if (path == PressRaceNetworkPathState.Unavailable) {
            unavailableAt = atMillis
        } else {
            val started = unavailableAt
            if (started != null && atMillis != null && atMillis >= started) {
                samples +=
                    PressRaceMeasureSample(
                        sample = old.sequence + samples.size + 1,
                        kind = PressRaceMeasureKind.NetworkPathUnavailableToAvailable,
                        millis = atMillis - started,
                        source = PressRaceMeasureSource.NetworkPath,
                        observedAtMillis = atMillis,
                        networkPathState = path,
                    )
            }
            unavailableAt = null
        }
        if (atMillis != null) {
            samples +=
                PressRaceMeasureSample(
                    sample = old.sequence + samples.size + 1,
                    kind = PressRaceMeasureKind.NetworkPathStatusObserved,
                    millis = 0L,
                    source = PressRaceMeasureSource.NetworkPath,
                    observedAtMillis = atMillis,
                    networkPathState = path,
                )
        }
        Change(
            old.copy(
                sequence = old.sequence + samples.size,
                networkPath = path,
                networkUnavailableAtMillis = unavailableAt,
            ),
            samples,
        )
    }

    /** Start marker is captured only for an already-open socket failure supplied by the session owner. */
    fun socketLossDetected(
        view: PressRaceView,
        epoch: Long,
        atMillis: Long?,
        networkPath: PressRaceNetworkPathState,
    ) = change { old ->
        val at = atMillis ?: return@change Change(old)
        val current = old.socketRecovery
        if (current != null && current.view == view) {
            val sample =
                PressRaceMeasureSample(
                    sample = old.sequence + 1,
                    kind = PressRaceMeasureKind.SocketRecoveryAttemptFailed,
                    millis = 0L,
                    source = PressRaceMeasureSource.WebSocket,
                    observedAtMillis = at,
                    networkPathState = networkPath,
                )
            return@change Change(
                old.copy(
                    sequence = old.sequence + 1,
                    socketRecovery = current.copy(epoch = epoch, failedAttempts = current.failedAttempts + 1),
                ),
                listOf(sample),
            )
        }
        val sample =
            PressRaceMeasureSample(
                sample = old.sequence + 1,
                kind = PressRaceMeasureKind.SocketLossDetected,
                millis = 0L,
                source = PressRaceMeasureSource.WebSocket,
                observedAtMillis = at,
                networkPathAtSocketLoss = networkPath,
            )
        Change(
            old.copy(
                sequence = old.sequence + 1,
                socketRecovery = SocketRecovery(view, at, epoch, networkPath),
            ),
            listOf(sample),
        )
    }

    /** Failed connects/initializations during a pending recovery add evidence but never reset its start time. */
    fun socketRecoveryAttemptFailed(
        view: PressRaceView?,
        epoch: Long,
        atMillis: Long?,
        networkPath: PressRaceNetworkPathState,
    ) = change { old ->
        val recovery = old.socketRecovery ?: return@change Change(old)
        if (view != recovery.view || epoch != recovery.epoch) return@change Change(old)
        val at = atMillis ?: return@change Change(old)
        val sample =
            PressRaceMeasureSample(
                sample = old.sequence + 1,
                kind = PressRaceMeasureKind.SocketRecoveryAttemptFailed,
                millis = 0L,
                source = PressRaceMeasureSource.WebSocket,
                observedAtMillis = at,
                networkPathState = networkPath,
            )
        Change(
            old.copy(
                sequence = old.sequence + 1,
                socketRecovery = recovery.copy(failedAttempts = recovery.failedAttempts + 1),
            ),
            listOf(sample),
        )
    }

    /** Retries keep the first loss time, while stale epochs and a changed view cannot complete the sample. */
    fun socketRecoveryEpochChanged(
        epoch: Long,
        view: PressRaceView?,
    ) = change { old ->
        val recovery = old.socketRecovery ?: return@change Change(old)
        if (view != recovery.view) return@change Change(old.copy(socketRecovery = null))
        Change(old.copy(socketRecovery = recovery.copy(epoch = epoch)))
    }

    /** Call only after the current epoch's baseline passed SnapshotGate and was published. */
    fun socketRecoveryBaselinePublished(
        snapshot: PressRaceSnapshot,
        epoch: Long,
    ) = change { old ->
        val recovery = old.socketRecovery ?: return@change Change(old)
        if (!snapshot.baseline || recovery.epoch != epoch || recovery.view != snapshot.view) {
            return@change Change(old)
        }
        val elapsed = safeNowMillis()?.minus(recovery.startedAtMillis)
        if (elapsed == null) return@change Change(old.copy(socketRecovery = null))
        Change(
            old.copy(sequence = old.sequence + 1, socketRecovery = null),
            listOf(
                PressRaceMeasureSample(
                    sample = old.sequence + 1,
                    kind = PressRaceMeasureKind.SocketLossToValidatedBaseline,
                    millis = elapsed,
                    failedAttempts = recovery.failedAttempts,
                    source = PressRaceMeasureSource.WebSocket,
                    networkPathAtSocketLoss = recovery.networkPathAtLoss,
                ),
            ),
        )
    }

    fun cancelSocketRecovery() = change { old -> Change(old.copy(socketRecovery = null)) }

    /** Clock failures omit diagnostic samples instead of escaping into UI, actor, or input callbacks. */
    fun nowMillis(): Long? = safeNowMillis()

    private fun safeNowMillis(): Long? = runCatching { clock() }.getOrNull()

    val pendingInputCount: Int get() = state.value.inputs.size

    fun coverageGap(
        source: PressRaceMeasureSource,
        skippedPresses: Long,
    ) {
        if (!enabled || skippedPresses <= 0L) return
        while (true) {
            val old = state.value
            if (source in old.coverageGapSources) return
            val sampleIndex = old.sequence + 1
            val sample =
                PressRaceMeasureSample(
                    sample = sampleIndex,
                    kind = PressRaceMeasureKind.CoverageGap,
                    millis = 0L,
                    presses = skippedPresses.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    source = source,
                )
            val next =
                old.copy(
                    sequence = sampleIndex,
                    samples = (old.samples + sample).takeLast(128),
                    coverageGapSources = old.coverageGapSources + source,
                )
            if (state.compareAndSet(old, next)) {
                // Coverage gaps bypass the regular sample cap, but are emitted once per source/session.
                runCatching { sink(sample) }
                return
            }
        }
    }

    private fun sinkLimitReached() {
        while (true) {
            val old = state.value
            if (old.sinkLimitWarningEmitted) return
            val sampleIndex = old.sequence + 1
            val sample =
                PressRaceMeasureSample(
                    sample = sampleIndex,
                    kind = PressRaceMeasureKind.SinkLimitReached,
                    millis = 0L,
                    source = PressRaceMeasureSource.Recorder,
                )
            val next =
                old.copy(
                    sequence = sampleIndex,
                    samples = (old.samples + sample).takeLast(128),
                    sinkLimitWarningEmitted = true,
                )
            if (state.compareAndSet(old, next)) {
                // One bounded warning reports that regular samples stopped at their session cap.
                runCatching { sink(sample) }
                return
            }
        }
    }

    fun tap(
        id: String,
        source: PressRaceMeasureSource = PressRaceMeasureSource.WebSocket,
        atMillis: Long? = null,
    ) =
        change { old ->
            val time = atMillis ?: safeNowMillis() ?: return@change Change(old)
            val previous = old.inputs[id]
            if (previous == null && old.inputs.size >= 20) return@change Change(old)
            val input =
                previous?.copy(last = time, count = previous.count + 1)
                    ?: Input(old.sequence + 1, time, time, 1, source)
            Change(old.copy(sequence = maxOf(old.sequence, input.sample), inputs = old.inputs + (id to input)))
        }

    fun discard(id: String) = change { old -> Change(old.copy(inputs = old.inputs - id)) }

    fun firstSend(id: String) =
        change { old ->
            val input = old.inputs[id] ?: return@change Change(old)
            if (input.sent != null) return@change Change(old)
            val time = safeNowMillis() ?: return@change Change(old.copy(inputs = old.inputs - id))
            Change(
                old.copy(inputs = old.inputs + (id to input.copy(sent = time))),
                listOf(
                    PressRaceMeasureSample(
                        input.sample,
                        PressRaceMeasureKind.FirstTapToSend,
                        time - input.first,
                        input.count,
                        source = input.source,
                    ),
                    PressRaceMeasureSample(
                        input.sample,
                        PressRaceMeasureKind.LastTapToSend,
                        time - input.last,
                        input.count,
                        source = input.source,
                    ),
                ),
            )
        }

    /** Starts one real sender invocation while preserving the first-attempt latency marker. */
    fun sendAttemptStarted(id: String) =
        change { old ->
            val input = old.inputs[id] ?: return@change Change(old)
            val time = safeNowMillis() ?: return@change Change(old.copy(inputs = old.inputs - id))
            val attempt = input.attemptCount + 1
            val started = input.copy(
                sent = input.sent ?: time,
                attemptCount = attempt,
                attemptStarted = time,
                attemptOpen = true,
            )
            val firstSendSamples =
                if (input.sent != null) {
                    emptyList()
                } else {
                    listOf(
                        PressRaceMeasureSample(
                            input.sample,
                            PressRaceMeasureKind.FirstTapToSend,
                            time - input.first,
                            input.count,
                            source = input.source,
                        ),
                        PressRaceMeasureSample(
                            input.sample,
                            PressRaceMeasureKind.LastTapToSend,
                            time - input.last,
                            input.count,
                            source = input.source,
                        ),
                    )
                }
            Change(old.copy(inputs = old.inputs + (id to started)), firstSendSamples)
        }

    /** Records a sanitized result for the currently open attempt; repeated callbacks are ignored. */
    fun sendAttemptResult(
        id: String,
        result: PressRaceHttpAttemptResult,
        retryAfterMillis: Long? = null,
    ) =
        change { old ->
            val input = old.inputs[id] ?: return@change Change(old)
            if (!input.attemptOpen || input.attemptCount <= 0) return@change Change(old)
            val started = input.attemptStarted ?: return@change Change(old)
            val time = safeNowMillis() ?: return@change Change(old.copy(inputs = old.inputs - id))
            val elapsed = time - started
            if (elapsed < 0L) return@change Change(old.copy(inputs = old.inputs - id))
            Change(
                old.copy(inputs = old.inputs + (id to input.copy(attemptOpen = false))),
                listOf(
                    PressRaceMeasureSample(
                        sample = input.sample,
                        kind = PressRaceMeasureKind.SendAttemptResult,
                        millis = elapsed,
                        presses = input.count,
                        source = input.source,
                        attempt = input.attemptCount,
                        attemptResult = result,
                        retryAfterMillis = retryAfterMillis?.coerceAtLeast(0L),
                    ),
                ),
            )
        }

    /** Records an unknown-write comparison result without exposing snapshot contents or identifiers. */
    fun recoveryResult(
        id: String,
        result: PressRaceRecoveryOutcome,
    ) =
        change { old ->
            val input = old.inputs[id] ?: return@change Change(old)
            val sent = input.sent ?: return@change Change(old)
            val time = safeNowMillis() ?: return@change Change(old.copy(inputs = old.inputs - id))
            val elapsed = time - sent
            if (elapsed < 0L) return@change Change(old.copy(inputs = old.inputs - id))
            Change(
                old.copy(
                    inputs =
                        if (result == PressRaceRecoveryOutcome.Pending) {
                            old.inputs
                        } else {
                            old.inputs - id
                        },
                ),
                listOf(
                    PressRaceMeasureSample(
                        sample = input.sample,
                        kind = PressRaceMeasureKind.RecoveryResult,
                        millis = elapsed,
                        presses = input.count,
                        source = input.source,
                        recoveryOutcome = result,
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
                    safeNowMillis()?.let { time ->
                        listOf(
                            PressRaceMeasureSample(
                                input.sample,
                                if (recovered) PressRaceMeasureKind.RecoveryResult else PressRaceMeasureKind.FinalAck,
                                time - it,
                                input.count,
                                outcome,
                                input.source,
                            ),
                        )
                    }.orEmpty()
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
        val time = safeNowMillis() ?: return@change Change(old.copy(frames = old.frames + (key to updated)))
        Change(
            old.copy(frames = old.frames + (key to updated)),
            listOf(
                PressRaceMeasureSample(
                    frame.sample,
                    if (draw) PressRaceMeasureKind.SnapshotToDraw else PressRaceMeasureKind.SnapshotToUi,
                    time - frame.received,
                    source = PressRaceMeasureSource.WebSocket,
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
                if (old.emitted < 512 && next.emitted == 512) sinkLimitReached()
                return
            }
        }
    }
}
