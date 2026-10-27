package com.pheeeew.core.notification

enum class NotificationAuthorization {
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,
    PROVISIONAL,
    EPHEMERAL,
    UNKNOWN,
}

interface NotificationAuthorizationProvider {
    suspend fun authorization(): NotificationAuthorization
}

interface NotificationPermissionController : NotificationAuthorizationProvider {
    /** Whether this platform can show an authorization prompt right now. */
    suspend fun canRequestAuthorization(): Boolean = authorization() == NotificationAuthorization.NOT_DETERMINED

    suspend fun requestAuthorization()

    suspend fun openSettings(): Boolean
}
