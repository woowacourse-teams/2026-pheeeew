package com.pheeeew.core.monitoring.transport

import com.pheeeew.core.monitoring.MonitoringEvent
import com.pheeeew.core.monitoring.MonitoringSnapshot

/** Coordinates SDK-specific transports without knowing monitoring flow or event semantics. */
internal class SdkMonitoringTransport {
    private val postHog = PostHogTransport()
    private val sentry = SentryTransport()

    var enabled: Boolean = false
        set(value) {
            field = value
            postHog.enabled = value
            sentry.enabled = value
        }

    fun track(event: MonitoringEvent): Boolean {
        if (!enabled) return false
        val accepted = postHog.track(event)
        if (accepted) sentry.breadcrumb(event.name)
        return accepted
    }

    fun context(snapshot: MonitoringSnapshot?) {
        sentry.context(snapshot)
    }

    fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) {
        sentry.report(error, snapshot)
    }

    fun flush() {
        postHog.flush()
    }
}
