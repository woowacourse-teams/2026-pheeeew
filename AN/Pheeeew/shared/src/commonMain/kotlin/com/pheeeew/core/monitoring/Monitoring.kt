package com.pheeeew.core.monitoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** UI-thread confined. Platform lifecycle callbacks and ViewModel actions use the main thread. */
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
    private val loaded = runCatching { store.read() }
    private val decoded = runCatching { loaded.getOrNull()?.let { json.decodeFromString<MonitoringState>(it) } }
    private var state =
        decoded.getOrNull() ?: MonitoringState(
            anonymousId = id(),
            knownNew = newInstallation && loaded.isSuccess && loaded.getOrNull() == null,
            environment = config.environment,
        )
    private val processId = id()
    private var sequence = 0L
    private var foreground = false
    private var backgroundElapsed: Long? = null
    private var visit: String? = null
    private var attempt: MonitoringSnapshot? = null
    private var activeSave: MonitoringSnapshot? = null
    private var activeCapture: MonitoringSnapshot? = null
    private var saveIndex = 0
    private var captureIndex = 0
    private var screen = "splash"
    private var phase = "idle"
    private var usable =
        config.configured && loaded.isSuccess && decoded.isSuccess &&
            state.environment == config.environment && state.version == 1

    val anonymousId: String get() = state.anonymousId
    val recordingAvailable: Boolean get() = usable
    val droppedEventCount: Long get() = state.droppedEvents

    init {
        // Persist the installation before any event can be sent. Corrupt/unreadable storage is
        // not overwritten and is never reclassified as a new installation.
        if (usable) usable = persist()
    }

    fun setScreen(value: String) {
        screen = value.takeIf { it in SCREENS } ?: "unknown"
    }

    fun foreground() =
        change {
            if (foreground) return@change
            val expired = backgroundElapsed?.let { elapsed() - it >= VISIT_TIMEOUT_MS } == true
            if (visit == null && state.visitId != null) {
                val oldVisit = MonitoringSnapshot(state.visitId!!, screen = state.lastScreen, state = state.lastStage)
                state.attempt?.let {
                    endRecord(
                        it,
                        if (state.savePending) "unknown" else "interrupted",
                        "process_interrupted",
                        "inferred",
                    )
                }
                emit(
                    "app_visit_ended",
                    oldVisit,
                    mapOf(
                        "reason" to "process_interrupted",
                        "end_time_quality" to "inferred",
                        "last_observed_at" to iso(state.lastObserved),
                    ),
                    "visit_end:${oldVisit.sessionId}",
                )
            } else if (expired) {
                attempt?.let {
                    endRecord(
                        it,
                        if (activeSave !=
                            null
                        ) {
                            "unknown"
                        } else {
                            "interrupted"
                        },
                        "background",
                        "inferred",
                    )
                }
                visit?.let {
                    emit(
                        "app_visit_ended",
                        MonitoringSnapshot(it, screen = screen, state = phase),
                        mapOf(
                            "reason" to "background",
                            "end_time_quality" to "inferred",
                            "last_observed_at" to iso(state.lastObserved),
                        ),
                        "visit_end:$it",
                    )
                }
                visit = null
                // Retain the original attempt for a still-present memo/save UI. Later events carry
                // its original visit; a new foreground does not fabricate a new sigh start.
            }
            foreground = true
            backgroundElapsed = null
            if (visit == null) {
                visit = id()
                val first = state.knownNew && state.firstOpened == null
                state = state.copy(visitId = visit, firstVisitId = if (first) visit else state.firstVisitId)
                val origin = visitSnapshot()
                if (first) {
                    state = state.copy(firstOpened = now())
                    emit("app_first_opened", origin, mapOf("first_opened_at" to iso(state.firstOpened!!)), "first_open")
                }
                emit(
                    "app_visit_started",
                    origin,
                    mapOf(
                        "entry_reason" to if (expired) "foreground_return" else "launch",
                        "first_visit" to first,
                    ),
                    "visit_start:$visit",
                )
            }
            activeDay()
            observe()
            updateContext()
        }

    fun background() =
        change {
            if (!foreground) return@change
            val fields = attempt?.sighAttemptId?.let { mapOf("active_sigh_attempt_id" to it) }.orEmpty()
            emit("app_backgrounded", visitSnapshot(), fields)
            observe()
            foreground = false
            backgroundElapsed = elapsed()
            updateContext()
        }

    fun tick() {
        if (!usable) return
        if (foreground) {
            val day = day(now())
            // Avoid writing the storage every frame/second. Heartbeat only bounds inferred ends.
            if (day !in state.activeDays || now() - state.lastObserved >= HEARTBEAT_MS) {
                change {
                    activeDay()
                    observe()
                }
                return
            }
        }
        drain()
    }

    fun beginAttempt(guideMode: Boolean): Boolean {
        if (!usable || !foreground || attempt != null) return false
        change {
            phase = "starting"
            saveIndex = 0
            captureIndex = 0
            attempt = MonitoringSnapshot(visit!!, id(), screen = screen, state = phase)
            state = state.copy(attempt = attempt, savePending = false)
            emit(
                "sigh_started",
                attempt!!,
                mapOf("entry_point" to "main_button", "guide_mode" to guideMode),
                "start:${attempt!!.sighAttemptId}",
            )
            updateContext()
        }
        return true
    }

    fun startFailed(reason: String) =
        change {
            attempt?.let {
                emit("sigh_start_failed", it, mapOf("reason" to reason), "start_failure:${it.sighAttemptId}")
                endRecord(it, "start_failed", reason)
            }
            clearAttempt()
        }

    fun memoEditing() =
        change {
            phase = "editing_memo"
            observe()
        }

    fun memoFinished() =
        change {
            phase = "awaiting_breath"
            observe()
        }

    /** Allocates a capture per actual input start; callers may wire detailed audio events later. */
    fun beginCapture(): MonitoringSnapshot? {
        val origin = attempt ?: return null
        if (!usable) return null
        change {
            captureIndex += 1
            activeCapture =
                origin.copy(captureId = id(), captureIndex = captureIndex, screen = screen, state = "listening")
            phase = "listening"
            observe()
        }
        return activeCapture
    }

    fun beginSave(): MonitoringSnapshot? {
        if (!usable) return null
        val origin = attempt ?: return null
        activeSave?.let { return it }
        change {
            phase = "submitting"
            saveIndex += 1
            activeSave = origin.copy(saveAttemptId = id(), screen = screen, state = phase)
            state = state.copy(savePending = true)
            observe()
            emit(
                "save_started",
                activeSave!!,
                mapOf(
                    "save_index" to saveIndex,
                    "trigger" to
                        if (saveIndex ==
                            1
                        ) {
                            "release"
                        } else {
                            "user_retry"
                        },
                    "min_display_duration_ms" to 2000,
                ),
                "save_start:${activeSave!!.saveAttemptId}",
            )
            updateContext()
        }
        return activeSave
    }

    fun saveResult(
        origin: MonitoringSnapshot?,
        success: Boolean,
        durationMs: Long,
        errorCode: String? = null,
    ) = change {
        if (origin?.saveAttemptId == null || origin.sighAttemptId == null) return@change
        val key = "save_result:${origin.saveAttemptId}"
        if (key in state.logicalKeys) return@change
        val fields =
            buildMap<String, Any> {
                put("outcome", if (success) "success" else "failure")
                if (durationMs >= 0) put("save_operation_duration_ms", durationMs)
                put("creation_kind", "unknown")
                if (!success) put("reason", "unknown")
                errorCode?.let { put("error_code", it) }
            }
        emit("save_result", origin, fields, key)
        if (success) {
            if (state.knownNew && state.firstSaved == null) {
                state = state.copy(firstSaved = now())
                emit(
                    "first_sigh_saved",
                    origin,
                    mapOf(
                        "first_saved_at" to iso(state.firstSaved!!),
                        "cohort_date" to day(state.firstSaved!!),
                        "first_save_history" to "known",
                    ),
                    "first_save",
                )
            }
            // If this attempt already ended as unknown, save_result is authoritative. Do not
            // emit a second terminal event; the analytics query joins the later result.
            endRecord(origin.copy(state = "save_result"), "saved", "none")
            if (attempt?.sighAttemptId == origin.sighAttemptId) clearAttempt()
        } else if (activeSave?.saveAttemptId == origin.saveAttemptId) {
            activeSave = null
            state = state.copy(savePending = false)
            phase = "save_error"
            updateContext()
        }
    }

    fun endAttempt(
        outcome: String = "cancelled",
        reason: String = "user_cancel",
    ) = change {
        attempt?.let { endRecord(it.copy(state = phase), outcome, reason) }
        clearAttempt()
    }

    fun report(
        error: Throwable,
        origin: MonitoringSnapshot? = snapshot(),
    ) {
        if (usable) runCatching { transport.report(error, origin) }
    }

    fun snapshot(): MonitoringSnapshot? = activeSave ?: activeCapture ?: attempt ?: visit?.let { visitSnapshot() }

    private fun endRecord(
        origin: MonitoringSnapshot,
        outcome: String,
        reason: String,
        quality: String = "observed",
    ) {
        emit(
            "sigh_attempt_ended",
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
        attempt = null
        activeSave = null
        activeCapture = null
        state = state.copy(attempt = null, savePending = false)
        phase = "idle"
        updateContext()
    }

    private fun updateContext() {
        runCatching { transport.context(if (foreground) snapshot() else null) }
    }

    private fun visitSnapshot() = MonitoringSnapshot(visit!!, screen = screen, state = phase)

    private fun observe() {
        state =
            state.copy(
                lastObserved = now(),
                lastScreen = screen,
                lastStage = phase,
                attempt = attempt?.copy(state = phase),
            )
    }

    private fun activeDay() {
        val date = day(now())
        if (date !in state.activeDays) {
            state = state.copy(activeDays = (state.activeDays + date).takeLast(MAX_LOGICAL_KEYS))
            emit("app_active_day", visitSnapshot(), mapOf("activity_date" to date), "day:$date")
        }
    }

    private fun emit(
        name: String,
        origin: MonitoringSnapshot,
        fields: Map<String, Any>,
        logicalKey: String? = null,
    ) {
        if (logicalKey != null && logicalKey in state.logicalKeys) return
        val time = now()
        val properties =
            fields + origin.properties() +
                mapOf(
                    "event_id" to id(),
                    "event_schema_version" to 1,
                    "measurement_config_version" to "v1",
                    "anonymous_id" to anonymousId,
                    "occurred_at" to iso(time),
                    "process_id" to processId,
                    "event_sequence" to ++sequence,
                    "screen" to origin.screen,
                    "state" to origin.state,
                    "environment" to config.environment,
                    "platform" to config.platform,
                    "app_version" to config.appVersion,
                    "build_number" to config.buildNumber,
                    "os_version" to config.osVersion,
                    "device_class" to config.deviceClass,
                    "install_class" to
                        if (!state.knownNew) {
                            "legacy_unknown"
                        } else if (origin.sessionId ==
                            state.firstVisitId
                        ) {
                            "new"
                        } else {
                            "returning"
                        },
                )
        val event = MonitoringEvent(name, time, JsonObject(properties.mapValues { (_, value) -> primitive(value) }))
        // Keep state changes and the pending event in the same durable record before handoff.
        val pending = (state.pending + event).toMutableList()
        if (pending.size > MAX_PENDING) {
            // Preserve installation-first facts while dropping the oldest ordinary event.
            val discard = pending.indexOfFirst { it.name != "app_first_opened" && it.name != "first_sigh_saved" }
            pending.removeAt(discard)
        }
        state =
            state.copy(
                pending = pending,
                droppedEvents = state.droppedEvents + if (state.pending.size >= MAX_PENDING) 1 else 0,
                logicalKeys =
                    if (logicalKey ==
                        null
                    ) {
                        state.logicalKeys
                    } else {
                        (state.logicalKeys + logicalKey).takeLast(MAX_LOGICAL_KEYS)
                    },
            )
    }

    private inline fun change(block: () -> Unit) {
        if (!usable) return
        block()
        if (!persist()) {
            usable = false
            return
        }
        drain()
    }

    private fun persist(): Boolean = runCatching { store.write(json.encodeToString(state)) }.isSuccess

    private fun drain() {
        if (!usable || state.pending.isEmpty()) return
        val accepted = state.pending.takeWhile { runCatching { transport.track(it) }.getOrDefault(false) }
        if (accepted.isEmpty()) return
        state = state.copy(pending = state.pending.drop(accepted.size))
        // Failure to acknowledge durably can replay the same IDs after restart, never new IDs.
        if (!persist()) usable = false
    }

    companion object {
        const val VISIT_TIMEOUT_MS = 30 * 60 * 1000L
        private const val HEARTBEAT_MS = 30_000L
        private const val MAX_PENDING = 1000
        private const val MAX_LOGICAL_KEYS = 4096
        private val SCREENS = setOf("splash", "map", "settings", "legaldocument", "onboarding", "unknown")
    }
}

private fun monotonicClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

private fun iso(time: Long) = Instant.fromEpochMilliseconds(time).toString()

private fun day(time: Long) = iso(time + 9 * 60 * 60 * 1000).take(10)

private fun primitive(value: Any): JsonPrimitive =
    when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> error("Unsupported monitoring property")
    }
