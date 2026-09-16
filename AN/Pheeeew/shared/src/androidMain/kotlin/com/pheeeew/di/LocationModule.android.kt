package com.pheeeew.di

import androidx.activity.ComponentActivity
import com.pheeeew.core.location.AndroidPlatformLocationProvider
import com.pheeeew.core.permission.AndroidLocationPermissionController
import com.pheeeew.core.permission.AndroidLocationPermissionSettingsLauncher

/**
 * 위치 의존성을 만들거나, 구성 변경 뒤 유지된 동일 인스턴스를 새 Activity에 연결합니다.
 */
fun createAndroidLocationDependencies(
    activity: ComponentActivity,
    retainedDependencies: LocationDependencies?,
): LocationDependencies {
    retainedDependencies?.let { dependencies ->
        val permissionController =
            dependencies.permissionController as? AndroidLocationPermissionController
                ?: error("retainedDependencies must use AndroidLocationPermissionController")
        permissionController.attach(activity)
        return dependencies
    }

    val permissionController = AndroidLocationPermissionController(activity)
    return LocationModule.create(
        permissionController = permissionController,
        permissionSettingsLauncher = AndroidLocationPermissionSettingsLauncher(activity),
        locationProvider = AndroidPlatformLocationProvider(activity),
    )
}
