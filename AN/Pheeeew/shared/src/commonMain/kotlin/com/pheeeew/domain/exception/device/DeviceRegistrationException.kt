package com.pheeeew.domain.exception.device

sealed class DeviceRegistrationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class InvalidRefreshToken(
        cause: Throwable? = null,
    ) : DeviceRegistrationException(
        message = "Refresh token is invalid.",
        cause = cause,
    )

    class DeviceNotFound(
        cause: Throwable? = null,
    ) : DeviceRegistrationException(
        message = "Registered device was not found.",
        cause = cause,
    )

    class Network(
        cause: Throwable? = null,
    ) : DeviceRegistrationException(
        message = "Network error occurred.",
        cause = cause,
    )

    class Server(
        cause: Throwable? = null,
    ) : DeviceRegistrationException(
        message = "Device server error occurred.",
        cause = cause,
    )

    class InvalidChallenge(cause: Throwable? = null) : DeviceRegistrationException(
        message = "Device challenge is invalid or expired.",
        cause = cause,
    )

    class AttestationRejected(cause: Throwable? = null) : DeviceRegistrationException(
        message = "Device attestation was rejected.",
        cause = cause,
    )

    class RetryableServer(
        val retryAfterSeconds: Long? = null,
        cause: Throwable? = null,
    ) : DeviceRegistrationException(
        message = "Device verification is temporarily unavailable.",
        cause = cause,
    )
}
