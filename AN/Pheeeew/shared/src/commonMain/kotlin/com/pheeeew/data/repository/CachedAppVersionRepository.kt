package com.pheeeew.data.repository

import com.pheeeew.domain.model.version.AppVersionPolicy
import com.pheeeew.domain.model.version.validateAppVersionPolicy
import com.pheeeew.domain.repository.AppVersionRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

interface AppVersionCacheStorage {
    fun read(): String?

    fun write(value: String)
}

/** Only complete, validated policies are persisted. A network failure never overwrites the cache. */
class CachedAppVersionRepository(
    private val remote: AppVersionRepository,
    private val storage: AppVersionCacheStorage,
    private val platform: String,
    private val environment: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AppVersionRepository {
    override fun cachedPolicy(): AppVersionPolicy? =
        runCatching {
            val entry = Json.decodeFromString<Entry>(storage.read() ?: return null)
            val timestamp = now()
            if (entry.schema != 1 || entry.platform != platform || entry.environment != environment ||
                entry.fetchedAt < 0 || entry.fetchedAt > timestamp || timestamp - entry.fetchedAt >= MAX_AGE_MILLIS
            ) {
                return null
            }
            entry.policy().also(::validateAppVersionPolicy)
        }.getOrNull()

    override suspend fun getPolicy(): AppVersionPolicy {
        val policy = remote.getPolicy().also(::validateAppVersionPolicy)
        runCatching {
            storage.write(
                Json.encodeToString(
                    Entry(
                        1,
                        platform,
                        environment,
                        now(),
                        policy.minSupportedVersion,
                        policy.latestVersion,
                        policy.storeUrl,
                    ),
                ),
            )
        }
        return policy
    }

    @Serializable
    private data class Entry(
        val schema: Int,
        val platform: String,
        val environment: String,
        val fetchedAt: Long,
        val minimum: String,
        val latest: String,
        val storeUrl: String,
    ) {
        fun policy() = AppVersionPolicy(minimum, latest, storeUrl)
    }

    companion object {
        const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
