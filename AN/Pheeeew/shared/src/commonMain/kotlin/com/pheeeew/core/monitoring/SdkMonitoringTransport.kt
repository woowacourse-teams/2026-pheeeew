package com.pheeeew.core.monitoring

import com.pheeeew.core.monitoring.transport.CompositeMonitoringTransport

/** Compatibility façade used by platform startup code. */
class SdkMonitoringTransport : MonitoringTransport {
    private val delegate = CompositeMonitoringTransport()

    var enabled: Boolean
        get() = delegate.enabled
        set(value) {
            delegate.enabled = value
        }

    override fun track(event: MonitoringEvent): Boolean = delegate.track(event)

    override fun context(snapshot: MonitoringSnapshot?) {
        delegate.context(snapshot)
    }

    override fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) {
        delegate.report(error, snapshot)
    }

    override fun flush() {
        delegate.flush()
    }
}

object NoOpMonitoringTransport : MonitoringTransport {
    override fun track(event: MonitoringEvent): Boolean = false

    override fun context(snapshot: MonitoringSnapshot?) = Unit

    override fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) = Unit
}
