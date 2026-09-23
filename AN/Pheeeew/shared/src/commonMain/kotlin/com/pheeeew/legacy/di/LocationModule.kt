package com.pheeeew.legacy.di

import com.pheeeew.legacy.core.location.LocationFreshnessPolicy
import com.pheeeew.legacy.core.location.PlatformLocationProvider
import com.pheeeew.legacy.core.permission.LocationPermissionController
import com.pheeeew.legacy.core.permission.LocationPermissionSettingsLauncher
import com.pheeeew.legacy.data.repository.LocationRepositoryImpl
import com.pheeeew.legacy.domain.repository.LocationRepository

/** 지도와 등록 흐름이 반드시 같은 위치 Repository를 공유하도록 묶은 의존성입니다. */
data class LocationDependencies(
    val permissionController: LocationPermissionController,
    val permissionSettingsLauncher: LocationPermissionSettingsLauncher,
    val repository: LocationRepository,
)

object LocationModule {
    fun create(
        permissionController: LocationPermissionController,
        permissionSettingsLauncher: LocationPermissionSettingsLauncher,
        locationProvider: PlatformLocationProvider,
        freshnessPolicy: LocationFreshnessPolicy = LocationFreshnessPolicy(),
        locationTimeoutMillis: Long = LocationRepositoryImpl.DEFAULT_LOCATION_TIMEOUT_MILLIS,
    ): LocationDependencies =
        LocationDependencies(
            permissionController = permissionController,
            permissionSettingsLauncher = permissionSettingsLauncher,
            repository =
                LocationRepositoryImpl(
                    permissionController = permissionController,
                    locationProvider = locationProvider,
                    freshnessPolicy = freshnessPolicy,
                    locationTimeoutMillis = locationTimeoutMillis,
                ),
        )
}
