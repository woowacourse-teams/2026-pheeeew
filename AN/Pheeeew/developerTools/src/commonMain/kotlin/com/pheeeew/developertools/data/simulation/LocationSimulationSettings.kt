package com.pheeeew.developertools.data.simulation

import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.permission.LocationPermissionStatus

enum class LocationSimulationMode {
    ACTUAL,
    FIXED,
    PERMISSION_DENIED,
    SERVICES_DISABLED,
    UNAVAILABLE,
}

data class LocationSimulationSettings(
    val mode: LocationSimulationMode = LocationSimulationMode.ACTUAL,
    val delayMillis: Long = 0L,
    val neverResponds: Boolean = false,
    val latitude: Double = DEFAULT_LATITUDE,
    val longitude: Double = DEFAULT_LONGITUDE,
)

fun interface LocationSimulationSettingsProvider {
    fun currentSettings(): LocationSimulationSettings
}

class SimulatedLocationPermissionController(
    private val delegate: LocationPermissionController,
    private val simulation: LocationSimulationSettingsProvider,
) : LocationPermissionController by delegate {
    override suspend fun currentStatus(): LocationPermissionStatus =
        when (simulation.currentSettings().mode) {
            LocationSimulationMode.PERMISSION_DENIED -> {
                LocationPermissionStatus.Denied
            }

            LocationSimulationMode.SERVICES_DISABLED -> {
                LocationPermissionStatus.ServicesDisabled
            }

            LocationSimulationMode.ACTUAL -> {
                delegate.currentStatus()
            }

            LocationSimulationMode.FIXED,
            LocationSimulationMode.UNAVAILABLE,
            -> {
                LocationPermissionStatus.Granted
            }
        }

    override suspend fun requestPermission(): LocationPermissionStatus =
        when (simulation.currentSettings().mode) {
            LocationSimulationMode.PERMISSION_DENIED -> {
                LocationPermissionStatus.Denied
            }

            LocationSimulationMode.SERVICES_DISABLED -> {
                LocationPermissionStatus.ServicesDisabled
            }

            LocationSimulationMode.ACTUAL -> {
                delegate.requestPermission()
            }

            LocationSimulationMode.FIXED,
            LocationSimulationMode.UNAVAILABLE,
            -> {
                LocationPermissionStatus.Granted
            }
        }
}

private const val DEFAULT_LATITUDE = 37.5665
private const val DEFAULT_LONGITUDE = 126.9780
