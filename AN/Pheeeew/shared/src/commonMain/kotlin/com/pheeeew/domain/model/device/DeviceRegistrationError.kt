package com.pheeeew.domain.model.device

sealed interface DeviceRegistrationError {
    data object Network : DeviceRegistrationError

    data object InvalidRefreshToken : DeviceRegistrationError

    data object DeviceNotFound : DeviceRegistrationError

    data object InvalidChallenge : DeviceRegistrationError

    data object AttestationRejected : DeviceRegistrationError

    data object Server : DeviceRegistrationError

    data class RetryableServer(
        val retryAfterSeconds: Long? = null,
    ) : DeviceRegistrationError

    data object Unknown : DeviceRegistrationError
}
