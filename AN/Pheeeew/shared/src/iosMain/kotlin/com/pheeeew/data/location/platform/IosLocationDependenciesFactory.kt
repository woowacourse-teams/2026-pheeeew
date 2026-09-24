package com.pheeeew.data.location.platform.ios

import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.data.location.repository.LocationRepositoryImpl
import platform.CoreLocation.CLLocationManager

fun createIosLocationDependencies(): LocationDependencies {
    val locationManager = CLLocationManager()
    val permissionController = IosLocationPermissionController(locationManager)
    return LocationDependencies(
        permissionController = permissionController,
        repository =
            LocationRepositoryImpl(
                permissionController = permissionController,
                provider = IosPlatformLocationProvider(locationManager),
            ),
    )
}
