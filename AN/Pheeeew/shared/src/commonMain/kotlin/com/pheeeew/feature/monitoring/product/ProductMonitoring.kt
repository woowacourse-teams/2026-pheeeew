package com.pheeeew.feature.monitoring.product

import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventContext
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.ValueType
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

/** App feature events. Only fixed labels and opaque identifiers belong in these properties. */
object ProductEvents {
    private val names =
        """
        emotion_record_closed emotion_record_location_changed emotion_record_location_confirmed
        emotion_record_group_options_finished emotion_record_group_changed
        emotion_item_impression emotion_content_viewed nearby_filter_applied
        emotion_reaction_started emotion_reaction_finished emotion_block_finished
        emotion_report_finished emotion_delete_finished
        voice_recording_finished voice_preview_finished emotion_audio_load_finished
        emotion_audio_playback_started emotion_audio_playback_finished
        group_list_load_finished group_create_form_viewed group_create_validation_failed
        group_create_confirmation_resolved group_join_started group_lookup_finished
        group_join_submit_started group_join_finished group_flow_closed group_detail_load_finished
        group_emotion_press_started group_emotion_press_finished group_emotion_feedback_viewed
        group_invite_copy_finished group_leave_finished ranking_viewed ranking_load_finished ranking_week_changed
        device_session_prepare_finished location_acquire_finished map_load_finished map_location_requested
        onboarding_step_viewed onboarding_finished operation_reconciled
        settings_action_selected settings_external_open_finished
        """.trimIndent().split(Regex("\\s+"))
    private val properties =
        listOf(
            "flow_id",
            "selector_view_id",
            "view_id",
            "detail_view_id",
            "impression_id",
            "entry_key",
            "entry_source",
            "group_key",
            "group_selection",
            "action",
            "outcome",
            "step",
            "field",
            "rule",
            "operation_kind",
            "playback_id",
            "recording_id",
            "group_operation_key",
        ).associateWith { PropertyRule(ValueType.TEXT, maxLength = 128) } +
            listOf(
                "duration_ms",
                "played_ms",
                "media_duration_ms",
                "item_count",
                "weeks_ago",
                "from_weeks_ago",
                "page_index",
            ).associateWith { PropertyRule(ValueType.INTEGER, minimum = 0.0) } +
            mapOf(
                "completion_ratio" to PropertyRule(ValueType.DECIMAL, minimum = 0.0),
                "is_own" to PropertyRule(ValueType.BOOLEAN),
            )
    val definitions =
        names.map { name ->
            val rules =
                if (name.endsWith("_finished") || name == "operation_reconciled") {
                    properties + ("outcome" to properties.getValue("outcome").copy(required = true))
                } else {
                    properties
                }
            EventDefinition(name, properties = rules)
        }
    private val byName = definitions.associateBy { it.name }

    fun definition(name: String) = byName.getValue(name)
}

class ProductMonitoring(
    private val monitoring: Monitoring,
    private val screen: String,
    private val defaults: Map<String, EventValue> = emptyMap(),
) {
    fun screenShown() {
        val context = context() ?: return
        runCatching {
            monitoring.track(
                DefinedEvent(
                    com.pheeeew.feature.screens.map.monitoring.RecordFunnelEvents.screen,
                    mapOf(
                        "view_id" to
                            EventValue.Text(
                                kotlin.uuid.Uuid
                                    .random()
                                    .toString(),
                            ),
                    ),
                ),
                context,
            )
        }
    }

    fun context(parent: EventContext? = null) = runCatching { monitoring.context(screen, parent) }.getOrNull()

    fun emit(
        name: String,
        fields: Map<String, EventValue> = emptyMap(),
        context: EventContext? = context(),
    ) {
        context ?: return
        runCatching { monitoring.track(DefinedEvent(ProductEvents.definition(name), defaults + fields), context) }
    }

    fun operation(
        finished: String,
        fields: Map<String, EventValue> = emptyMap(),
        started: String? = null,
        parent: EventContext? = null,
    ): ProductOperation {
        val context = context(parent)
        if (started != null) emit(started, fields, context)
        return ProductOperation(this, finished, fields, context)
    }
}

class ProductOperation internal constructor(
    private val monitoring: ProductMonitoring,
    private val name: String,
    private val fields: Map<String, EventValue>,
    private val context: EventContext?,
) {
    private val start = TimeSource.Monotonic.markNow()
    private var finished = false

    fun finish(
        outcome: String,
        extra: Map<String, EventValue> = emptyMap(),
    ) {
        if (finished) return
        finished = true
        monitoring.emit(
            name,
            fields + extra +
                mapOf(
                    "outcome" to EventValue.Text(outcome),
                    "duration_ms" to EventValue.Integer(start.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)),
                ),
            context,
        )
    }

    suspend fun <T> observe(
        outcome: (T) -> String,
        block: suspend () -> T,
    ): T =
        try {
            block().also { finish(outcome(it)) }
        } catch (
            timeout: kotlinx.coroutines.TimeoutCancellationException,
        ) {
            finish("unknown")
            throw timeout
        } catch (
            cancelled: CancellationException,
        ) {
            finish("cancelled")
            throw cancelled
        } catch (error: Exception) {
            finish("unknown")
            throw error
        }
}

fun labels(vararg values: Pair<String, String>): Map<String, EventValue> =
    values.associate {
        it.first to
            EventValue.Text(it.second)
    }

/** Only use with sealed result types, never payloads, exception messages or user values. */
fun resultLabel(result: Any): String =
    when (val type = result::class.simpleName) {
        "Success", "Loaded", "Created", "Joined", "Pressed", "Left", "Copied" -> "success"
        "OutcomeUnknown", "Unavailable", "NetworkUnavailable" -> "unknown"
        else -> type?.replace(Regex("([a-z])([A-Z])"), "$1_$2")?.lowercase() ?: "unknown"
    }
