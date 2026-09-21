package com.pheeeew.core.monitoring.transport

import com.pheeeew.core.monitoring.MonitoringEvent
import com.posthog.kmp.CaptureOptions
import com.posthog.kmp.PostHog
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Owns only PostHog event delivery. Filtering is configured by PostHog beforeSend. */
internal class PostHogTransport {
    var enabled = false

    fun track(event: MonitoringEvent): Boolean {
        if (!enabled) return false
        val properties =
            event.properties.mapValues { (_, value) ->
                val primitive = value as JsonPrimitive
                if (primitive.isString) {
                    primitive.content
                } else {
                    primitive.booleanOrNull ?: primitive.longOrNull ?: primitive.doubleOrNull ?: primitive.content
                }
            }
        PostHog.capture(event.name, properties, CaptureOptions(timestamp = event.timestamp))
        return true
    }

    fun flush() {
        if (enabled) PostHog.flush()
    }
}
