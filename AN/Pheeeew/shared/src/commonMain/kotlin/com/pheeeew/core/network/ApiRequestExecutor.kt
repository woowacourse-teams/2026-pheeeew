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
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/** Executes authenticated API requests with at most one explicitly replayable AUTH-001 recovery. */
class ApiRequestExecutor internal constructor(
    private val client: HttpClient,
    private val config: ApiConfig,
    private val accessTokenProvider: AccessTokenProvider?,
    private val json: Json,
    private val observer: ApiResponseObserver? = null,
    private val attemptObserver: ApiAttemptObserver? = null,
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

    /** Sends bytes to an HTTPS signed URL without API authentication or URL rewriting. */
    suspend fun putSignedBinary(
        url: String,
        signedHeaders: Map<String, List<String>>,
        bytes: ByteArray,
        monitoringEndpoint: String? = null,
    ): ApiResult<Unit> {
        val contentTypeValue =
            signedHeaders.entries
                .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                ?.value
                ?.singleOrNull()
        val contentLength =
            signedHeaders.entries
                .firstOrNull { it.key.equals(HttpHeaders.ContentLength, ignoreCase = true) }
                ?.value
                ?.singleOrNull()
                ?.toLongOrNull()
        if (contentTypeValue == null || contentLength != bytes.size.toLong()) {
            return ApiResult.Failure(
                NetworkFailure.Contract(
                    reason = ContractFailureReason.MALFORMED_REQUEST_BODY,
                    mutationCertainty = MutationCertainty.NOT_APPLIED,
                ),
            )
        }
        val isHttps = runCatching { Url(url).protocol.name == "https" }.getOrDefault(false)
        if (!isHttps) {
            return ApiResult.Failure(
                NetworkFailure.Contract(
                    reason = ContractFailureReason.MALFORMED_REQUEST_BODY,
                    mutationCertainty = MutationCertainty.NOT_APPLIED,
                ),
            )
        }

        val attempt =
            monitoringEndpoint?.let { endpoint ->
                runCatching { attemptObserver?.started(endpoint, "PUT") }.getOrNull()
            }
        val started = TimeSource.Monotonic.markNow()

        fun finish(
            outcome: HttpAttemptOutcome,
            status: Int? = null,
        ) {
            runCatching { attempt?.completed(outcome, status, started.elapsedNow().inWholeMilliseconds) }
        }
        val response =
            try {
                client.request(url) {
                    method = HttpMethod.Put
                    setBody(
                        object : OutgoingContent.ByteArrayContent() {
                            override val contentType: ContentType = ContentType.parse(contentTypeValue)
                            override val contentLength: Long = bytes.size.toLong()

                            override fun bytes(): ByteArray = bytes
                        },
                    )
                    signedHeaders.forEach { (name, values) ->
                        if (!name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                            values.forEach { value -> header(name, value) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                finish(HttpAttemptOutcome.CANCELLED)
                throw cancelled
            } catch (timeout: HttpRequestTimeoutException) {
                finish(HttpAttemptOutcome.TIMEOUT)
                return signedUploadFailure(TransportFailureReason.TIMEOUT)
            } catch (timeout: ConnectTimeoutException) {
                finish(HttpAttemptOutcome.TIMEOUT)
                return signedUploadFailure(TransportFailureReason.TIMEOUT)
            } catch (timeout: SocketTimeoutException) {
                finish(HttpAttemptOutcome.TIMEOUT)
                return signedUploadFailure(TransportFailureReason.TIMEOUT)
            } catch (_: IOException) {
                finish(HttpAttemptOutcome.CONNECTION)
                return signedUploadFailure(TransportFailureReason.CONNECTION)
            } catch (exception: Exception) {
                finish(HttpAttemptOutcome.UNEXPECTED)
                return ApiResult.Failure(
                    NetworkFailure.Unexpected(
                        exceptionType = exception::class.simpleName ?: "Exception",
                        mutationCertainty = MutationCertainty.UNKNOWN,
                    ),
                )
            }

        finish(HttpAttemptOutcome.RESPONSE, response.status.value)
        if (response.status.value == SIGNED_UPLOAD_SUCCESS) return ApiResult.Success(Unit)
        return response.toHttpFailure(RequestKind.WRITE)
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
                    } catch (failure: SessionAccessException) {
                        return request.sessionProviderFailure(failure.details)
                    } catch (_: Exception) {
                        return request.sessionProviderFailure()
                    }
                accessToken ?: return request.sessionUnavailableFailure()
            }

        val first = transmit(request, token)
        val response = (first as? RequestExecutionResult.Response)?.value ?: return first
        val recovery = accessTokenProvider as? RecoverableAccessTokenProvider
        if (token == null || recovery == null || !request.replayAfterAuthentication ||
            response.status.value != 401
        ) {
            return first
        }
        val failure = response.toHttpFailure(request.kind)
        if ((failure.reason as? NetworkFailure.HttpStatus)?.error?.code != "AUTH-001") return first
        val renewed =
            try {
                recovery.recover(token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SessionAccessException) {
                return request.sessionProviderFailure(failure.details)
            } catch (_: Exception) {
                return request.sessionProviderFailure()
            }
        return transmit(request, renewed)
    }

    private suspend fun transmit(
        request: ApiRequest,
        token: AccessToken?,
    ): RequestExecutionResult {
        val attempt =
            request.monitoringEndpoint?.let { endpoint ->
                runCatching { attemptObserver?.started(endpoint, request.method.value) }.getOrNull()
            }
        val started = TimeSource.Monotonic.markNow()

        fun finish(
            outcome: HttpAttemptOutcome,
            status: Int? = null,
        ) {
            runCatching { attempt?.completed(outcome, status, started.elapsedNow().inWholeMilliseconds) }
        }
        val result =
            try {
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
                finish(HttpAttemptOutcome.CANCELLED)
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

        val outcome =
            when (result) {
                is RequestExecutionResult.Response -> {
                    HttpAttemptOutcome.RESPONSE
                }

                is RequestExecutionResult.Failure -> {
                    when (val reason = result.value.reason) {
                        is NetworkFailure.Transport -> {
                            if (reason.reason ==
                                TransportFailureReason.TIMEOUT
                            ) {
                                HttpAttemptOutcome.TIMEOUT
                            } else {
                                HttpAttemptOutcome.CONNECTION
                            }
                        }

                        is NetworkFailure.Contract -> {
                            HttpAttemptOutcome.CONTRACT
                        }

                        else -> {
                            HttpAttemptOutcome.UNEXPECTED
                        }
                    }
                }
            }
        finish(outcome, (result as? RequestExecutionResult.Response)?.value?.status?.value)
        try {
            observer?.completed((result as? RequestExecutionResult.Response)?.value?.status?.value)
        } catch (_: Exception) {
            // Observability must not change the request result.
        }
        return result
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

    private fun ApiRequest.sessionProviderFailure(
        details: SessionFailureDetails? = null,
    ): RequestExecutionResult.Failure =
        RequestExecutionResult.Failure(
            ApiResult.Failure(
                NetworkFailure.SessionProviderFailed(
                    kind.toMutationCertainty(unknownForWrite = false),
                    details,
                ),
            ),
        )

    private fun signedUploadFailure(reason: TransportFailureReason): ApiResult.Failure =
        ApiResult.Failure(
            NetworkFailure.Transport(
                reason = reason,
                mutationCertainty = MutationCertainty.UNKNOWN,
            ),
        )

    @Serializable
    private data class ApiErrorDto(
        val code: String? = null,
        val message: String? = null,
    )

    private companion object {
        const val NO_CONTENT = 204
        const val SIGNED_UPLOAD_SUCCESS = 200
    }
}
