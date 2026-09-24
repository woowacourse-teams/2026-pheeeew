package com.pheeeew.data.location.platform.android

import androidx.activity.ComponentActivity
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.data.location.repository.LocationRepositoryImpl

fun createAndroidLocationDependencies(
    activity: ComponentActivity,
    retainedDependencies: LocationDependencies?,
): LocationDependencies {
    val permissionController = retainedDependencies?.permissionController
        ?.let { it as? AndroidLocationPermissionController }
        ?.also { it.attach(activity) }
        ?: AndroidLocationPermissionController(activity)
    return LocationDependencies(
        permissionController = permissionController,
        repository = LocationRepositoryImpl(
            permissionController = permissionController,
            provider = AndroidPlatformLocationProvider(activity),
        ),
    )
}
