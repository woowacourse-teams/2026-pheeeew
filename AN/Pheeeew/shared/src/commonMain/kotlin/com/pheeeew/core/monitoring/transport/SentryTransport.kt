package com.pheeeew.core.monitoring.transport

import com.pheeeew.core.monitoring.MonitoringSnapshot
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb

/** Owns only Sentry breadcrumbs, scope context, and exception reporting. */
internal class SentryTransport {
    var enabled = false

    fun breadcrumb(eventName: String) {
        if (enabled) {
            runCatching { Sentry.addBreadcrumb(Breadcrumb(category = "monitoring", message = eventName)) }
        }
    }

    fun context(snapshot: MonitoringSnapshot?) {
        if (!enabled) return
        Sentry.configureScope { scope ->
            MONITORING_TAGS.forEach(scope::removeTag)
            snapshot?.properties()?.forEach { (key, value) -> scope.setTag(key, value) }
        }
    }

    fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) {
        if (!enabled) return
        Sentry.captureException(error) { scope ->
            MONITORING_TAGS.forEach(scope::removeTag)
            snapshot?.properties()?.forEach { (key, value) -> scope.setTag(key, value) }
        }
    }

    private companion object {
        val MONITORING_TAGS =
            listOf("session_id", "sigh_attempt_id", "save_attempt_id", "capture_id", "capture_index")
    }
}
