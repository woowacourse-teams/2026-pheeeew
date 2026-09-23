package com.pheeeew.legacy.data.repository

import com.pheeeew.legacy.core.location.LocationFreshnessPolicy
import com.pheeeew.legacy.core.location.PlatformLocationProvider
import com.pheeeew.legacy.core.location.PlatformLocationResult
import com.pheeeew.legacy.core.permission.LocationPermissionController
import com.pheeeew.legacy.core.permission.LocationPermissionStatus
import com.pheeeew.legacy.domain.model.location.LocationError
import com.pheeeew.legacy.domain.model.location.LocationState
import com.pheeeew.legacy.domain.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * 권한 확인, OS 위치 조회, 타임아웃과 신선도 판정을 하나의 흐름으로 조립합니다.
 *
 * 위치 값은 로깅하지 않으며, 오래된 위치는 [LocationState.Available]로
 * 노출하지 않습니다.
 */
class LocationRepositoryImpl(
    private val permissionController: LocationPermissionController,
    private val locationProvider: PlatformLocationProvider,
    private val freshnessPolicy: LocationFreshnessPolicy = LocationFreshnessPolicy(),
    private val locationTimeoutMillis: Long = DEFAULT_LOCATION_TIMEOUT_MILLIS,
    private val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : LocationRepository {
    init {
        require(locationTimeoutMillis > 0L) { "locationTimeoutMillis must be positive" }
    }

    private val refreshMutex = Mutex()
    private val mutableLocationState = MutableStateFlow<LocationState>(LocationState.Loading)

    override val locationState: StateFlow<LocationState> = mutableLocationState.asStateFlow()

    override suspend fun refreshCurrentLocation() {
        refreshMutex.withLock {
            mutableLocationState.value = LocationState.Loading

            when (currentLocationPermissionStatus()) {
                LocationPermissionStatus.Granted -> {
                    mutableLocationState.value = resolveCurrentLocation()
                }

                LocationPermissionStatus.ServicesDisabled -> {
                    mutableLocationState.value =
                        LocationState.Unavailable(LocationError.ServicesDisabled)
                }

                LocationPermissionStatus.Denied,
                LocationPermissionStatus.PermanentlyDenied,
                -> {
                    mutableLocationState.value =
                        LocationState.Unavailable(LocationError.PermissionDenied)
                }
            }
        }
    }

    private suspend fun currentLocationPermissionStatus(): LocationPermissionStatus =
        try {
            permissionController.currentStatus()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            LocationPermissionStatus.Denied
        }

    private suspend fun resolveCurrentLocation(): LocationState {
        val result =
            try {
                withTimeoutOrNull(locationTimeoutMillis) {
                    locationProvider.getCurrentLocation()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return LocationState.Unavailable(LocationError.GpsUnavailable)
            }

        return when (result) {
            null -> {
                LocationState.Unavailable(LocationError.LocationTimeout)
            }

            PlatformLocationResult.GpsUnavailable -> {
                LocationState.Unavailable(LocationError.GpsUnavailable)
            }

            is PlatformLocationResult.Success -> {
                if (freshnessPolicy.isFresh(result.location, currentTimeMillis())) {
                    LocationState.Available(result.location)
                } else {
                    LocationState.Unavailable(LocationError.LocationTimeout)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_LOCATION_TIMEOUT_MILLIS: Long = 30_000L
    }
}
