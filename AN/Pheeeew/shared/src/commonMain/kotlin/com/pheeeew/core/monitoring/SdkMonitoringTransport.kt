package com.pheeeew.core.monitoring

import com.posthog.kmp.CaptureOptions
import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogBeforeSend
import com.posthog.kmp.PostHogConfig
import com.posthog.kmp.PostHogEvent
import com.posthog.kmp.SessionRecordingConfig
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal class SdkMonitoringTransport(
    private val registry: EventRegistry,
) : MonitoringTransport {
    override fun track(event: EventEnvelope): Boolean {
        val fields = event.properties.mapValues { (_, value) -> value as? JsonPrimitive ?: return false }
        val safe = registry.sanitizeEnvelope(event.name, fields) ?: return false
        PostHog.capture(
            event.name,
            safe.mapValues { (_, value) ->
                (value as JsonPrimitive).sdkValue()
            },
            CaptureOptions(timestamp = event.timestamp),
        )
        runCatching { Sentry.addBreadcrumb(Breadcrumb(category = "monitoring", message = event.name)) }
        return true
    }

    override fun report(
        error: Throwable,
        context: Map<String, String>,
    ) {
        Sentry.captureException(error) { scope ->
            context.filterKeys { it in ERROR_TAGS }.forEach { (key, value) -> scope.setTag(key, value.take(128)) }
        }
    }

    override fun flush() {
        PostHog.flush()
    }
}

private val ERROR_TAGS = setOf("session_id", "operation_id", "screen")

internal fun MonitoringConfig.posthogConfig(registry: EventRegistry) =
    PostHogConfig(
        apiKey = posthogToken,
        host = posthogHost,
        captureApplicationLifecycleEvents = false,
        captureScreenViews = false,
        captureDeepLinks = false,
        preloadFeatureFlags = false,
        sendFeatureFlagEvent = false,
        sessionRecording = SessionRecordingConfig(enabled = false),
        maxQueueSize = queueCapacity,
        beforeSend = listOf(PostHogBeforeSend { sanitizePostHogEvent(it, registry) }),
    )

internal fun sanitizePostHogEvent(
    event: PostHogEvent,
    registry: EventRegistry,
): PostHogEvent? {
    val fields =
        event.properties
            .mapNotNull { (key, value) ->
                val primitive =
                    when (value) {
                        is String -> JsonPrimitive(value)
                        is Boolean -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value)
                        else -> null
                    }
                primitive?.let { key to it }
            }.toMap()
            .let { fields ->
                // Old SDK-persisted events used schema 1; all new events explicitly set a version.
                if ("event_schema_version" in fields) fields else fields + ("event_schema_version" to JsonPrimitive(1))
            }
    val clean = registry.sanitizeEnvelope(event.event, fields) ?: return null
    return event.copy(
        distinctId = clean.getValue("anonymous_id").let { (it as JsonPrimitive).content },
        properties =
            clean.mapValues { (_, value) -> (value as JsonPrimitive).sdkValue() } + mapOf("\$geoip_disable" to true),
    )
}

private fun JsonPrimitive.sdkValue(): Any =
    if (isString) {
        content
    } else {
        booleanOrNull ?: longOrNull ?: doubleOrNull
            ?: content
    }

internal fun identifyMonitoring(
    id: String,
    config: MonitoringConfig,
) {
    Sentry.setUser(User(id = id))
    Sentry.configureScope { it.setTag("app_platform", config.platform) }
}
