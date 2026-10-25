package com.pheeeew.core.notification

enum class NotificationAuthorization {
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,
    PROVISIONAL,
    EPHEMERAL,
    UNKNOWN,
}

interface NotificationPermissionController {
    suspend fun authorization(): NotificationAuthorization

    suspend fun requestAuthorization()

    suspend fun openSettings(): Boolean
}
