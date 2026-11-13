package com.pheeeew.core.notification

import kotlinx.coroutines.CompletableDeferred

/** Main-thread owner of the OS request, independent of the lifetime of individual UI waiters. */
internal class AndroidNotificationPermissionRequest {
    private var inFlight: CompletableDeferred<Unit>? = null

    suspend fun request(launch: () -> Unit) {
        val existing = inFlight
        if (existing != null) {
            existing.await()
            return
        }
        val request = CompletableDeferred<Unit>()
        inFlight = request
        try {
            launch()
        } catch (failure: Exception) {
            if (inFlight === request) inFlight = null
            request.completeExceptionally(failure)
        }
        request.await()
    }

    fun complete() {
        val request = inFlight
        inFlight = null
        request?.complete(Unit)
    }

    fun cancel() {
        val request = inFlight
        inFlight = null
        request?.cancel()
    }
}

/** An attempted request alone is not evidence of denial: the dialog may have been dismissed. */
internal class AndroidNotificationPermissionHistory(
    hasObservedRationale: Boolean = false,
    hasBeenAuthorized: Boolean = false,
) {
    var hasObservedRationale = hasObservedRationale
        private set

    var hasBeenAuthorized = hasBeenAuthorized
        private set

    fun authorization(
        granted: Boolean,
        showsRationale: Boolean,
    ): NotificationAuthorization {
        if (granted) {
            onResult(granted = true, showsRationale = false)
            return NotificationAuthorization.AUTHORIZED
        }
        observeRationale(showsRationale)
        return if (hasObservedRationale || hasBeenAuthorized) {
            NotificationAuthorization.DENIED
        } else {
            NotificationAuthorization.NOT_DETERMINED
        }
    }

    fun canRequest(showsRationale: Boolean): Boolean = !hasObservedRationale || showsRationale

    fun observeRationale(showsRationale: Boolean) {
        if (showsRationale) hasObservedRationale = true
    }

    fun onResult(
        granted: Boolean,
        showsRationale: Boolean,
    ) {
        hasBeenAuthorized = hasBeenAuthorized || granted
        hasObservedRationale = !granted && (hasObservedRationale || showsRationale)
    }
}
