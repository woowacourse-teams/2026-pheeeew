package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.CollectionCalendarDto
import com.pheeeew.data.remote.dto.CollectionDailyDto
import com.pheeeew.data.remote.dto.CollectionCardDto
import com.pheeeew.data.remote.dto.CollectionCardReferenceDto
import com.pheeeew.data.remote.dto.CollectionClaimRequestDto
import com.pheeeew.data.remote.dto.CollectionEmotionPageDto
import com.pheeeew.data.remote.dto.CollectionHistoryDto
import com.pheeeew.data.remote.dto.CollectionTodayDto
import com.pheeeew.domain.model.collection.CollectionHistoryQuery
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
internal class CollectionApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findToday(): ApiResult<CollectionTodayDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$COLLECTION_PATH/today",
                kind = RequestKind.READ,
                monitoringEndpoint = "collection_today",
            ),
        ) { response -> response.body<CollectionTodayDto>() }

    suspend fun claim(
        date: String,
        state: String,
    ): ApiResult<CollectionCardReferenceDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = "$COLLECTION_PATH/claim",
                kind = RequestKind.WRITE,
                body = CollectionClaimRequestDto(date, state),
                replayAfterAuthentication = true,
                monitoringEndpoint = "collection_claim",
            ),
        ) { response -> response.body<CollectionCardReferenceDto>() }

    suspend fun findHistory(query: CollectionHistoryQuery): ApiResult<CollectionHistoryDto> {
        val queryParameters =
            buildMap {
                query.emotion?.let { put("emotion", it.name) }
                put("limit", query.limit.toString())
                query.cursor?.let { put("cursor", it) }
            }
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = COLLECTION_PATH,
                kind = RequestKind.READ,
                queryParameters = queryParameters,
                monitoringEndpoint = "collection_history",
            ),
        ) { response -> response.body<CollectionHistoryDto>() }
    }

    suspend fun findCard(cardId: String): ApiResult<CollectionCardDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$COLLECTION_PATH/$cardId",
                kind = RequestKind.READ,
                monitoringEndpoint = "collection_detail",
            ),
        ) { response -> response.body<CollectionCardDto>() }

    suspend fun findCalendar(month: String): ApiResult<CollectionCalendarDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$COLLECTION_PATH/calendar",
                kind = RequestKind.READ,
                queryParameters = mapOf("month" to month),
                monitoringEndpoint = "collection_calendar",
            ),
        ) { response -> response.body<CollectionCalendarDto>() }

    suspend fun findEmotions(
        cardId: String,
        cursor: String?,
    ): ApiResult<CollectionEmotionPageDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$COLLECTION_PATH/$cardId/emotions",
                kind = RequestKind.READ,
                queryParameters = buildMap { cursor?.let { put("cursor", it) } },
                monitoringEndpoint = "collection_card_emotions",
            ),
        ) { response -> response.body<CollectionEmotionPageDto>() }

    suspend fun findDaily(
        date: String,
        cursor: String?,
    ): ApiResult<CollectionDailyDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "$COLLECTION_PATH/daily",
                kind = RequestKind.READ,
                queryParameters = buildMap {
                    put("date", date)
                    cursor?.let { put("cursor", it) }
                },
                monitoringEndpoint = "collection_daily",
            ),
        ) { response -> response.body<CollectionDailyDto>() }

    private companion object {
        const val COLLECTION_PATH = "/api/v1/collection-cards"
    }
}
