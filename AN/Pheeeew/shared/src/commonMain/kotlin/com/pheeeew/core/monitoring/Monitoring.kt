package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.tracker.BreathCaptureTracker
import com.pheeeew.core.monitoring.tracker.MapVisitTracker
import com.pheeeew.core.monitoring.tracker.SaveTracker
import com.pheeeew.core.monitoring.tracker.SighAttemptTracker
import com.pheeeew.core.monitoring.tracker.VisitTracker
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

// 외부에 공개하는 진입점
class Monitoring(
    private val config: MonitoringConfig,
    private val store: MonitoringStore,
    private val transport: MonitoringTransport,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val elapsed: () -> Long = monotonicClock(),
    private val id: () -> String = { Uuid.random().toString() },
    newInstallation: Boolean = false,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val stateStore = MonitoringStateStore(store, json, config, id, newInstallation)
    private var state: MonitoringState
        get() = stateStore.state
        set(value) = stateStore.replace(value)
    private val processId = id()
    private val recorder by lazy {
        MonitoringEventRecorder(
            config = config,
            stateStore = stateStore,
            transport = transport,
            processId = processId,
            id = id,
            now = now,
            anonymousId = { anonymousId },
        )
    }
    private val visitTracker = VisitTracker(id, elapsed, VISIT_TIMEOUT_MS)
    private val attemptTracker = SighAttemptTracker(id, elapsed)
    private val saveTracker = SaveTracker(id, elapsed)
    private val captureTracker = BreathCaptureTracker(id)
    private val mapVisitTracker = MapVisitTracker(id)
    private var screen = "splash"
    private var phase = "idle"
    private var usable =
        config.configured && stateStore.readable &&
            state.environment == config.environment && state.version == 1
    private val breathCoordinator =
        BreathCaptureCoordinator(
            captureTracker = captureTracker,
            attemptTracker = attemptTracker,
            attempt = { attempt },
            currentState = { state },
            usable = { usable },
            screen = { screen },
            phase = { phase },
            setPhase = { phase = it },
            elapsed = elapsed,
            id = id,
            change = { block -> change(block) },
            emit = { name, origin, fields, logicalKey -> emit(name, origin, fields, logicalKey) },
            observe = { observe() },
            updateContext = { updateContext() },
            flush = { flush() },
        )
    private val lifecycleCoordinator =
        MonitoringLifecycleCoordinator(
            visitTracker = visitTracker,
            saveTracker = saveTracker,
            now = now,
            currentState = { state },
            replaceState = { state = it },
            screen = { screen },
            phase = { phase },
            attempt = { attempt },
            activeSave = { activeSave },
            change = { block -> change(block) },
            emit = { name, origin, fields, logicalKey -> emit(name, origin, fields, logicalKey) },
            endRecord = { origin, outcome, reason, quality -> endRecord(origin, outcome, reason, quality) },
            observe = { observe() },
            updateContext = { updateContext() },
        )
    private val attemptCoordinator =
        SighAttemptCoordinator(
            visitTracker = visitTracker,
            attemptTracker = attemptTracker,
            usable = { usable },
            isForeground = { visitTracker.isForeground },
            screen = { screen },
            phase = { phase },
            setPhase = { phase = it },
            attempt = { attempt },
            currentState = { state },
            replaceState = { state = it },
            elapsed = elapsed,
            id = id,
            change = { block -> change(block) },
            emit = { name, origin, fields, logicalKey -> emit(name, origin, fields, logicalKey) },
            endRecord = { origin, outcome, reason, quality -> endRecord(origin, outcome, reason, quality) },
            resetForAttempt = {
                saveTracker.resetForAttempt()
                breathCoordinator.resetForAttempt()
            },
            clearAttempt = { clearAttempt() },
            observe = { observe() },
            updateContext = { updateContext() },
        )
    private val saveCoordinator =
        SaveCoordinator(
            saveTracker = saveTracker,
            usable = { usable },
            attempt = { attempt },
            activeSave = { activeSave },
            screen = { screen },
            phase = { phase },
            setPhase = { phase = it },
            currentState = { state },
            replaceState = { state = it },
            now = now,
            elapsed = elapsed,
            id = id,
            formatTime = ::iso,
            formatDay = ::day,
            change = { block -> change(block) },
            emit = { name, origin, fields, logicalKey -> emit(name, origin, fields, logicalKey) },
            endRecord = { origin, outcome, reason, quality -> endRecord(origin, outcome, reason, quality) },
            clearAttempt = { clearAttempt() },
            observe = { observe() },
            updateContext = { updateContext() },
        )
    private val mapVisitCoordinator =
        MapVisitCoordinator(
            visitTracker = visitTracker,
            mapVisitTracker = mapVisitTracker,
            saveTracker = saveTracker,
            elapsed = elapsed,
            change = { block -> change(block) },
            emit = { name, origin, fields, logicalKey -> emit(name, origin, fields, logicalKey) },
            updateContext = { updateContext() },
        )

    val anonymousId: String get() = state.anonymousId
    val recordingAvailable: Boolean get() = usable
    val droppedEventCount: Long get() = state.droppedEvents

    private val attempt: MonitoringSnapshot? get() = attemptTracker.snapshot
    private val activeSave: MonitoringSnapshot? get() = saveTracker.activeSave
    private val activeMapVisit: MonitoringSnapshot? get() = mapVisitTracker.activeMapVisit
    private val activeSelection: MonitoringSnapshot? get() = mapVisitTracker.activeSelection

    init {
        // Persist the installation before any event can be sent. Corrupt/unreadable storage is
        // not overwritten and is never reclassified as a new installation.
        if (usable) usable = persist()
    }

    fun setScreen(value: String) {
        screen = value.takeIf { it in SCREENS } ?: "unknown"
    }

    fun foreground() {
        if (!usable) return
        lifecycleCoordinator.foreground(::flush)
    }

    fun background() {
        if (!usable) return
        lifecycleCoordinator.background(::flush)
    }

    /** Flushes events accepted by the analytics SDK without changing monitoring state. */
    fun flush() {
        if (!usable) return
        drain()
        runCatching { transport.flush() }
    }

    fun tick() {
        if (!usable) return
        lifecycleCoordinator.tick(::drain)
    }

    fun beginAttempt(guideMode: Boolean): Boolean = attemptCoordinator.beginAttempt(guideMode)

    fun startFailed(reason: String) = attemptCoordinator.startFailed(reason)

    fun memoEditing() = attemptCoordinator.memoEditing()

    fun memoShown() = attemptCoordinator.memoShown()

    fun memoValidationFailed() = attemptCoordinator.memoValidationFailed()

    fun memoCompleted(memoPresent: Boolean) = attemptCoordinator.memoCompleted(memoPresent)

    fun memoSkipped() = attemptCoordinator.memoSkipped()

    fun microphonePermissionResult(granted: Boolean) = attemptCoordinator.microphonePermissionResult(granted)

    fun beginCapture(): MonitoringSnapshot? = breathCoordinator.beginCapture()

    fun microphoneReady() = breathCoordinator.microphoneReady()

    fun baseSizeChanged() = breathCoordinator.baseSizeChanged()

    fun soundFirstDetected(
        strength: Float,
        activeThreshold: Float,
    ) = breathCoordinator.soundFirstDetected(strength, activeThreshold)

    fun breathSample(
        active: Boolean,
        sampleElapsedMs: Long,
        growth: Float,
    ) = breathCoordinator.breathSample(active, sampleElapsedMs, growth)

    fun releaseReady(
        growth: Float,
        minimumReleaseProgress: Float,
    ) = breathCoordinator.releaseReady(growth, minimumReleaseProgress)

    fun controlTapped(
        controlPhase: String,
        growth: Float,
        inputActive: Boolean,
    ) = breathCoordinator.controlTapped(controlPhase, growth, inputActive)

    fun swipeAttempted(
        upwardDistanceDp: Float,
        upwardVelocityDpPerSecond: Float,
        growth: Float,
        success: Boolean,
        reason: String,
    ) = breathCoordinator.swipeAttempted(
        upwardDistanceDp,
        upwardVelocityDpPerSecond,
        growth,
        success,
        reason,
    )

    fun gestureCancelled(reason: String) = breathCoordinator.gestureCancelled(reason)

    fun releaseAnimationFinished() = breathCoordinator.releaseAnimationFinished()

    fun endCapture(
        finalGrowth: Float,
        stopReason: String,
        interrupted: Boolean,
    ) = breathCoordinator.endCapture(finalGrowth, stopReason, interrupted)

    fun microphoneFailed(error: String) = breathCoordinator.microphoneFailed(error)

    fun beginSave(): MonitoringSnapshot? = saveCoordinator.beginSave()

    fun saveResult(
        origin: MonitoringSnapshot?,
        success: Boolean,
        durationMs: Long,
        errorCode: String? = null,
    ) = saveCoordinator.saveResult(origin, success, durationMs, errorCode)

    fun saveWaitFinished(
        origin: MonitoringSnapshot?,
        waitMs: Long,
    ) = saveCoordinator.saveWaitFinished(origin, waitMs)

    fun saveUiResultShown(outcome: String) = saveCoordinator.saveUiResultShown(outcome)

    fun apiRequestFinished(
        routeTemplate: String,
        method: String,
        durationMs: Long,
        success: Boolean,
        statusCode: Int? = null,
        errorCode: String? = null,
        correlationId: String? = null,
    ) = saveCoordinator.apiRequestFinished(
        routeTemplate = routeTemplate,
        method = method,
        durationMs = durationMs,
        success = success,
        statusCode = statusCode,
        errorCode = errorCode,
        correlationId = correlationId,
    )

    fun mapVisitStarted(entryReason: String) = mapVisitCoordinator.mapVisitStarted(entryReason)

    fun mapStarsVisible(visibleStarCount: Int) = mapVisitCoordinator.mapStarsVisible(visibleStarCount)

    fun starSelected(entrySource: String) = mapVisitCoordinator.starSelected(entrySource)

    fun starDetailShown() = mapVisitCoordinator.starDetailShown()

    fun starDetailFailed(
        reason: String,
        errorCode: String? = null,
    ) = mapVisitCoordinator.starDetailFailed(reason, errorCode)

    fun savedStarVisible() = mapVisitCoordinator.savedStarVisible()

    fun mapVisitEnded(
        reason: String,
        quality: String = "observed",
    ) = mapVisitCoordinator.mapVisitEnded(reason, quality)

    fun endAttempt(
        outcome: String = "cancelled",
        reason: String = "user_cancel",
    ) = change {
        if (captureTracker.activeCapture() != null) {
            breathCoordinator.finishForAttempt(
                finalGrowth = captureTracker.lastGrowth(),
                stopReason = if (reason == "user_cancel") "user_cancel" else reason,
                interrupted = reason != "user_cancel",
            )
        }
        attempt?.let { endRecord(it.copy(state = phase), outcome, reason) }
        clearAttempt()
    }

    fun report(
        error: Throwable,
        origin: MonitoringSnapshot? = snapshot(),
    ) {
        if (usable) runCatching { transport.report(error, origin) }
    }

    fun snapshot(): MonitoringSnapshot? =
        activeSave ?: captureTracker.activeCapture() ?: attempt ?: activeSelection ?: activeMapVisit
            ?: visitTracker.currentVisitId?.let { visitSnapshot() }

    private fun endRecord(
        origin: MonitoringSnapshot,
        outcome: String,
        reason: String,
        quality: String = "observed",
    ) {
        emit(
            MonitoringEventNames.SIGH_ATTEMPT_ENDED,
            origin,
            mapOf(
                "outcome" to outcome,
                "reason" to reason,
                "last_stage" to origin.state,
                "end_time_quality" to quality,
            ),
            "end:${origin.sighAttemptId}",
        )
    }

    private fun clearAttempt() {
        attemptTracker.clear()
        saveTracker.clearActive()
        breathCoordinator.clear()
        state = state.copy(attempt = null, savePending = false)
        phase = "idle"
        updateContext()
    }

    private fun updateContext() {
        runCatching { transport.context(if (visitTracker.isForeground) snapshot() else null) }
    }

    private fun visitSnapshot() = MonitoringSnapshot(visitTracker.currentVisitId!!, screen = screen, state = phase)

    private fun observe() {
        state =
            state.copy(
                lastObserved = now(),
                lastScreen = screen,
                lastStage = phase,
                attempt = attemptTracker.snapshot?.copy(state = phase),
            )
    }

    private fun emit(
        name: String,
        origin: MonitoringSnapshot,
        fields: Map<String, Any>,
        logicalKey: String? = null,
    ) = recorder.record(name, origin, fields, logicalKey)

    private inline fun change(block: () -> Unit) {
        if (!usable) return
        block()
        if (!persist()) {
            usable = false
            return
        }
        drain()
    }

    private fun persist(): Boolean = recorder.persist()

    private fun drain() {
        if (!usable) return
        if (!recorder.drain()) usable = false
    }

    companion object {
        const val VISIT_TIMEOUT_MS = 30 * 60 * 1000L
        private val SCREENS = setOf("splash", "map", "settings", "legaldocument", "onboarding", "unknown")
    }
}

private fun monotonicClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

private fun iso(time: Long) = Instant.fromEpochMilliseconds(time).toString()

private fun day(time: Long) = iso(time + 9 * 60 * 60 * 1000).take(10)
