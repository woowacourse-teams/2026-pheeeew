package com.pheeeew.core.network

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Executes authenticated API requests without automatic retries. */
class ApiRequestExecutor internal constructor(
    private val client: HttpClient,
    private val config: ApiConfig,
    private val accessTokenProvider: AccessTokenProvider?,
    private val json: Json,
) {
    /** Decodes a non-empty successful response body with the caller's serializable response DTO. */
    suspend fun <T> execute(
        request: ApiRequest,
        decodeSuccessBody: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val response =
            when (val result = executeRequest(request)) {
                is RequestExecutionResult.Response -> result.value
                is RequestExecutionResult.Failure -> return result.value
            }
        if (!response.status.isSuccess()) return response.toHttpFailure(request.kind)
        if (response.status.value == NO_CONTENT) {
            return ApiResult.Failure(
                NetworkFailure.Contract(
                    reason = ContractFailureReason.EMPTY_SUCCESS_BODY,
                    mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                ),
            )
        }

        return try {
            ApiResult.Success(decodeSuccessBody(response))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ApiResult.Failure(
                NetworkFailure.Contract(
                    reason = ContractFailureReason.MALFORMED_SUCCESS_BODY,
                    mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                ),
            )
        }
    }

    /** Handles endpoints whose documented successful response is HTTP 204 with no body. */
    suspend fun executeNoContent(request: ApiRequest): ApiResult<Unit> {
        val response =
            when (val result = executeRequest(request)) {
                is RequestExecutionResult.Response -> result.value
                is RequestExecutionResult.Failure -> return result.value
            }
        if (!response.status.isSuccess()) return response.toHttpFailure(request.kind)
        if (response.status.value == NO_CONTENT) return ApiResult.Success(Unit)
        return ApiResult.Failure(
            NetworkFailure.Contract(
                reason = ContractFailureReason.UNEXPECTED_EMPTY_RESPONSE,
                mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
            ),
        )
    }

    private suspend fun executeRequest(request: ApiRequest): RequestExecutionResult {
        val token =
            if (request.authentication == AuthenticationRequirement.NONE) {
                null
            } else {
                val provider = accessTokenProvider ?: return request.sessionUnavailableFailure()
                val accessToken =
                    try {
                        provider.accessToken()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        return request.sessionProviderFailure()
                    }
                accessToken ?: return request.sessionUnavailableFailure()
            }

        return try {
            RequestExecutionResult.Response(
                client.request(
                    "${config.baseUrl.trimEnd('/')}/${request.path.trimStart('/')}",
                ) {
                    method = request.method
                    request.queryParameters.forEach { (name, value) -> parameter(name, value) }
                    token?.let { header(HttpHeaders.Authorization, "Bearer ${it.value}") }
                    request.body?.let {
                        contentType(ContentType.Application.Json)
                        setBody(it)
                    }
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: HttpRequestTimeoutException) {
            RequestExecutionResult.Failure(
                ApiResult.Failure(
                    NetworkFailure.Transport(
                        reason = TransportFailureReason.TIMEOUT,
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                    ),
                ),
            )
        } catch (timeout: ConnectTimeoutException) {
            RequestExecutionResult.Failure(
                ApiResult.Failure(
                    NetworkFailure.Transport(
                        reason = TransportFailureReason.TIMEOUT,
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                    ),
                ),
            )
        } catch (timeout: SocketTimeoutException) {
            RequestExecutionResult.Failure(
                ApiResult.Failure(
                    NetworkFailure.Transport(
                        reason = TransportFailureReason.TIMEOUT,
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                    ),
                ),
            )
        } catch (_: SerializationException) {
            RequestExecutionResult.Failure(
                ApiResult.Failure(
                    NetworkFailure.Contract(
                        reason = ContractFailureReason.MALFORMED_REQUEST_BODY,
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = false),
                    ),
                ),
            )
        } catch (_: IOException) {
            RequestExecutionResult.Failure(
                ApiResult.Failure(
                    NetworkFailure.Transport(
                        reason = TransportFailureReason.CONNECTION,
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                    ),
                ),
            )
        } catch (exception: Exception) {
            RequestExecutionResult.Failure(
                ApiResult.Failure(
                    NetworkFailure.Unexpected(
                        exceptionType = exception::class.simpleName ?: "Exception",
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                    ),
                ),
            )
        }
    }

    private sealed interface RequestExecutionResult {
        data class Response(
            val value: HttpResponse,
        ) : RequestExecutionResult

        data class Failure(
            val value: ApiResult.Failure,
        ) : RequestExecutionResult
    }

    private suspend fun HttpResponse.toHttpFailure(kind: RequestKind): ApiResult.Failure {
        val error =
            try {
                json.decodeFromString<ApiErrorDto>(bodyAsText()).let { ApiError(it.code, it.message) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        return ApiResult.Failure(
            NetworkFailure.HttpStatus(
                statusCode = status.value,
                error = error,
                retryAfter = headers[HttpHeaders.RetryAfter],
                mutationCertainty =
                    if (kind == RequestKind.READ) {
                        MutationCertainty.NOT_A_MUTATION
                    } else {
                        MutationCertainty.UNKNOWN
                    },
            ),
        )
    }

    private fun RequestKind.toMutationCertainty(unknownForWrite: Boolean): MutationCertainty =
        when (this) {
            RequestKind.READ -> MutationCertainty.NOT_A_MUTATION
            RequestKind.WRITE -> if (unknownForWrite) MutationCertainty.UNKNOWN else MutationCertainty.NOT_APPLIED
        }

    private fun ApiRequest.sessionUnavailableFailure(): RequestExecutionResult.Failure =
        RequestExecutionResult.Failure(
            ApiResult.Failure(
                NetworkFailure.SessionUnavailable(
                    kind.toMutationCertainty(unknownForWrite = false),
                ),
            ),
        )

    private fun ApiRequest.sessionProviderFailure(): RequestExecutionResult.Failure =
        RequestExecutionResult.Failure(
            ApiResult.Failure(
                NetworkFailure.SessionProviderFailed(
                    kind.toMutationCertainty(unknownForWrite = false),
                ),
            ),
        )

    @Serializable
    private data class ApiErrorDto(
        val code: String? = null,
        val message: String? = null,
    )

    private companion object {
        const val NO_CONTENT = 204
    }
}
