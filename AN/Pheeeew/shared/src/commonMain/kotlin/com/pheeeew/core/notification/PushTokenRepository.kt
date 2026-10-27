package com.pheeeew.core.notification

import com.pheeeew.core.network.ApiResult

interface PushTokenRepository {
    suspend fun register(
        token: String,
        expectedSessionGeneration: Long,
    ): ApiResult<Unit>

    suspend fun unregister(expectedSessionGeneration: Long): ApiResult<Unit>
}

/** Store only the latest SDK token; every activation reconciles it with current permission. */
interface PushTokenStore {
    suspend fun read(): String?

    suspend fun write(token: String)
}
