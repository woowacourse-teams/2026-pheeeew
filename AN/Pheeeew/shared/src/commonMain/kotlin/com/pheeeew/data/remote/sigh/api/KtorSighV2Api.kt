package com.pheeeew.data.remote.sigh.api

import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighPageResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV2PropertiesDto
import com.pheeeew.domain.model.sigh.SighBounds
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorSighV2Api(
    private val client: HttpClient,
) : SighV2Api {
    override suspend fun getFirstPage(bounds: SighBounds): SighPageResponseDto =
        executeRequest {
            client.get(SIGHS_PATH) {
                parameter("minLongitude", bounds.minLongitude)
                parameter("minLatitude", bounds.minLatitude)
                parameter("maxLongitude", bounds.maxLongitude)
                parameter("maxLatitude", bounds.maxLatitude)
            }
        }

    override suspend fun getNextPage(cursor: String): SighPageResponseDto {
        require(cursor.isNotBlank()) { "커서는 비어 있을 수 없습니다." }

        return executeRequest {
            client.get(SIGHS_PATH) {
                parameter("cursor", cursor)
            }
        }
    }

    override suspend fun getById(id: Long): SighFeatureDto<SighV2PropertiesDto> {
        require(id > 0) { "한숨 식별자는 양수여야 합니다." }

        return executeRequest {
            client.get("$SIGHS_PATH/$id")
        }
    }

    override suspend fun create(request: SighCreateV2RequestDto): SighFeatureDto<SighV2PropertiesDto> =
        executeRequest {
            client.post(SIGHS_PATH) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private companion object {
        const val SIGHS_PATH = "/api/v2/sighs"
    }
}
