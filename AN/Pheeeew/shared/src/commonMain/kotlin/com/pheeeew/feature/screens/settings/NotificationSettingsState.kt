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
    val saveFailureNoticeId: Long? = null,
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
    private var confirmedState = NotificationSettingsUiState()
    private val pendingChanges = mutableListOf<PreferenceChange>()
    private var nextNoticeId = 0L

    private class PreferenceChange(
        val apply: (NotificationPreferences) -> NotificationPreferences,
    )

    private fun publish(value: NotificationSettingsUiState) {
        confirmedState = value
        render()
    }

    private fun render() {
        val preferences =
            confirmedState.preferences?.let { saved ->
                pendingChanges.fold(saved) { current, change -> change.apply(current) }
            }
        mutableState.value = confirmedState.copy(preferences = preferences)
    }

    fun acknowledgeSaveFailure(id: Long) {
        if (confirmedState.saveFailureNoticeId == id) {
            publish(confirmedState.copy(saveFailureNoticeId = null))
        }
    }

    suspend fun refresh() =
        runOperation {
            publish(confirmedState.copy(authorization = null))
            val authorization = permissions.authorization()
            var preferences = store.read()
            val pending = pendingEnable
            if (pending != null && authorization.allowsNotifications()) {
                preferences = store.update(pending, true)
            }
            pendingEnable = null
            publish(
                confirmedState.copy(
                    preferences = preferences,
                    authorization = authorization,
                    busy = false,
                    failed = false,
                ),
            )
        }

    suspend fun toggle(
        kind: NotificationKind,
        enabled: Boolean,
    ) = runOperation(
        change =
            PreferenceChange { preferences ->
                when (kind) {
                    NotificationKind.COLLECTION -> preferences.copy(collectionEnabled = enabled)
                    NotificationKind.RANK_DROP -> preferences.copy(rankDropEnabled = enabled)
                }
            },
    ) {
        val authorization = permissions.authorization()
        val preferences = store.read()
        publish(confirmedState.copy(authorization = authorization, preferences = preferences))
        if (enabled && !authorization.allowsNotifications()) {
            enableWithSystemPermission(kind, permissions.canRequestAuthorization(), authorization)
        } else {
            val saved = store.update(kind, enabled)
            publish(
                confirmedState.copy(preferences = saved, authorization = authorization, busy = false, failed = false),
            )
        }
    }

    suspend fun toggleAll(enabled: Boolean) =
        runOperation(change = PreferenceChange { NotificationPreferences(enabled, enabled) }) {
            val authorization = permissions.authorization()
            val preferences = store.read()
            if (authorization.allowsNotifications() || !enabled) {
                val saved = store.replace(NotificationPreferences(enabled, enabled))
                publish(
                    confirmedState.copy(
                        preferences = saved,
                        authorization = authorization,
                        busy = false,
                        failed = false,
                    ),
                )
            } else {
                publish(confirmedState.copy(preferences = preferences, authorization = authorization))
                error("Notification permission is not granted")
            }
        }

    private suspend fun enableWithSystemPermission(
        kind: NotificationKind,
        canRequestAuthorization: Boolean,
        authorization: NotificationAuthorization,
    ) {
        if (canRequestAuthorization) {
            permissions.requestAuthorization()
            val updated = permissions.authorization()
            val saved = if (updated.allowsNotifications()) store.update(kind, true) else store.read()
            publish(confirmedState.copy(preferences = saved, authorization = updated, busy = false, failed = false))
        } else {
            pendingEnable = kind
            if (!permissions.openSettings()) {
                pendingEnable = null
                error("Settings did not open")
            }
            publish(confirmedState.copy(authorization = authorization, busy = false))
        }
    }

    private suspend fun runOperation(
        change: PreferenceChange? = null,
        block: suspend () -> Unit,
    ) {
        // Register before waiting for the mutex so every tap is visible immediately.
        val optimisticChange = change?.takeIf { confirmedState.authorization.allowsNotifications() }
        optimisticChange?.let { pendingChanges.add(it) }
        render()
        try {
            mutex.withLock {
                publish(confirmedState.copy(busy = true, failed = false))
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    pendingEnable = null
                    publish(
                        confirmedState.copy(
                            failed = true,
                            saveFailureNoticeId =
                                if (change !=
                                    null
                                ) {
                                    ++nextNoticeId
                                } else {
                                    confirmedState.saveFailureNoticeId
                                },
                        ),
                    )
                } finally {
                    optimisticChange?.let { pendingChanges.remove(it) }
                    publish(confirmedState.copy(busy = false))
                }
            }
        } finally {
            // Also remove edits cancelled while waiting for another save.
            optimisticChange?.let { pendingChanges.remove(it) }
            render()
        }
    }
}
