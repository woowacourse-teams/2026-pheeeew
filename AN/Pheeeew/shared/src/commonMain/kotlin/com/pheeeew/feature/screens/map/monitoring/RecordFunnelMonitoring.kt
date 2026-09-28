package com.pheeeew.feature.screens.map.monitoring

import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventContext
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.ValueType
import kotlin.uuid.Uuid

object RecordFunnelEvents {
    private fun text(vararg values: String) =
        PropertyRule(
            ValueType.TEXT,
            required = true,
            allowed = values.toSet().takeIf { it.isNotEmpty() },
        )

    private val selector = mapOf("selector_view_id" to text())
    private val flow = selector + ("flow_id" to text())
    val screen = EventDefinition("screen_viewed", properties = mapOf("view_id" to text()))
    val selectorViewed = EventDefinition("emotion_selector_viewed", properties = selector)
    val selected = EventDefinition("emotion_record_emotion_selected", properties = selector)
    val permission =
        EventDefinition(
            "permission_request_finished",
            properties =
                mapOf("selector_view_id" to PropertyRule(ValueType.TEXT), "flow_id" to PropertyRule(ValueType.TEXT)) +
                    mapOf(
                        "permission" to text("location", "microphone"),
                        "outcome" to text("granted", "denied", "services_disabled", "unknown", "cancelled"),
                    ),
        )
    val started = EventDefinition("emotion_record_started", properties = flow)
    val step =
        EventDefinition(
            "emotion_record_step_viewed",
            properties =
                flow +
                    mapOf(
                        "step" to text("input", "location", "group"),
                        "step_view_id" to text(),
                    ),
        )
    val inputStarted =
        EventDefinition(
            "emotion_record_input_started",
            properties =
                flow + ("input_mode" to text("text", "voice")),
        )
    val inputFinished =
        EventDefinition(
            "emotion_record_input_finished",
            properties =
                flow +
                    mapOf(
                        "input_mode" to text("text", "voice", "none"),
                        "action" to text("next", "skip"),
                    ),
        )
    val submit =
        EventDefinition(
            "emotion_record_submit_started",
            properties =
                flow +
                    mapOf(
                        "registration_key" to text(),
                        "input_mode" to text("text", "voice", "none"),
                        "group_selection" to text("none", "group"),
                    ),
        )
    val definitions =
        listOf(screen, selectorViewed, selected, permission, started, step, inputStarted, inputFinished, submit)
}

/** Owned by the recording ViewModel; UI callbacks report visibility, not recomposition. */
class RecordFunnelMonitoring(
    private val monitoring: Monitoring,
) {
    private var screenVisible = false
    private var selectorId: String? = null
    private var selectorContext: EventContext? = null
    private var selectionContext: EventContext? = null
    private var flowSelectorId: String? = null
    private var flowId: String? = null
    private var flowContext: EventContext? = null
    private var visibleStep: String? = null
    private val inputModes = mutableSetOf<String>()
    var submissionContext: EventContext? = null
        private set

    private fun context(parent: EventContext? = null) = runCatching { monitoring.context("map", parent) }.getOrNull()

    private fun emit(
        event: EventDefinition,
        context: EventContext?,
        fields: Map<String, String>,
    ) {
        context ?: return
        runCatching { monitoring.track(DefinedEvent(event, fields.mapValues { EventValue.Text(it.value) }), context) }
    }

    private fun flowFields(): Map<String, String>? {
        val flow = flowId ?: return null
        val selector = flowSelectorId ?: return null
        return mapOf("flow_id" to flow, "selector_view_id" to selector)
    }

    fun screenShown() {
        if (screenVisible) return
        screenVisible = true
        emit(RecordFunnelEvents.screen, context(), mapOf("view_id" to Uuid.random().toString()))
    }

    fun screenHidden() {
        screenVisible = false
        selectorVisibility(false)
        visibleStep = null
    }

    fun selectorVisibility(visible: Boolean) {
        if (!visible) {
            selectorId = null
            return
        }
        if (selectorId != null) return
        selectorId = Uuid.random().toString()
        selectorContext = context()
        emit(RecordFunnelEvents.selectorViewed, selectorContext, mapOf("selector_view_id" to selectorId!!))
    }

    fun selected(): String? {
        val id = selectorId ?: return null
        selectionContext = selectorContext
        emit(RecordFunnelEvents.selected, selectionContext, mapOf("selector_view_id" to id))
        return id
    }

    fun permissionFinished(
        selector: String?,
        outcome: String,
    ) {
        selector ?: return
        emit(
            RecordFunnelEvents.permission,
            selectionContext,
            mapOf(
                "selector_view_id" to selector,
                "permission" to "location",
                "outcome" to outcome,
            ),
        )
    }

    fun microphonePermission(outcome: String) {
        emit(
            RecordFunnelEvents.permission,
            flowContext,
            (flowFields() ?: emptyMap()) + mapOf("permission" to "microphone", "outcome" to outcome),
        )
    }

    fun start(selector: String?) {
        clearFlow()
        selector ?: return
        flowSelectorId = selector
        flowId = Uuid.random().toString()
        flowContext = context(selectionContext)
        emit(RecordFunnelEvents.started, flowContext, flowFields()!!)
    }

    fun stepShown(step: String?) {
        if (visibleStep == step) return
        visibleStep = step
        step ?: return
        val fields = flowFields() ?: return
        emit(
            RecordFunnelEvents.step,
            flowContext,
            fields + mapOf("step" to step, "step_view_id" to Uuid.random().toString()),
        )
    }

    fun inputStarted(mode: String) {
        val fields = flowFields() ?: return
        if (!inputModes.add(mode)) return
        emit(RecordFunnelEvents.inputStarted, flowContext, fields + ("input_mode" to mode))
    }

    fun inputFinished(
        mode: String,
        skipped: Boolean,
    ) {
        val fields = flowFields() ?: return
        emit(
            RecordFunnelEvents.inputFinished,
            flowContext,
            fields + mapOf("input_mode" to mode, "action" to if (skipped) "skip" else "next"),
        )
    }

    fun submit(
        registrationKey: String,
        mode: String,
        hasGroup: Boolean,
    ): RecordSubmission? {
        val fields = flowFields() ?: return null
        submissionContext = context(flowContext)
        val context = submissionContext ?: return null
        val submissionFields =
            fields +
                mapOf(
                    "registration_key" to registrationKey,
                    "input_mode" to mode,
                    "group_selection" to if (hasGroup) "group" else "none",
                )
        val submission = RecordSubmission(monitoring, context, submissionFields)
        emit(RecordFunnelEvents.submit, context, submissionFields)
        return submission
    }

    fun observe(
        name: String,
        fields: Map<String, com.pheeeew.core.monitoring.EventValue> = emptyMap(),
    ): com.pheeeew.feature.monitoring.product.ProductOperation {
        val values = (flowFields() ?: emptyMap()).mapValues { EventValue.Text(it.value) } + fields
        return com.pheeeew.feature.monitoring.product
            .ProductMonitoring(
                monitoring,
                "map",
            ).operation(name, values, parent = flowContext)
    }

    fun recordEvent(
        name: String,
        fields: Map<String, String> = emptyMap(),
    ) {
        val base = flowFields() ?: return
        com.pheeeew.feature.monitoring.product
            .ProductMonitoring(
                monitoring,
                "map",
            ).emit(
                name,
                (base + fields).mapValues {
                    EventValue.Text(it.value)
                },
                flowContext,
            )
    }

    fun clearFlow() {
        flowId = null
        flowSelectorId = null
        flowContext = null
        visibleStep = null
        inputModes.clear()
        submissionContext = null
    }
}
