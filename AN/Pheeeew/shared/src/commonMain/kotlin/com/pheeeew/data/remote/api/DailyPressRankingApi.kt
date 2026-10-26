package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.RequestKind
import com.pheeeew.core.notification.isNotificationDate
import com.pheeeew.data.remote.dto.PressRankingItemResponseDto
import com.pheeeew.domain.model.ranking.DailyPressRanking
import com.pheeeew.domain.model.ranking.PressRankingItem
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Historical notifications keep their requested day. This never falls back to the weekly API. */
class DailyPressRankingApi(
    private val requests: ApiRequestExecutor,
    private val sessionGeneration: () -> Long? = { 0L },
) {
    suspend fun find(date: String): ApiResult<DailyPressRanking> {
        require(isNotificationDate(date)) { "Invalid ranking date" }
        val generation =
            sessionGeneration()
                ?: return ApiResult.Failure(NetworkFailure.SessionUnavailable(MutationCertainty.NOT_A_MUTATION))
        return requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = "/api/v2/emotions/press-rankings/daily",
                kind = RequestKind.READ,
                queryParameters = mapOf("date" to date),
                monitoringEndpoint = "daily_press_ranking",
                expectedSessionGeneration = generation,
            ),
        ) { response ->
            val dto = response.body<DailyRankingDto>()
            check(sessionGeneration() == generation) { "Device session changed" }
            require(dto.date == date) { "Ranking date did not match the request" }
            val start = Instant.parse(dto.startAt)
            val end = Instant.parse(dto.endAt)
            val expectedStart = Instant.parse("${date}T00:00:00Z") - 9.hours
            require(start == expectedStart && end == expectedStart + 24.hours) { "Invalid KST ranking interval" }
            DailyPressRanking(dto.date, dto.startAt, dto.endAt, dto.items.map(::toDomain), dto.me?.let(::toDomain))
        }
    }

    private fun toDomain(item: PressRankingItemResponseDto): PressRankingItem {
        require(item.rank > 0 && item.nickname.isNotBlank() && item.pressCount >= 0) { "Invalid ranking item" }
        return PressRankingItem(item.rank, item.nickname, item.pressCount)
    }

    @Serializable
    private data class DailyRankingDto(
        val date: String,
        val startAt: String,
        val endAt: String,
        val items: List<PressRankingItemResponseDto>,
        val me: PressRankingItemResponseDto?,
    )
}
