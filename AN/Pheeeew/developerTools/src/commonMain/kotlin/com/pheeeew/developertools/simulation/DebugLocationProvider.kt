package com.pheeeew.developertools.simulation

import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.domain.model.CurrentLocation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.time.Clock

/** A request snapshots its acquisition conditions; the real repository owns its deadline. */
class DebugLocationProvider(
    private val delegate: PlatformLocationProvider,
    private val simulation: LocationSimulationSettingsProvider = ErrorSimulation,
) : PlatformLocationProvider {
    override suspend fun getCurrentLocation(): CurrentLocation? {
        val settings = simulation.currentSettings()
        if (settings.neverResponds) awaitCancellation()
        delay(settings.delayMillis)
        return when (settings.mode) {
            LocationSimulationMode.ACTUAL -> {
                delegate.getCurrentLocation()
            }

            LocationSimulationMode.FIXED -> {
                require(settings.latitude.isFinite() && settings.latitude in -90.0..90.0)
                require(settings.longitude.isFinite() && settings.longitude in -180.0..180.0)
                CurrentLocation(settings.latitude, settings.longitude, 5f, Clock.System.now().toEpochMilliseconds())
            }

            // Native providers return no location on acquisition failure or lost permission.
            LocationSimulationMode.UNAVAILABLE,
            LocationSimulationMode.PERMISSION_DENIED,
            LocationSimulationMode.SERVICES_DISABLED,
            -> {
                null
            }
        }
    }
}
