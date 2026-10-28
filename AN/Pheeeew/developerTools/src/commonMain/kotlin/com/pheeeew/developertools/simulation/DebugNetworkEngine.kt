package com.pheeeew.developertools.simulation

import com.pheeeew.data.remote.api.ApiEndpointPaths
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.io.IOException

enum class NetworkRequestAvailability {
    AVAILABLE,
    OFFLINE,
}

/** The delegate receives no request until all Debug conditions have passed. */
@OptIn(InternalAPI::class)
class DebugNetworkEngine(
    private val delegate: HttpClientEngine,
    apiBaseUrl: String? = null,
) : HttpClientEngineBase("developer-tools") {
    private val apiOrigin = apiBaseUrl?.let(::Url)

    override val config: HttpClientEngineConfig get() = delegate.config
    override val supportedCapabilities = delegate.supportedCapabilities
    override val dispatcher get() = delegate.dispatcher

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val availability = ErrorSimulation.awaitAvailability()
        if (availability == NetworkRequestAvailability.OFFLINE) {
            throw IOException("Simulated connection failure")
        }
        val origin = apiOrigin
        if (origin == null || data.url.protocol != origin.protocol || data.url.host != origin.host ||
            data.url.port != origin.port
        ) {
            return delegate.execute(data)
        }
        val path = data.url.encodedPath
        if (data.method == HttpMethod.Get && path.matches(Regex("${ApiEndpointPaths.EMOTION_DETAIL}/[0-9]+")) &&
            ErrorSimulation.shouldFail(ErrorSimulationScenario.EmotionDetailNotFound)
        ) {
            return syntheticResponse(HttpStatusCode.NotFound)
        }
        if (data.method == HttpMethod.Post && path == ApiEndpointPaths.EMOTION_REGISTRATION &&
            ErrorSimulation.shouldFail(ErrorSimulationScenario.EmotionRegistrationUnavailable)
        ) {
            return syntheticResponse(HttpStatusCode.ServiceUnavailable)
        }

        val scenario =
            when {
                data.method == HttpMethod.Post && path == ApiEndpointPaths.AUDIO_UPLOAD_URL -> {
                    ErrorSimulationScenario.AudioUploadFailed
                }

                data.method == HttpMethod.Get && path == ApiEndpointPaths.EMOTION_MAP -> {
                    if (data.url.parameters["cursor"] ==
                        null
                    ) {
                        ErrorSimulationScenario.MapPins
                    } else {
                        ErrorSimulationScenario.MapPinsNextPage
                    }
                }

                data.method == HttpMethod.Get && path == ApiEndpointPaths.EMOTION_REGIONS -> {
                    ErrorSimulationScenario.RegionClusters
                }

                data.method == HttpMethod.Get && path.matches(Regex("${ApiEndpointPaths.EMOTION_DETAIL}/[0-9]+")) -> {
                    ErrorSimulationScenario.EmotionDetailUnavailable
                }

                data.method == HttpMethod.Delete && path.matches(Regex("${ApiEndpointPaths.EMOTIONS}/[0-9]+")) -> {
                    ErrorSimulationScenario.EmotionDeleteUnavailable
                }

                data.method == HttpMethod.Post &&
                    path in listOf(ApiEndpointPaths.EMOTION_BLOCK, ApiEndpointPaths.DEVICE_BLOCK)
                -> {
                    ErrorSimulationScenario.EmotionBlockUnavailable
                }

                data.method == HttpMethod.Post && path == ApiEndpointPaths.REPORT -> {
                    ErrorSimulationScenario.EmotionReportUnavailable
                }

                else -> {
                    null
                }
            }
        if (scenario != null && ErrorSimulation.shouldFail(scenario)) {
            throw IOException("Simulated connection failure")
        }
        return delegate.execute(data)
    }

    private suspend fun syntheticResponse(status: HttpStatusCode): HttpResponseData =
        HttpResponseData(
            status,
            GMTDate(),
            headersOf(HttpHeaders.ContentType, "application/json"),
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel("{}"),
            callContext(),
        )

    override fun close() {
        super.close()
        delegate.close()
    }
}
