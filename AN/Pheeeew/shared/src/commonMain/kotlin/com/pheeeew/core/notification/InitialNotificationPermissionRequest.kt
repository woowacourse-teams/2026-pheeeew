package com.pheeeew.core.notification

import com.pheeeew.domain.repository.OnboardingStore
import kotlinx.coroutines.CancellationException

/** Created before onboarding; invoked after the first map's location handling. */
class InitialNotificationPermissionRequest(
    private val store: OnboardingStore,
) {
    private var handled = store.isInitialNotificationPermissionHandled()

    suspend fun requestIfNeeded(controller: NotificationPermissionController) {
        if (handled) return
        // Consume before opening the system dialog, even if it is dismissed or the UI is recreated.
        handled = true
        store.completeInitialNotificationPermission()
        try {
            if (controller.canRequestAuthorization()) controller.requestAuthorization()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Permission failure must not prevent entry into the map.
        }
    }
}
