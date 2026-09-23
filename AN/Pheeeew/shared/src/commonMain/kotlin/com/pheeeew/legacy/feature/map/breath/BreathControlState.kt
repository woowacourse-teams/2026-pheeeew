package com.pheeeew.legacy.feature.map.breath

import com.pheeeew.legacy.core.audio.BreathInput
import com.pheeeew.legacy.core.audio.BreathInputError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeSource

private typealias StrengthObserver = (strength: Float, activeThreshold: Float, isActive: Boolean) -> Unit
private typealias InputStoppedObserver = (finalGrowth: Float, reason: String, interrupted: Boolean) -> Unit

/**
 * 한숨 입력의 세션 수명과 side effect를 Compose UI에서 분리한 state holder입니다.
 *
 * strength는 최신 값만 의미가 있으므로 conflated channel을 사용하고, 권한·중지·오류 같은
 * 제어 이벤트는 별도 무제한 channel에서 유실되지 않도록 처리합니다.
 */
class BreathControlState(
    private val breathInput: BreathInput,
    private val scope: CoroutineScope,
    private val ensureLocationPermission: suspend () -> Boolean,
    private val reducer: BreathSessionReducer = BreathSessionReducer(),
    private val onMicrophonePermissionResult: (Boolean) -> Unit = {},
    private val onInputStartRequested: () -> Unit = {},
    private val onInputReady: () -> Unit = {},
    private val onStrengthEvaluated: StrengthObserver = { _, _, _ -> },
    private val onBreathSample: (active: Boolean, elapsedMs: Long, growth: Float) -> Unit = { _, _, _ -> },
    private val onInputStopped: InputStoppedObserver = { _, _, _ -> },
    private val onInputFailed: (BreathInputError) -> Unit = {},
) {
    private data class MeasuredStrength(
        val sessionId: Long,
        val strength: Float,
        val at: Duration,
    )

    private val controlEvents = Channel<BreathSessionEvent>(Channel.UNLIMITED)
    private val strengthSamples = Channel<MeasuredStrength>(Channel.CONFLATED)
    private val _session = MutableStateFlow<BreathSessionState>(BreathSessionState.Idle())
    private val _errors = Channel<BreathInputError>(Channel.UNLIMITED)
    private val _burstRevision = MutableStateFlow(0L)
    private val _needsMoreRevision = MutableStateFlow(0L)

    private var permissionWarmup: Deferred<Boolean>? = null
    private var permissionJob: Job? = null
    private var locationPermissionJob: Job? = null
    private var lastSampleAt: Duration? = null
    private var readySessionId: Long? = null
    private var pendingPreReadySample: MeasuredStrength? = null
    private val sampleTimeOrigin = TimeSource.Monotonic.markNow()

    private val eventJob =
        scope.launch {
            while (isActive) {
                select<Unit> {
                    controlEvents.onReceive { event -> handle(event) }
                    strengthSamples.onReceive { sample -> handle(sample) }
                }
            }
        }

    val session: StateFlow<BreathSessionState> = _session.asStateFlow()
    val errors = _errors.receiveAsFlow()
    val burstRevision: StateFlow<Long> = _burstRevision.asStateFlow()
    val needsMoreRevision: StateFlow<Long> = _needsMoreRevision.asStateFlow()

    fun warmUpMicrophonePermission() {
        if (permissionWarmup?.isActive == true) return
        permissionWarmup =
            scope.async {
                requestPermissionSafely()
            }
    }

    fun start() {
        dispatch(BreathSessionEvent.StartRequested)
    }

    fun release(
        upwardDistanceDp: Float,
        upwardVelocityDpPerSecond: Float,
    ) {
        activeSessionId()?.let { sessionId ->
            dispatch(
                BreathSessionEvent.ReleaseRequested(
                    sessionId = sessionId,
                    upwardDistanceDp = upwardDistanceDp,
                    upwardVelocityDpPerSecond = upwardVelocityDpPerSecond,
                ),
            )
        }
    }

    fun cancel() {
        dispatch(BreathSessionEvent.CancelRequested)
    }

    fun lifecycleStopped() {
        dispatch(BreathSessionEvent.LifecycleStopped)
    }

    fun burstFinished(sessionId: Long) {
        dispatch(BreathSessionEvent.BurstFinished(sessionId))
    }

    fun dispose() {
        permissionWarmup?.cancel()
        permissionJob?.cancel()
        locationPermissionJob?.cancel()
        lastSampleAt = null
        _session.value.takeIf { it.isInputActive() }?.let {
            onInputStopped(it.growth, "dispose", true)
        }
        breathInput.stop()
        eventJob.cancel()
    }

    private fun dispatch(event: BreathSessionEvent) {
        controlEvents.trySend(event)
    }

    private fun handle(sample: MeasuredStrength) {
        val currentState = _session.value
        if (sample.sessionId != currentState.sessionId || !currentState.isInputActive()) return
        if (readySessionId != sample.sessionId) {
            pendingPreReadySample = sample
            return
        }

        val previousSampleAt = lastSampleAt ?: sample.at
        lastSampleAt = sample.at
        val sampleElapsed = (sample.at - previousSampleAt).coerceAtLeast(ZERO)
        val strength = sample.strength.coerceIn(0f, 1f)
        val activeThreshold = reducer.activeThreshold(currentState) ?: return
        val isActive = strength >= activeThreshold
        onStrengthEvaluated(strength, activeThreshold, isActive)
        handle(
            BreathSessionEvent.StrengthSample(
                sessionId = sample.sessionId,
                strength = strength,
                elapsed = sampleElapsed,
            ),
        )
        onBreathSample(isActive, sampleElapsed.inWholeMilliseconds, _session.value.growth)
    }

    private fun handle(event: BreathSessionEvent) {
        val currentState = _session.value
        when (event) {
            is BreathSessionEvent.PermissionResult -> {
                if (currentState is BreathSessionState.RequestingPermission &&
                    currentState.sessionId == event.sessionId
                ) {
                    onMicrophonePermissionResult(event.granted)
                }
            }

            is BreathSessionEvent.InputReady -> {
                if (currentState.sessionId == event.sessionId && currentState.isInputActive()) {
                    readySessionId = event.sessionId
                    onInputReady()
                }
            }

            is BreathSessionEvent.InputFailed -> {
                if (currentState.sessionId == event.sessionId && currentState !is BreathSessionState.Idle) {
                    onInputFailed(event.error)
                }
            }

            else -> {}
        }
        val transition = reducer.reduce(_session.value, event)
        if (transition.effects.any { it is BreathSessionEffect.StopInput }) {
            val reason =
                when (event) {
                    is BreathSessionEvent.ReleaseRequested -> "release"
                    BreathSessionEvent.CancelRequested -> "user_cancel"
                    BreathSessionEvent.LifecycleStopped -> "background"
                    is BreathSessionEvent.InputFailed -> "input_failed"
                    else -> "unknown"
                }
            onInputStopped(
                currentState.growth,
                reason,
                reason in setOf("background", "input_failed", "unknown"),
            )
        }
        _session.value = transition.state
        transition.effects.forEach(::execute)
        if (event is BreathSessionEvent.InputReady && readySessionId == event.sessionId) {
            pendingPreReadySample
                ?.takeIf { it.sessionId == event.sessionId }
                ?.also { pendingPreReadySample = null }
                ?.let(::handle)
        }
    }

    private fun execute(effect: BreathSessionEffect) {
        when (effect) {
            is BreathSessionEffect.RequestMicrophonePermission -> requestMicrophonePermission(effect.sessionId)
            is BreathSessionEffect.RequestLocationPermission -> requestLocationPermission(effect.sessionId)
            is BreathSessionEffect.StartInput -> startInput(effect.sessionId)
            is BreathSessionEffect.StopInput -> stopInput()
            is BreathSessionEffect.StartBurstAnimation -> _burstRevision.value += 1L
            is BreathSessionEffect.ShowNeedsMore -> _needsMoreRevision.value += 1L
            is BreathSessionEffect.ShowError -> _errors.trySend(effect.error)
        }
    }

    private fun requestMicrophonePermission(sessionId: Long) {
        permissionJob?.cancel()
        val warmup = permissionWarmup
        permissionWarmup = null
        permissionJob =
            scope.launch {
                val granted = warmup?.await() ?: requestPermissionSafely()
                controlEvents.trySend(BreathSessionEvent.PermissionResult(sessionId, granted))
            }
    }

    private fun requestLocationPermission(sessionId: Long) {
        locationPermissionJob?.cancel()
        locationPermissionJob =
            scope.launch {
                val granted = runCatchingCancellable { ensureLocationPermission() } ?: false
                controlEvents.trySend(BreathSessionEvent.LocationPermissionResult(sessionId, granted))
            }
    }

    private fun startInput(sessionId: Long) {
        while (strengthSamples.tryReceive().isSuccess) {
            // Drop samples that were queued before this session started.
        }
        lastSampleAt = sampleTimeOrigin.elapsedNow()
        readySessionId = null
        pendingPreReadySample = null
        onInputStartRequested()
        runCatching {
            breathInput.start(
                onReady = {
                    controlEvents.trySend(BreathSessionEvent.InputReady(sessionId))
                },
                onStrengthChanged = { strength ->
                    strengthSamples.trySend(
                        MeasuredStrength(
                            sessionId = sessionId,
                            strength = strength,
                            at = sampleTimeOrigin.elapsedNow(),
                        ),
                    )
                },
                onError = { error ->
                    controlEvents.trySend(BreathSessionEvent.InputFailed(sessionId, error))
                },
            )
        }.onFailure {
            controlEvents.trySend(BreathSessionEvent.InputFailed(sessionId, BreathInputError.StartFailed))
        }
    }

    private fun stopInput() {
        permissionJob?.cancel()
        locationPermissionJob?.cancel()
        lastSampleAt = null
        readySessionId = null
        pendingPreReadySample = null
        while (strengthSamples.tryReceive().isSuccess) {
            // Drop samples from the stopped session.
        }
        breathInput.stop()
    }

    private fun activeSessionId(): Long? =
        _session.value
            .takeIf { it.isInputActive() }
            ?.sessionId

    private fun BreathSessionState.isInputActive(): Boolean =
        this is BreathSessionState.Listening ||
            this is BreathSessionState.NeedsMore ||
            this is BreathSessionState.Quiet

    private suspend fun requestPermissionSafely(): Boolean =
        runCatchingCancellable { breathInput.requestPermission() } ?: false

    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
}
