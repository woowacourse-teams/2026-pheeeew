package com.pheeeew.legacy.di

import com.pheeeew.legacy.core.location.IosPlatformLocationProvider
import com.pheeeew.legacy.core.permission.IosLocationPermissionController
import com.pheeeew.legacy.core.permission.IosLocationPermissionSettingsLauncher
import com.pheeeew.legacy.di.LocationDependencies
import com.pheeeew.legacy.di.LocationModule

fun createIosLocationDependencies(): LocationDependencies =
    LocationModule.create(
        permissionController = IosLocationPermissionController(),
        permissionSettingsLauncher = IosLocationPermissionSettingsLauncher(),
        locationProvider = IosPlatformLocationProvider(),
    )
