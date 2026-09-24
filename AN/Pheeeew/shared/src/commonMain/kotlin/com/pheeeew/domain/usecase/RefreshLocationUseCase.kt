package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.LocationState
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.repository.LocationRepository

class RefreshLocationUseCase(
    private val permissionController: LocationPermissionController,
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(): LocationState {
        when (permissionController.currentStatus()) {
            LocationPermissionStatus.Denied,
            LocationPermissionStatus.ServicesDisabled,
            -> permissionController.requestPermission()
            LocationPermissionStatus.Granted,
            LocationPermissionStatus.PermanentlyDenied,
            -> Unit
        }
        repository.refresh()
        return repository.state.value
    }
}
