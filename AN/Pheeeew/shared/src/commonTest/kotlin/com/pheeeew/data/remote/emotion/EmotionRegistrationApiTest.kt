package com.pheeeew.data.remote.emotion

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionRegistrationApiTest {
    @Test
    fun noContentRegistrationSendsNoneAndParsesId() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals("/api/v1/emotions", request.url.encodedPath)
                            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                            assertEquals("NONE", body.getValue("contentType").jsonPrimitive.content)
                            assertEquals("FRUSTRATED", body.getValue("state").jsonPrimitive.content)
                            assertEquals(
                                "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                                body.getValue("requestId").jsonPrimitive.content,
                            )
                            respond(
                                """{"id":42}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val registration =
                    EmotionRegistration(
                        requestId = "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                        state = EmotionState.FRUSTRATED,
                        coordinate = GeoCoordinate(37.5665, 126.9780),
                        rotationDegrees = 0.0,
                        groupId = null,
                        content = EmotionRegistrationContent.None,
                    )
                assertEquals(
                    42L,
                    assertIs<ApiResult.Success<EmotionRegistrationResponseDto>>(
                        EmotionRegistrationApi(client.requests).register(registration.toRequestDto(null)!!),
                    ).value.id,
                )
            } finally {
                client.close()
            }
        }
}
