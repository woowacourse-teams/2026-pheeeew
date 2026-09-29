package com.pheeeew.data.location.repository

import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

class LocationRepositoryImpl(
    private val permissionController: LocationPermissionController,
    private val provider: PlatformLocationProvider,
) : LocationRepository {
    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow<LocationState>(LocationState.Loading)
    override val state: StateFlow<LocationState> = _state

    override suspend fun refresh() {
        refreshMutex.withLock {
            when (permissionController.currentStatus()) {
                LocationPermissionStatus.Granted -> {
                    _state.value = resolveLocation()
                }

                LocationPermissionStatus.ServicesDisabled -> {
                    _state.value = LocationState.Unavailable(LocationError.ServicesDisabled)
                }

                LocationPermissionStatus.Denied,
                LocationPermissionStatus.PermanentlyDenied,
                -> {
                    _state.value = LocationState.Unavailable(LocationError.PermissionDenied)
                }
            }
        }
    }

    private suspend fun resolveLocation(): LocationState {
        val location =
            withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) { provider.getCurrentLocation() }
                ?: return LocationState.Unavailable(LocationError.LocationTimeout)
        val age = Clock.System.now().toEpochMilliseconds() - location.capturedAtMillis
        return if (
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            location.accuracyMeters.isFinite() &&
            location.accuracyMeters >= 0f &&
            age in 0..MAXIMUM_LOCATION_AGE_MILLIS
        ) {
            LocationState.Available(location)
        } else {
            LocationState.Unavailable(LocationError.LocationTimeout)
        }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 30_000L
        const val MAXIMUM_LOCATION_AGE_MILLIS = 60_000L
    }
}
