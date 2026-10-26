package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

/** Read-only entry point: opening an alert never claims a card. */
class CollectionTodayApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun find() =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                "/api/v1/collection-cards/today",
                RequestKind.READ,
                monitoringEndpoint = "collection_today",
            ),
        ) { it.body<CollectionToday>() }
}

@Serializable
data class CollectionToday(
    val date: String,
    val stamps: CollectionProgress,
    val presses: CollectionProgress,
    val claimed: Boolean,
    val claimable: Boolean,
)

@Serializable
data class CollectionProgress(
    val count: Long,
    val target: Long,
    val remaining: Long,
)
