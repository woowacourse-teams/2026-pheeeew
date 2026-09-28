package com.pheeeew.feature.screens.map.monitoring

import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventContext
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.ValueType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

object ExplorationEvents {
    private fun text(vararg values: String) =
        PropertyRule(
            ValueType.TEXT,
            required = true,
            allowed = values.toSet().takeIf { it.isNotEmpty() },
        )

    private val number = PropertyRule(ValueType.INTEGER, required = true, minimum = 0.0)
    private val visit = mapOf("view_id" to text(), "entry_source" to text("map", "list"))
    private val load =
        visit +
            mapOf(
                "source_load_id" to PropertyRule(ValueType.TEXT),
                "load_id" to text(),
                "outcome" to text("success", "failed", "cancelled"),
                "presentation_state" to text("presented", "not_presented"),
                "visible_item_count" to number,
                "data_origin" to text("network", "cache"),
                "load_kind" to text("initial", "refresh", "pagination", "viewport", "cache", "resume"),
                "response_at_ms" to number,
                "load_duration_ms" to number,
                "presentation_delay_ms" to number,
            )
    private val detail =
        visit + mapOf("detail_view_id" to text(), "entry_key" to text(), "is_own" to PropertyRule(ValueType.BOOLEAN))
    val mapLoaded = EventDefinition("map_emotions_load_finished", properties = load)
    val listViewed = EventDefinition("nearby_list_viewed", properties = visit)
    val listLoaded = EventDefinition("nearby_list_load_finished", properties = load)
    val detailOpened = EventDefinition("emotion_detail_opened", properties = detail)
    val detailLoaded =
        EventDefinition(
            "emotion_detail_load_finished",
            properties =
                detail +
                    mapOf(
                        "load_id" to text(),
                        "outcome" to text("success", "failed", "cancelled"),
                        "load_duration_ms" to number,
                    ),
        )
    val detailViewed = EventDefinition("emotion_detail_viewed", properties = detail + ("outcome" to text("success")))
    val definitions = listOf(mapLoaded, listViewed, listLoaded, detailOpened, detailLoaded, detailViewed)
}

private fun id() = Uuid.random().toString()

private fun String.value() = EventValue.Text(this)

private fun Long.value() = EventValue.Integer(coerceAtLeast(0))

private fun Monitoring.safeContext() = runCatching { context("map") }.getOrNull()

private fun Monitoring.emit(
    event: EventDefinition,
    fields: Map<String, EventValue>,
    context: EventContext?,
) {
    context ?: return
    runCatching { track(DefinedEvent(event, fields), context) }
}

/** UI-thread owned. A load result and its actual presentation are two separate milestones. */
class ContentMonitoring(
    private val monitoring: Monitoring,
    private val scope: CoroutineScope,
    private val source: String,
) {
    var viewId: String = id()
        private set
    private var active = false
    private var visited = false
    private val pending = mutableSetOf<ContentLoad>()
    private val impressions = mutableMapOf<Long, String>()
    private val bodies = mutableSetOf<Long>()

    fun itemVisible(
        entry: Long,
        body: Boolean = false,
        isOwn: Boolean? = null,
    ) {
        if (!active) return
        val telemetry =
            com.pheeeew.feature.monitoring.product
                .ProductMonitoring(monitoring, "map")
        val ownership = isOwn?.let { mapOf("is_own" to EventValue.Flag(it)) }.orEmpty()
        val impression =
            impressions.getOrPut(entry) {
                val value = id()
                telemetry.emit(
                    "emotion_item_impression",
                    fields() + ownership +
                        mapOf("entry_key" to entry.toString().value(), "impression_id" to value.value()),
                )
                value
            }
        if (body &&
            bodies.add(entry)
        ) {
            telemetry.emit(
                "emotion_content_viewed",
                fields() + ownership +
                    mapOf("entry_key" to entry.toString().value(), "impression_id" to impression.value()),
            )
        }
    }

    fun visibility(visible: Boolean): Boolean {
        if (visible == active) return false
        active = visible
        if (!visible) {
            pending.toList().forEach { it.hidden() }
            return false
        }
        if (visited) viewId = id()
        impressions.clear()
        bodies.clear()
        visited = true
        if (source == "list") monitoring.emit(ExplorationEvents.listViewed, fields(), monitoring.safeContext())
        return true
    }

    fun load(
        kind: String,
        origin: String = "network",
        sourceLoadId: String? = null,
    ): ContentLoad {
        val requestedViewId = viewId
        return ContentLoad(
            monitoring,
            scope,
            if (source == "map") ExplorationEvents.mapLoaded else ExplorationEvents.listLoaded,
            fields() + sourceLoadId?.let { mapOf("source_load_id" to it.value()) }.orEmpty(),
            kind,
            origin,
            monitoring.safeContext(),
            canPresent = { active && viewId == requestedViewId },
            onFinished = { pending.remove(it) },
        ).also { pending.add(it) }
    }

    fun display(
        load: ContentLoad,
        origin: String = "network",
    ): ContentLoad {
        load.ready(origin)
        // A response begun on an earlier visit remains a non-presentation of that request.
        // The current visit can independently display the now-cached data.
        return if (active && !load.awaitingPresentation) {
            this.load("resume", "cache", load.loadId).also { it.ready() }
        } else {
            load
        }
    }

    fun presented(
        load: ContentLoad,
        count: Int,
    ) {
        load.presented(count)
        if (load.takeLatePresentation()) {
            this.load("resume", "cache", load.loadId).also {
                it.ready()
                it.presented(count)
            }
        }
    }

    fun supersede() {
        pending.toList().forEach { it.hidden() }
    }

    fun close() {
        active = false
        supersede()
        pending.toList().forEach { it.cancelled() }
    }

    private fun fields() = mapOf("view_id" to viewId.value(), "entry_source" to source.value())
}

