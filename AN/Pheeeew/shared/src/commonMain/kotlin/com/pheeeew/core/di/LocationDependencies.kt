package com.pheeeew.core.di

import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.domain.repository.LocationRepository

data class LocationDependencies(
    val permissionController: LocationPermissionController,
    val repository: LocationRepository,
)
