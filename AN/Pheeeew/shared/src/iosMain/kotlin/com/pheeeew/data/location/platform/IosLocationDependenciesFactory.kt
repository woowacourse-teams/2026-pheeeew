package com.pheeeew.data.location.platform.ios

import platform.CoreLocation.CLLocationManager
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.data.location.repository.LocationRepositoryImpl

fun createIosLocationDependencies(): LocationDependencies {
    val locationManager = CLLocationManager()
    val permissionController = IosLocationPermissionController(locationManager)
    return LocationDependencies(
        permissionController = permissionController,
        repository = LocationRepositoryImpl(
            permissionController = permissionController,
            provider = IosPlatformLocationProvider(locationManager),
        ),
    )
}