class ContentLoad internal constructor(
    private val monitoring: Monitoring,
    private val scope: CoroutineScope,
    private val definition: EventDefinition,
    private val visit: Map<String, EventValue>,
    private val kind: String,
    private var origin: String,
    private val requestContext: EventContext?,
    private val canPresent: () -> Boolean,
    private val onFinished: (ContentLoad) -> Unit,
) {
    val loadId: String = id()
    private val start = TimeSource.Monotonic.markNow()
    private var responseMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var responseAt = 0L
    private var duration = 0L
    private var ended = false
    private var presentedOnce = false
    private var latePresentationTaken = false
    private var detached = false
    private var timeout: Job? = null
    val awaitingPresentation: Boolean get() = !ended && !detached && canPresent()

    fun ready(dataOrigin: String = origin) {
        if (ended) return
        origin = dataOrigin
        responseMark = TimeSource.Monotonic.markNow()
        responseAt = Clock.System.now().toEpochMilliseconds()
        duration = start.elapsedNow().inWholeMilliseconds
        if (detached || !canPresent()) {
            finish("success", false, 0)
        } else {
            timeout =
                scope.launch {
                    delay(10_000)
                    finish("success", false, 0)
                }
        }
    }

    fun presented(count: Int) {
        if (responseMark == null || ended || detached || !canPresent()) return
        finish("success", true, count)
    }

    internal fun takeLatePresentation(): Boolean {
        if (!ended || responseMark == null || detached || !canPresent() || presentedOnce ||
            latePresentationTaken
        ) {
            return false
        }
        latePresentationTaken = true
        return true
    }

    fun failed() = finish("failed", false, 0)

    fun cancelled() = finish("cancelled", false, 0)

    fun hidden() {
        detached = true
        if (responseMark != null) finish("success", false, 0)
    }

    private fun finish(
        outcome: String,
        presented: Boolean,
        count: Int,
    ) {
        if (ended) return
        ended = true
        presentedOnce = presented
        timeout?.cancel()
        val fields =
            visit +
                mapOf(
                    "load_id" to loadId.value(),
                    "outcome" to outcome.value(),
                    "presentation_state" to (if (presented) "presented" else "not_presented").value(),
                    "visible_item_count" to count.toLong().value(),
                    "data_origin" to origin.value(),
                    "load_kind" to kind.value(),
                    "response_at_ms" to
                        (responseAt.takeIf { it > 0 } ?: Clock.System.now().toEpochMilliseconds()).value(),
                    "load_duration_ms" to
                        (if (responseMark != null) duration else start.elapsedNow().inWholeMilliseconds).value(),
                    "presentation_delay_ms" to (responseMark?.elapsedNow()?.inWholeMilliseconds ?: 0).value(),
                )
        monitoring.emit(definition, fields, if (presented) monitoring.safeContext() else requestContext)
        onFinished(this)
    }
}

/** One explicit detail opening. Retry loads retain the visit while using separate load IDs. */
class DetailVisit(
    monitoring: Monitoring,
    entryId: Long,
    source: String,
    viewId: String,
) {
    private val monitoring = monitoring
    private var fields: Map<String, EventValue> =
        mapOf(
            "detail_view_id" to id().value(),
            "entry_key" to entryId.toString().value(),
            "entry_source" to source.value(),
            "view_id" to viewId.value(),
        )
    private var contentViewed = false

    fun ownership(isOwn: Boolean) {
        fields = fields + ("is_own" to EventValue.Flag(isOwn))
    }

    fun contentShown() {
        if (closed || contentViewed) return
        contentViewed = true
        com.pheeeew.feature.monitoring.product
            .ProductMonitoring(
                monitoring,
                "map",
            ).emit("emotion_content_viewed", fields)
    }

    fun productMonitoring() =
        com.pheeeew.feature.monitoring.product
            .ProductMonitoring(monitoring, "map")

    fun eventFields() = fields

    private var viewed = false
    private var closed = false

    init {
        monitoring.emit(ExplorationEvents.detailOpened, fields, monitoring.safeContext())
    }

    fun load() = DetailLoad(monitoring, fields)

    fun close() {
        closed = true
    }

    fun shown() {
        if (viewed || closed) return
        viewed = true
        monitoring.emit(
            ExplorationEvents.detailViewed,
            fields + ("outcome" to "success".value()),
            monitoring.safeContext(),
        )
    }
}

class DetailLoad internal constructor(
    private val monitoring: Monitoring,
    private val fields: Map<String, EventValue>,
) {
    private val context = monitoring.safeContext()
    private val loadId = id()
    private val start = TimeSource.Monotonic.markNow()
    private var ended = false

    fun finish(
        outcome: String,
        isOwn: Boolean? = null,
    ) {
        if (ended) return
        ended = true
        monitoring.emit(
            ExplorationEvents.detailLoaded,
            fields + isOwn?.let { mapOf("is_own" to EventValue.Flag(it)) }.orEmpty() +
                mapOf(
                    "load_id" to loadId.value(),
                    "outcome" to outcome.value(),
                    "load_duration_ms" to start.elapsedNow().inWholeMilliseconds.value(),
                ),
            context,
        )
    }
}
