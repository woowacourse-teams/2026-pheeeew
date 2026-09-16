package com.pheeeew.feature.map.breath

import com.pheeeew.core.audio.BreathInput
import com.pheeeew.core.audio.BreathInputError
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
        breathInput.stop()
        eventJob.cancel()
    }

    private fun dispatch(event: BreathSessionEvent) {
        controlEvents.trySend(event)
    }

    private fun handle(sample: MeasuredStrength) {
        val currentState = _session.value
        if (sample.sessionId != currentState.sessionId || !currentState.isInputActive()) return

        val previousSampleAt = lastSampleAt ?: sample.at
        lastSampleAt = sample.at
        handle(
            BreathSessionEvent.StrengthSample(
                sessionId = sample.sessionId,
                strength = sample.strength,
                elapsed = (sample.at - previousSampleAt).coerceAtLeast(ZERO),
            ),
        )
    }

    private fun handle(event: BreathSessionEvent) {
        val transition = reducer.reduce(_session.value, event)
        _session.value = transition.state
        transition.effects.forEach(::execute)
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
        runCatching {
            breathInput.start(
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
