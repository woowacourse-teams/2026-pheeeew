package com.pheeeew.data.remote.sigh.api

import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.data.remote.sigh.dto.SighCreateV1RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighMapResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV1PropertiesDto
import com.pheeeew.domain.model.sigh.SighBounds
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorSighV1Api(
    private val client: HttpClient,
) : SighV1Api {
    override suspend fun getSighs(bounds: SighBounds): SighMapResponseDto =
        executeRequest {
            client.get(SIGHS_PATH) {
                parameter("minLongitude", bounds.minLongitude)
                parameter("minLatitude", bounds.minLatitude)
                parameter("maxLongitude", bounds.maxLongitude)
                parameter("maxLatitude", bounds.maxLatitude)
            }
        }

    override suspend fun registerSigh(request: SighCreateV1RequestDto): SighFeatureDto<SighV1PropertiesDto> =
        executeRequest {
            client.post(SIGHS_PATH) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private companion object {
        const val SIGHS_PATH = "/api/v1/sighs"
    }
}
