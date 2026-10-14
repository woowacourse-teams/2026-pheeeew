package com.pheeeew.data.remote.audio

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.emotion.EmotionRegistrationApi
import com.pheeeew.data.remote.emotion.toRequestDto
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AudioUploadApiTest {
    @Test
    fun requestsUrlUploadsRawBytesWithoutBearerAndRegistersReturnedUploadId() =
        runTest {
            val audioBytes = byteArrayOf(0, 1, 2, 3, 4)
            val uploadUrl = "https://storage.example.test/audio/object?signature=opaque-value"
            var requestIndex = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            when (requestIndex++) {
                                0 -> {
                                    assertEquals("/api/v1/audio-uploads", request.url.encodedPath)
                                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                                    val body =
                                        Json
                                            .parseToJsonElement(
                                                request.body.toByteArray().decodeToString(),
                                            ).jsonObject
                                    assertEquals("audio/mp4", body.getValue("contentType").jsonPrimitive.content)
                                    assertEquals(
                                        5L,
                                        body
                                            .getValue("contentLength")
                                            .jsonPrimitive.content
                                            .toLong(),
                                    )
                                    respond(
                                        """{"uploadId":"upload-123","uploadUrl":"$uploadUrl","expiresAt":"2026-09-28T09:05:00Z","headers":{"content-type":["audio/mp4"],"content-length":["5"],"if-none-match":["*"]}}""",
                                        status = HttpStatusCode.Created,
                                        headers =
                                            headersOf(
                                                HttpHeaders.ContentType,
                                                ContentType.Application.Json.toString(),
                                            ),
                                    )
                                }

                                1 -> {
                                    assertEquals(HttpMethod.Put, request.method)
                                    assertEquals("storage.example.test", request.url.host)
                                    assertEquals("/audio/object", request.url.encodedPath)
                                    assertEquals("signature=opaque-value", request.url.encodedQuery)
                                    assertNull(request.headers[HttpHeaders.Authorization])
                                    val outgoingContent: OutgoingContent = request.body
                                    assertEquals("audio/mp4", outgoingContent.contentType.toString())
                                    assertEquals(5L, outgoingContent.contentLength)
                                    assertEquals("5", request.headers[HttpHeaders.ContentLength])
                                    assertEquals("*", request.headers["If-None-Match"])
                                    assertEquals(audioBytes.toList(), request.body.toByteArray().toList())
                                    respond("", status = HttpStatusCode.OK)
                                }

                                2 -> {
                                    assertEquals("/api/v2/emotions", request.url.encodedPath)
                                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                                    val body =
                                        Json
                                            .parseToJsonElement(
                                                request.body.toByteArray().decodeToString(),
                                            ).jsonObject
                                    assertEquals("AUDIO", body.getValue("contentType").jsonPrimitive.content)
                                    assertEquals("upload-123", body.getValue("audioUploadId").jsonPrimitive.content)
                                    assertEquals("request-123", body.getValue("requestId").jsonPrimitive.content)
                                    respond(
                                        """{"id":42}""",
                                        status = HttpStatusCode.OK,
                                        headers =
                                            headersOf(
                                                HttpHeaders.ContentType,
                                                ContentType.Application.Json.toString(),
                                            ),
                                    )
                                }

                                else -> {
                                    error("Unexpected request")
                                }
                            }
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val audioUploadApi = AudioUploadApi(client.requests)
                val upload =
                    assertIs<ApiResult.Success<AudioUploadUrlResponseDto>>(
                        audioUploadApi.requestUploadUrl(audioBytes.size.toLong()),
                    ).value
                assertEquals("upload-123", upload.uploadId)
                assertEquals(setOf("content-type", "content-length", "if-none-match"), upload.headers.keys)
                assertIs<ApiResult.Success<Unit>>(audioUploadApi.upload(upload, audioBytes))

                val registrationApi = EmotionRegistrationApi(client.requests)
                val result =
                    registrationApi.register(
                        EmotionRegistration(
                            requestId = "request-123",
                            state = EmotionState.EXHAUSTED,
                            coordinate = GeoCoordinate(37.5669, 126.9774),
                            rotationDegrees = 35.5,
                            content = EmotionRegistrationContent.Audio("unused/path.m4a"),
                            groupId = null,
                        ).toRequestDto(upload.uploadId)!!,
                    )
                assertEquals(
                    42L,
                    assertIs<ApiResult.Success<com.pheeeew.data.remote.emotion.EmotionRegistrationResponseDto>>(
                        result,
                    ).value.id,
                )
                assertEquals(3, requestIndex)
            } finally {
                client.close()
            }
        }
}
