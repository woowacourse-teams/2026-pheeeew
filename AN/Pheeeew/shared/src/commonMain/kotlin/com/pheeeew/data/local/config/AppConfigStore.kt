package com.pheeeew.data.local.config

import com.pheeeew.domain.model.config.AppConfigSnapshot
import kotlinx.serialization.Serializable

interface AppConfigStore {
    suspend fun read(): String?

    suspend fun clear()

    suspend fun write(value: String)
}

@Serializable
internal data class CachedAppConfig(
    val schema: Int,
    val snapshot: AppConfigSnapshot,
    val fetchedAt: String,
)
