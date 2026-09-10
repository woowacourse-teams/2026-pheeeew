package com.pheeeew.data.remote.common

import com.pheeeew.core.network.toApiException
import com.pheeeew.data.remote.common.dto.ErrorResponseDto
import com.pheeeew.domain.exception.ApiException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

internal suspend inline fun <reified T> executeRequest(block: suspend () -> HttpResponse): T =
    try {
        val response = block()
        response.throwIfFailed()
        response.body()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: ApiException) {
        throw exception
    } catch (exception: Throwable) {
        throw exception.toApiException()
    }

@PublishedApi
internal suspend fun HttpResponse.throwIfFailed() {
    if (status.value in 200..299) return

    val error =
        try {
            body<ErrorResponseDto>()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    val code = error?.code ?: "HTTP_${status.value}"
    val message = error?.message ?: "API 요청에 실패했습니다."

    throw when (status) {
        HttpStatusCode.BadRequest -> ApiException.InvalidRequest(code, message)
        HttpStatusCode.Unauthorized -> ApiException.Unauthorized(code, message)
        HttpStatusCode.Forbidden -> ApiException.Forbidden(code, message)
        HttpStatusCode.NotFound -> ApiException.NotFound(code, message)
        HttpStatusCode.Conflict -> ApiException.Conflict(code, message)
        else -> ApiException.Unknown(code, message)
    }
}
