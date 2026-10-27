package com.pheeeew.feature.screens.settings

import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationPermissionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class NotificationPermissionUiState(
    val authorization: NotificationAuthorization? = null,
    val canRequestAuthorization: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Reads OS state rather than persisting permission as an app preference. Use on the UI thread. */
internal class NotificationPermissionState(
    private val controller: NotificationPermissionController,
) {
    private val mutableState = MutableStateFlow(NotificationPermissionUiState())
    val state = mutableState.asStateFlow()
    private var generation = 0

    suspend fun refresh() {
        val request = ++generation
        mutableState.value = NotificationPermissionUiState(loading = true)
        try {
            val authorization = controller.authorization()
            val canRequest = controller.canRequestAuthorization()
            if (request == generation) {
                mutableState.value = NotificationPermissionUiState(authorization, canRequestAuthorization = canRequest)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (request == generation) mutableState.value = NotificationPermissionUiState(failed = true)
        }
    }

    suspend fun requestPermission() {
        mutableState.value = NotificationPermissionUiState(loading = true)
        try {
            if (!controller.canRequestAuthorization()) {
                refresh()
                return
            }
            controller.requestAuthorization()
            refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = NotificationPermissionUiState(failed = true)
        }
    }

    suspend fun openSettings(): Boolean = controller.openSettings()
}
