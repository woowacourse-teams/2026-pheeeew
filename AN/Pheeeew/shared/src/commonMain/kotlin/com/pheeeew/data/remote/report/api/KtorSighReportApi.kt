package com.pheeeew.data.remote.report.api

import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.data.remote.report.dto.SighReportResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorSighReportApi(
    private val client: HttpClient,
) : SighReportApi {
    override suspend fun create(request: SighReportCreateRequestDto): SighReportResponseDto =
        executeRequest {
            client.post(REPORTS_PATH) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private companion object {
        const val REPORTS_PATH = "/api/v2/reports"
    }
}
