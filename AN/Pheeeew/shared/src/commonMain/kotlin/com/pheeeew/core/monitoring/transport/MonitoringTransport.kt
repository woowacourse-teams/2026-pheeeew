package com.pheeeew.core.monitoring.transport

import com.pheeeew.core.monitoring.MonitoringEvent
import com.pheeeew.core.monitoring.MonitoringSnapshot

interface MonitoringTransport {
    /** True means accepted by the SDK, not acknowledged by the remote server. */
    fun track(event: MonitoringEvent): Boolean

    fun context(snapshot: MonitoringSnapshot?)

    fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    )

    /** Requests immediate delivery of events already accepted by the SDK. */
    fun flush() = Unit
}
