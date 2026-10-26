package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPreferences
import com.pheeeew.core.notification.NotificationPreferencesStore
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** No default-value cache: every edit starts with the authenticated device's server values. */
class NotificationSettingsApi(
    private val requests: ApiRequestExecutor,
    private val sessionGeneration: () -> Long? = { 0L },
) : NotificationPreferencesStore {
    private val mutex = Mutex()

    override suspend fun read(): NotificationPreferences = mutex.withLock { fetch() }

    override suspend fun update(
        kind: NotificationKind,
        enabled: Boolean,
    ): NotificationPreferences =
        mutex.withLock {
            val current = fetch()
            save(
                when (kind) {
                    NotificationKind.COLLECTION -> current.copy(collectionEnabled = enabled)
                    NotificationKind.RANK_DROP -> current.copy(rankDropEnabled = enabled)
                },
            )
        }

    override suspend fun replace(preferences: NotificationPreferences): NotificationPreferences =
        mutex.withLock {
            save(preferences)
        }

    private suspend fun fetch(): NotificationPreferences {
        val generation = requireNotNull(sessionGeneration()) { "Device session is not ready" }
        val result =
            requests.execute(
                ApiRequest(HttpMethod.Get, PATH, RequestKind.READ, monitoringEndpoint = "notification_settings"),
            ) { it.body<SettingsDto>() }
        check(sessionGeneration() == generation) { "Device session changed" }
        return when (result) {
            is ApiResult.Success -> {
                NotificationPreferences(
                    result.value.collectionClaimableEnabled,
                    result.value.rankChangedEnabled,
                )
            }

            is ApiResult.Failure -> {
                error("Notification settings could not be loaded")
            }
        }
    }

    private suspend fun save(value: NotificationPreferences): NotificationPreferences {
        val generation = requireNotNull(sessionGeneration()) { "Device session is not ready" }
        val result =
            requests.executeNoContent(
                ApiRequest(
                    HttpMethod.Put,
                    PATH,
                    RequestKind.WRITE,
                    body = SettingsDto(value.collectionEnabled, value.rankDropEnabled),
                    replayAfterAuthentication = true,
                    monitoringEndpoint = "notification_settings",
                ),
            )
        check(sessionGeneration() == generation) { "Device session changed" }
        check(result is ApiResult.Success) { "Notification settings could not be saved" }
        return fetch()
    }

    @Serializable
    private data class SettingsDto(
        val collectionClaimableEnabled: Boolean,
        val rankChangedEnabled: Boolean,
    )

    private companion object {
        const val PATH = "/api/v3/devices/me/notification-settings"
    }
}
