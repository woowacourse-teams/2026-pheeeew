package com.pheeeew.domain.model.device

sealed interface DeviceRegistrationState {
    data object Unknown : DeviceRegistrationState
    data object Unregistered : DeviceRegistrationState
    data object Registering : DeviceRegistrationState
    data object Refreshing : DeviceRegistrationState
    data class Registered(
        val accessTokenExpiresAtEpochSeconds: Long,
    ) : DeviceRegistrationState

    data class Failed(
        val error: DeviceRegistrationError,
    ) : DeviceRegistrationState
}
