package com.pheeeew.data.remote.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.EmotionReportCreateRequestDto
import com.pheeeew.data.remote.dto.EmotionReportResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
class ReportApi(
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
                replayAfterAuthentication = true,
            ),
        ) { response -> response.body<EmotionReportResponseDto>() }

    private companion object {
        const val PATH = ApiEndpointPaths.REPORT
    }
}
