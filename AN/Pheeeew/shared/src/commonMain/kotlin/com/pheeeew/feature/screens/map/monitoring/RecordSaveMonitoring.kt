package com.pheeeew.feature.screens.map.monitoring

import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventContext
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.ValueType
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.AudioUploadObservation
import kotlin.time.TimeSource

object RecordSaveEvents {
    private fun text(vararg allowed: String) =
        PropertyRule(
            ValueType.TEXT,
            required = true,
            allowed = allowed.toSet().takeIf { it.isNotEmpty() },
        )

    private val keys =
        mapOf(
            "selector_view_id" to text(),
            "flow_id" to text(),
            "registration_key" to text(),
            "input_mode" to text("text", "voice", "none"),
            "group_selection" to text("none", "group"),
        )
    private val duration = PropertyRule(ValueType.INTEGER, required = true, minimum = 0.0)
    private val result =
        keys +
            mapOf(
                "outcome" to text("success", "failed", "unknown", "cancelled"),
                "entry_key" to PropertyRule(ValueType.TEXT),
            )
    val upload =
        EventDefinition(
            "emotion_audio_upload_finished",
            properties =
                keys +
                    mapOf(
                        "outcome" to text("success", "failed", "unknown", "cancelled"),
                        "failure_stage" to text("none", "file", "url_request", "upload"),
                        "cache_reused" to PropertyRule(ValueType.BOOLEAN, required = true),
                        "duration_ms" to duration,
                    ),
        )
    val finished = EventDefinition("emotion_record_submit_finished", properties = result + ("duration_ms" to duration))
    val viewed =
        EventDefinition(
            "emotion_record_result_viewed",
            properties =
                result + ("feedback_elapsed_ms" to duration),
        )
    val definitions = listOf(upload, finished, viewed)
}

/** A snapshot of one submission, independent of subsequent flows and retries. */
class RecordSubmission internal constructor(
    private val monitoring: Monitoring,
    private val context: EventContext,
    fields: Map<String, String>,
) {
    private val fields = fields.mapValues { EventValue.Text(it.value) }
    private val started = TimeSource.Monotonic.markNow()
    private var finished = false
    private var uploadRecorded = false

    fun audioFinished(observation: AudioUploadObservation) {
        if (uploadRecorded || finished) return
        uploadRecorded = true
        emit(
            RecordSaveEvents.upload,
            fields +
                mapOf(
                    "outcome" to EventValue.Text(observation.outcome.name.lowercase()),
                    "failure_stage" to EventValue.Text(observation.failureStage.name.lowercase()),
                    "cache_reused" to EventValue.Flag(observation.cacheReused),
                    "duration_ms" to EventValue.Integer(observation.durationMs.coerceAtLeast(0)),
                ),
        )
    }

    fun finish(result: EmotionRegistrationResult): RecordResultReceipt? {
        val entry = (result as? EmotionRegistrationResult.Success)?.id?.takeIf { it > 0 }
        val outcome =
            when (result) {
                is EmotionRegistrationResult.Success -> if (entry != null) "success" else "unknown"

                EmotionRegistrationResult.Rejected,
                EmotionRegistrationResult.AudioUnavailable,
                EmotionRegistrationResult.AudioUploadFailed,
                -> "failed"

                EmotionRegistrationResult.Unavailable -> "unknown"
            }
        return complete(outcome, entry)
    }

    fun cancelled() {
        complete("cancelled", null)
    }

    private fun complete(
        outcome: String,
        entry: Long?,
    ): RecordResultReceipt? {
        if (finished) return null
        finished = true
        val resultFields =
            fields + mapOf("outcome" to EventValue.Text(outcome)) +
                entry?.let { mapOf("entry_key" to EventValue.Text(it.toString())) }.orEmpty()
        emit(
            RecordSaveEvents.finished,
            resultFields + ("duration_ms" to EventValue.Integer(started.elapsedNow().inWholeMilliseconds)),
        )
        return RecordResultReceipt(monitoring, context, resultFields, started)
    }

    private fun emit(
        definition: EventDefinition,
        values: Map<String, EventValue>,
    ) {
        runCatching { monitoring.track(DefinedEvent(definition, values), context) }
    }
}

/** Attached only to a registration notice; unrelated group notices have no receipt. */
class RecordResultReceipt internal constructor(
    private val monitoring: Monitoring,
    private val context: EventContext,
    private val fields: Map<String, EventValue>,
    private val started: TimeSource.Monotonic.ValueTimeMark,
) {
    private var shown = false

    fun shown() {
        if (shown) return
        shown = true
        runCatching {
            monitoring.track(
                DefinedEvent(
                    RecordSaveEvents.viewed,
                    fields +
                        ("feedback_elapsed_ms" to EventValue.Integer(started.elapsedNow().inWholeMilliseconds)),
                ),
                context,
            )
        }
    }
}
