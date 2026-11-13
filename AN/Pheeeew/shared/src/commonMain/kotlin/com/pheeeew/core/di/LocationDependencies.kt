package com.pheeeew.core.di

import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.repository.LocationRepository

class LocationDependencies(
    val permissionController: LocationPermissionController,
    val repository: LocationRepository,
    private val release: () -> Unit = {},
) : AutoCloseable {
    override fun close() = release()
}
