package com.pheeeew.feature.monitoring.product
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.device.DeviceSessionRepository

class MonitoredDeviceSession(
    private val delegate: DeviceSessionRepository,
    monitoring: Monitoring,
) : DeviceSessionRepository {
    private val telemetry = ProductMonitoring(monitoring, "network")

    override suspend fun prepare() =
        telemetry.operation("device_session_prepare_finished").observe(
            { if (it is DeviceSessionResult.Ready) "success" else "failed" },
        ) { delegate.prepare() }
}
