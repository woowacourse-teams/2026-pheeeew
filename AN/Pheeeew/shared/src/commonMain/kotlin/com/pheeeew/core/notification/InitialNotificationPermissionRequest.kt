package com.pheeeew.core.notification

import kotlinx.coroutines.CancellationException

/** Checks OS authorization once per app UI entry, after the first map's location handling. */
class InitialNotificationPermissionRequest {
    private var handled = false

    suspend fun requestIfNeeded(controller: NotificationPermissionController) {
        if (handled) return
        // Avoid duplicate dialogs when readiness effects restart in the same UI entry.
        handled = true
        try {
            if (controller.authorization() == NotificationAuthorization.NOT_DETERMINED &&
                controller.canRequestAuthorization()
            ) {
                controller.requestAuthorization()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Permission failure must not prevent entry into the map.
        }
    }
}
