package com.pheeeew.core.network

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
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

    /** Executes a long-lived response incrementally, applying the request timeout only until headers arrive. */
    suspend fun <T> executeStreaming(
        request: ApiRequest,
        decodeSuccessBody: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val isPressRaceSse = request.monitoringEndpoint == "press_race_sse_stream"
        val token =
            if (request.authentication == AuthenticationRequirement.NONE) {
                null
            } else {
                val provider = accessTokenProvider ?: return request.sessionUnavailableFailure().value
                if (isPressRaceSse) println("[PressRaceReceive] mode=SSE event=token_lookup_started")
                val accessToken =
                    try {
                        provider.accessToken().also { token ->
                            if (isPressRaceSse) {
                                println(
                                    "[PressRaceReceive] mode=SSE event=token_lookup_completed " +
                                        "available=${token != null}",
                                )
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: SessionAccessException) {
                        return request.sessionProviderFailure(failure.details).value
                    } catch (_: Exception) {
                        return request.sessionProviderFailure().value
                    }
                accessToken ?: return request.sessionUnavailableFailure().value
            }

        val first = transmitStreaming(request, token, decodeSuccessBody)
        val failure = (first as? ApiResult.Failure)?.reason as? NetworkFailure.HttpStatus ?: return first
        val recovery = accessTokenProvider as? RecoverableAccessTokenProvider
        if (token == null || recovery == null || failure.statusCode != 401) return first
        val code = failure.error?.code
        if (code == "DEVICE-004") {
            return try {
                recovery.deviceUnavailable(token)
                first
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (sessionFailure: SessionAccessException) {
                request.sessionProviderFailure(sessionFailure.details).value
            } catch (_: Exception) {
                request.sessionProviderFailure().value
            }
        }
        if (!request.replayAfterAuthentication || code != "AUTH-001") return first
        val renewed =
            try {
                recovery.recover(token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (sessionFailure: SessionAccessException) {
                return request.sessionProviderFailure(sessionFailure.details).value
            } catch (_: Exception) {
                return request.sessionProviderFailure().value
            }
        val replayed = transmitStreaming(request, renewed, decodeSuccessBody)
        val replayFailure = (replayed as? ApiResult.Failure)?.reason as? NetworkFailure.HttpStatus
        if (replayFailure?.statusCode == 401 && replayFailure.error?.code == "DEVICE-004") {
            try {
                recovery.deviceUnavailable(renewed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (sessionFailure: SessionAccessException) {
                return request.sessionProviderFailure(sessionFailure.details).value
            } catch (_: Exception) {
                return request.sessionProviderFailure().value
            }
        }
        return replayed
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
        val isPressRaceSse = request.monitoringEndpoint == "press_race_sse_stream"
        val token =
            if (request.authentication == AuthenticationRequirement.NONE) {
                null
            } else {
                val provider = accessTokenProvider ?: return request.sessionUnavailableFailure()
                if (isPressRaceSse) println("[PressRaceReceive] mode=SSE event=token_lookup_started")
                val accessToken =
                    try {
                        provider.accessToken().also { accessToken ->
                            if (isPressRaceSse) {
                                println("[PressRaceReceive] mode=SSE event=token_lookup_completed available=${accessToken != null}")
                            }
                        }
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
        if (token == null || recovery == null || response.status.value != 401) return first
        val failure = response.toHttpFailure(request.kind)
        val code = (failure.reason as? NetworkFailure.HttpStatus)?.error?.code
        if (code == "DEVICE-004") {
            return invalidateDevice(request, recovery, token) ?: first
        }
        if (!request.replayAfterAuthentication || code != "AUTH-001") return first
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
        val replayed = transmit(request, renewed)
        val replayResponse = (replayed as? RequestExecutionResult.Response)?.value ?: return replayed
        if (replayResponse.status.value == 401 &&
            (replayResponse.toHttpFailure(request.kind).reason as? NetworkFailure.HttpStatus)?.error?.code ==
            "DEVICE-004"
        ) {
            return invalidateDevice(request, recovery, renewed) ?: replayed
        }
        return replayed
    }

    private suspend fun invalidateDevice(
        request: ApiRequest,
        recovery: RecoverableAccessTokenProvider,
        token: AccessToken,
    ): RequestExecutionResult.Failure? =
        try {
            recovery.deviceUnavailable(token)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SessionAccessException) {
            request.sessionProviderFailure(failure.details)
        } catch (_: Exception) {
            request.sessionProviderFailure()
        }

    private suspend fun transmit(
        request: ApiRequest,
        token: AccessToken?,
    ): RequestExecutionResult {
        if (request.expectedSessionGeneration != null && token?.generation != request.expectedSessionGeneration) {
            return request.sessionUnavailableFailure()
        }
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
                if (request.monitoringEndpoint == "press_race_sse_stream") {
                    println("[PressRaceReceive] mode=SSE event=transport_started")
                }
                RequestExecutionResult.Response(
                    client.request(
                        "${config.baseUrl.trimEnd('/')}/${request.path.trimStart('/')}",
                    ) {
                        method = request.method
                        request.queryParameters.forEach { (name, value) -> parameter(name, value) }
                        token?.let { header(HttpHeaders.Authorization, "Bearer ${it.value}") }
                        request.accept?.let { header(HttpHeaders.Accept, it) }
                        request.timeoutOverride?.let { override ->
                            timeout {
                                requestTimeoutMillis = override.requestMillis
                                socketTimeoutMillis = override.socketMillis
                            }
                        }
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
        if (request.monitoringEndpoint == "press_race_sse_stream") {
            val responseStatus = (result as? RequestExecutionResult.Response)?.value?.status?.value
            println(
                "[PressRaceReceive] mode=SSE event=http_response outcome=${outcome.name} " +
                    "status=${responseStatus ?: "NONE"} durationMs=${started.elapsedNow().inWholeMilliseconds}",
            )
        }
        try {
            observer?.completed((result as? RequestExecutionResult.Response)?.value?.status?.value)
        } catch (_: Exception) {
            // Observability must not change the request result.
        }
        return result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun <T> transmitStreaming(
        request: ApiRequest,
        token: AccessToken?,
        decodeSuccessBody: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        if (request.expectedSessionGeneration != null && token?.generation != request.expectedSessionGeneration) {
            return request.sessionUnavailableFailure().value
        }
        val attempt =
            request.monitoringEndpoint?.let { endpoint ->
                runCatching { attemptObserver?.started(endpoint, request.method.value) }.getOrNull()
            }
        val started = TimeSource.Monotonic.markNow()
        val isPressRaceSse = request.monitoringEndpoint == "press_race_sse_stream"
        var responseStatus: Int? = null
        var responseObserved = false
        var responseLogged = false

        fun observeResponse(status: Int?) {
            if (responseObserved) return
            responseObserved = true
            try {
                observer?.completed(status)
            } catch (_: Exception) {
                // Observability must not change the request result.
            }
        }

        fun finish(outcome: HttpAttemptOutcome) {
            runCatching { attempt?.completed(outcome, responseStatus, started.elapsedNow().inWholeMilliseconds) }
            if (isPressRaceSse && !responseLogged) {
                responseLogged = true
                println(
                    "[PressRaceReceive] mode=SSE event=http_response outcome=${outcome.name} " +
                        "status=${responseStatus ?: "NONE"} durationMs=${started.elapsedNow().inWholeMilliseconds}",
                )
            }
        }

        val result =
            try {
                if (isPressRaceSse) println("[PressRaceReceive] mode=SSE event=transport_started")
                val streamed =
                    supervisorScope {
                        val headersReceived = CompletableDeferred<Unit>()
                        val execution =
                            async(start = CoroutineStart.UNDISPATCHED) {
                                client.prepareRequest(
                                    "${config.baseUrl.trimEnd('/')}/${request.path.trimStart('/')}",
                                ) {
                                    method = request.method
                                    request.queryParameters.forEach { (name, value) -> parameter(name, value) }
                                    token?.let { header(HttpHeaders.Authorization, "Bearer ${it.value}") }
                                    request.accept?.let { header(HttpHeaders.Accept, it) }
                                    timeout {
                                        // Ktor's request timeout is tied to executionContext and would include
                                        // the complete streaming response. The separate watchdog below ends at headers.
                                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                                        socketTimeoutMillis =
                                            request.timeoutOverride?.socketMillis ?: config.timeouts.socketMillis
                                    }
                                    request.body?.let {
                                        contentType(ContentType.Application.Json)
                                        setBody(it)
                                    }
                                }.execute { response ->
                                    responseStatus = response.status.value
                                    headersReceived.complete(Unit)
                                    observeResponse(response.status.value)
                                    if (isPressRaceSse && !responseLogged) {
                                        responseLogged = true
                                        println(
                                            "[PressRaceReceive] mode=SSE event=http_response outcome=RESPONSE " +
                                                "status=${responseStatus} " +
                                                "durationMs=${started.elapsedNow().inWholeMilliseconds}",
                                        )
                                    }
                                    when {
                                        !response.status.isSuccess() -> response.toHttpFailure(request.kind)
                                        response.status.value == NO_CONTENT -> {
                                            ApiResult.Failure(
                                                NetworkFailure.Contract(
                                                    reason = ContractFailureReason.EMPTY_SUCCESS_BODY,
                                                        mutationCertainty =
                                                            request.kind.toMutationCertainty(unknownForWrite = true),
                                                ),
                                            )
                                        }
                                        else -> {
                                            try {
                                                ApiResult.Success(decodeSuccessBody(response))
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (timeout: HttpRequestTimeoutException) {
                                                streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
                                            } catch (timeout: ConnectTimeoutException) {
                                                streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
                                            } catch (timeout: SocketTimeoutException) {
                                                streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
                                            } catch (_: IOException) {
                                                streamingTransportFailure(request, TransportFailureReason.CONNECTION)
                                            } catch (_: SerializationException) {
                                                streamingContractFailure(request)
                                            } catch (_: Exception) {
                                                streamingContractFailure(request)
                                            }
                                        }
                                    }
                                }
                            }
                        try {
                            val waitResult =
                                select<StreamingWaitResult<ApiResult<T>>> {
                                    headersReceived.onAwait { StreamingWaitResult.Headers }
                                    execution.onAwait { value -> StreamingWaitResult.Completed(value) }
                                    onTimeout(config.timeouts.requestMillis) {
                                        when {
                                            headersReceived.isCompleted -> StreamingWaitResult.Headers
                                            execution.isCompleted -> StreamingWaitResult.Completed(execution.await())
                                            else -> StreamingWaitResult.TimedOut
                                        }
                                    }
                                }
                            when (waitResult) {
                                StreamingWaitResult.Headers -> execution.await()
                                is StreamingWaitResult.Completed -> waitResult.value
                                StreamingWaitResult.TimedOut -> {
                                    // Recheck at the timer boundary so a completion already observable there wins.
                                    when {
                                        headersReceived.isCompleted -> execution.await()
                                        execution.isCompleted -> execution.await()
                                        else -> {
                                            execution.cancelAndJoin()
                                            throw HeaderArrivalTimeoutException()
                                        }
                                    }
                                }
                            }
                        } finally {
                            // Parent cancellation after headers must also leave the execute block before returning.
                            if (!execution.isCompleted) execution.cancelAndJoin()
                        }
                    }
                streamed
            } catch (cancelled: CancellationException) {
                finish(HttpAttemptOutcome.CANCELLED)
                throw cancelled
            } catch (_: HeaderArrivalTimeoutException) {
                observeResponse(null)
                finish(HttpAttemptOutcome.TIMEOUT)
                return streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
            } catch (timeout: HttpRequestTimeoutException) {
                observeResponse(null)
                finish(HttpAttemptOutcome.TIMEOUT)
                return streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
            } catch (timeout: ConnectTimeoutException) {
                observeResponse(null)
                finish(HttpAttemptOutcome.TIMEOUT)
                return streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
            } catch (timeout: SocketTimeoutException) {
                finish(HttpAttemptOutcome.TIMEOUT)
                if (!responseObserved) observeResponse(null)
                return streamingTransportFailure(request, TransportFailureReason.TIMEOUT)
            } catch (_: SerializationException) {
                if (!responseObserved) observeResponse(null)
                finish(HttpAttemptOutcome.CONTRACT)
                return streamingContractFailure(request)
            } catch (_: IOException) {
                if (!responseObserved) observeResponse(null)
                finish(HttpAttemptOutcome.CONNECTION)
                return streamingTransportFailure(request, TransportFailureReason.CONNECTION)
            } catch (exception: Exception) {
                if (!responseObserved) observeResponse(null)
                finish(HttpAttemptOutcome.UNEXPECTED)
                return ApiResult.Failure(
                    NetworkFailure.Unexpected(
                        exceptionType = exception::class.simpleName ?: "Exception",
                        mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
                    ),
                )
            }

        val outcome =
            when (result) {
                is ApiResult.Success<*> -> HttpAttemptOutcome.RESPONSE
                is ApiResult.Failure ->
                    when (val reason = result.reason) {
                        is NetworkFailure.Transport -> if (reason.reason == TransportFailureReason.TIMEOUT) {
                            HttpAttemptOutcome.TIMEOUT
                        } else {
                            HttpAttemptOutcome.CONNECTION
                        }
                        is NetworkFailure.Contract -> HttpAttemptOutcome.CONTRACT
                        is NetworkFailure.HttpStatus -> HttpAttemptOutcome.RESPONSE
                        else -> HttpAttemptOutcome.UNEXPECTED
                    }
            }
        finish(outcome)
        if (!responseObserved) observeResponse(responseStatus)
        return result
    }

    private fun <T> streamingTransportFailure(
        request: ApiRequest,
        reason: TransportFailureReason,
    ): ApiResult<T> =
        ApiResult.Failure(
            NetworkFailure.Transport(
                reason = reason,
                mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
            ),
        )

    private fun <T> streamingContractFailure(request: ApiRequest): ApiResult<T> =
        ApiResult.Failure(
            NetworkFailure.Contract(
                reason = ContractFailureReason.MALFORMED_SUCCESS_BODY,
                mutationCertainty = request.kind.toMutationCertainty(unknownForWrite = true),
            ),
        )

    private sealed interface StreamingWaitResult<out T> {
        data object Headers : StreamingWaitResult<Nothing>

        data class Completed<T>(val value: T) : StreamingWaitResult<T>

        data object TimedOut : StreamingWaitResult<Nothing>
    }

    private class HeaderArrivalTimeoutException : Exception()

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
