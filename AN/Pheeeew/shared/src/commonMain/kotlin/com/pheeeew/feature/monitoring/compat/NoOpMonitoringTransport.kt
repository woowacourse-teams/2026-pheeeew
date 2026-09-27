package com.pheeeew.feature.monitoring.compat

internal object NoOpMonitoringTransport : MonitoringTransport {
    override fun track(event: MonitoringEvent) = false

    override fun context(snapshot: MonitoringSnapshot?) = Unit

    override fun report(
        error: Throwable,
        snapshot: MonitoringSnapshot?,
    ) = Unit
}
