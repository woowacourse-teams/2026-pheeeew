package com.pheeeew.feature.monitoring.product
import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.feature.screens.map.monitoring.RecordFunnelEvents
import kotlinx.coroutines.CancellationException

class MonitoredLocationPermission(
    private val delegate: LocationPermissionController,
    private val monitoring: Monitoring,
) : LocationPermissionController {
    override suspend fun currentStatus() = delegate.currentStatus()

    override suspend fun requestPermission(): LocationPermissionStatus {
        val context = runCatching { monitoring.context("map") }.getOrNull()
        var outcome = "unknown"
        try {
            return delegate.requestPermission().also {
                outcome =
                    when (it) {
                        LocationPermissionStatus.Granted -> "granted"
                        LocationPermissionStatus.ServicesDisabled -> "services_disabled"
                        else -> "denied"
                    }
            }
        } catch (cancelled: CancellationException) {
            outcome = "cancelled"
            throw cancelled
        } finally {
            if (context != null) {
                runCatching {
                    monitoring.track(
                        DefinedEvent(
                            RecordFunnelEvents.permission,
                            labels("permission" to "location", "outcome" to outcome),
                        ),
                        context,
                    )
                }
            }
        }
    }
}
