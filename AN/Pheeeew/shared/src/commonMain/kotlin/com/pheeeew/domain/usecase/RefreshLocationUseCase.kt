package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.permission.LocationPermissionStatus
import com.pheeeew.domain.repository.LocationRepository

class RefreshLocationUseCase(
    private val permissionController: LocationPermissionController,
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(requestPermission: Boolean): LocationState {
        when (permissionController.currentStatus()) {
            LocationPermissionStatus.Denied,
            LocationPermissionStatus.PermanentlyDenied,
            LocationPermissionStatus.ServicesDisabled,
            -> if (requestPermission) permissionController.requestPermission()

            LocationPermissionStatus.Granted,
            -> Unit
        }
        repository.refresh()
        return repository.state.value
    }
}
