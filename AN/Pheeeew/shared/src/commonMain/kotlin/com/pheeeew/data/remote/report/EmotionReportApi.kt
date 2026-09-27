package com.pheeeew.data.remote.report

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class EmotionReportApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun create(request: EmotionReportCreateRequestDto): ApiResult<EmotionReportResponseDto> =
        requests.execute(
            ApiRequest(
                HttpMethod.Post,
                PATH,
                RequestKind.WRITE,
                body = request,
                monitoringEndpoint = "emotion_report",
            ),
        ) { response -> response.body<EmotionReportResponseDto>() }

    private companion object {
        const val PATH = "/api/v2/reports"
    }
}
