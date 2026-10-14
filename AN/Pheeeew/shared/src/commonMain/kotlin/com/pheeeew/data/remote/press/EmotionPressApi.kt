package com.pheeeew.data.remote.press

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

internal class EmotionPressApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findMyToday(): ApiResult<MyDailyPressResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = PRESSES_PATH + "/me",
                kind = RequestKind.READ,
                queryParameters = mapOf(DAYS_AGO_PARAMETER to TODAY_DAYS_AGO),
                monitoringEndpoint = "emotion_press_me_daily",
            ),
        ) { response -> response.body<MyDailyPressResponseDto>() }

    suspend fun findAllToday(): ApiResult<AllDailyPressResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = PRESSES_PATH + "/total",
                kind = RequestKind.READ,
                queryParameters = mapOf(DAYS_AGO_PARAMETER to TODAY_DAYS_AGO),
                monitoringEndpoint = "emotion_press_all_daily",
            ),
        ) { response -> response.body<AllDailyPressResponseDto>() }

    suspend fun press(counts: Map<String, Int>): ApiResult<EmotionPressWriteResponseDto> =
        requests.execute(
            ApiRequest(
                method = HttpMethod.Post,
                path = PRESSES_PATH,
                kind = RequestKind.WRITE,
                body = EmotionPressWriteRequestDto(counts),
                replayAfterAuthentication = true,
                monitoringEndpoint = "emotion_press_write",
            ),
        ) { response -> response.body<EmotionPressWriteResponseDto>() }

    private companion object {
        const val PRESSES_PATH = "/api/v2/emotions/presses"
        const val DAYS_AGO_PARAMETER = "daysAgo"
        const val TODAY_DAYS_AGO = "0"
    }
}
