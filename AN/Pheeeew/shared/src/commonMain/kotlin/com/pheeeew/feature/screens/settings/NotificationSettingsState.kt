package com.pheeeew.feature.screens.settings

import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.NotificationPreferences
import com.pheeeew.core.notification.NotificationPreferencesStore
import com.pheeeew.core.notification.allowsNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class NotificationSettingsUiState(
    val preferences: NotificationPreferences? = null,
    val authorization: NotificationAuthorization? = null,
    val busy: Boolean = true,
    val failed: Boolean = false,
) {
    fun checked(kind: NotificationKind): Boolean =
        authorization.allowsNotifications() && preferences?.enabled(kind) == true
}

internal class NotificationSettingsState(
    private val permissions: NotificationPermissionController,
    private val store: NotificationPreferencesStore,
) {
    private val mutableState = MutableStateFlow(NotificationSettingsUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var pendingEnable: NotificationKind? = null

    suspend fun refresh() =
        runOperation {
            mutableState.value = state.value.copy(authorization = null)
            val authorization = permissions.authorization()
            var preferences = store.read()
            val pending = pendingEnable
            if (pending != null && authorization.allowsNotifications()) {
                preferences = enableOnly(pending)
            }
            pendingEnable = null
            mutableState.value = NotificationSettingsUiState(preferences, authorization, busy = false)
        }

    suspend fun toggle(
        kind: NotificationKind,
        enabled: Boolean,
    ) = runOperation {
        mutableState.value = state.value.copy(authorization = null)
        val authorization = permissions.authorization()
        val preferences = store.read()
        mutableState.value = state.value.copy(authorization = authorization, preferences = preferences)
        if (enabled && !authorization.allowsNotifications()) {
            enableWithSystemPermission(kind, authorization)
        } else {
            val saved = store.update(kind, enabled)
            mutableState.value = NotificationSettingsUiState(saved, authorization, busy = false)
        }
    }

    private suspend fun enableWithSystemPermission(
        kind: NotificationKind,
        authorization: NotificationAuthorization,
    ) {
        if (authorization == NotificationAuthorization.NOT_DETERMINED) {
            permissions.requestAuthorization()
            val updated = permissions.authorization()
            val saved = if (updated.allowsNotifications()) enableOnly(kind) else store.read()
            mutableState.value = NotificationSettingsUiState(saved, updated, busy = false)
        } else {
            pendingEnable = kind
            if (!permissions.openSettings()) {
                pendingEnable = null
                error("Settings did not open")
            }
            mutableState.value = state.value.copy(authorization = authorization, busy = false)
        }
    }

    private suspend fun enableOnly(kind: NotificationKind): NotificationPreferences {
        // While system permission was blocked, every toggle appeared OFF. Respect that
        // visible state when enabling one item rather than restoring hidden ON defaults.
        NotificationKind.entries.filter { it != kind }.forEach { store.update(it, false) }
        return store.update(kind, true)
    }

    private suspend fun runOperation(block: suspend () -> Unit) {
        mutex.withLock {
            mutableState.value = state.value.copy(busy = true, failed = false)
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                pendingEnable = null
                // Never show a failed save as successfully applied; the next refresh reads the source again.
                mutableState.value = state.value.copy(busy = false, failed = true)
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }
}
