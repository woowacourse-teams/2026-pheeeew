package com.pheeeew.data.repository

import com.pheeeew.core.monitoring.ActivityType
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.audio.AudioUploadApi
import com.pheeeew.data.remote.emotion.EmotionRegistrationApi
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.model.emotion.EmotionState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmotionActivityTest {
    @Test
    fun `only valid saved record reports original timestamp and telemetry failure cannot fail save`() =
        runTest {
            var id = 0
            val calls = mutableListOf<Long>()
            val monitoring =
                object : Monitoring by NoOpMonitoring {
                    override fun recordSuccessfulActivity(
                        type: ActivityType,
                        occurredAt: Long,
                    ) {
                        assertEquals(ActivityType.EMOTION_RECORD, type)
                        calls += occurredAt
                        error("analytics storage failed")
                    }
                }
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            respond(
                                "{\"id\":$id}",
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                val repository =
                    EmotionRegistrationRepositoryImpl(
                        EmotionRegistrationApi(client.requests),
                        AudioUploadApi(client.requests),
                        monitoring,
                    )
                val input =
                    EmotionRegistration(
                        "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
                        EmotionState.ANGRY,
                        GeoCoordinate(37.0, 127.0),
                        0.0,
                        null,
                        EmotionRegistrationContent.Memo("기록"),
                        occurredAt = 1234L,
                    )
                assertIs<EmotionRegistrationResult.Unavailable>(repository.register(input))
                assertEquals(emptyList(), calls)
                id = 42
                assertEquals(42L, assertIs<EmotionRegistrationResult.Success>(repository.register(input)).id)
                assertEquals(listOf(1234L), calls)
            } finally {
                client.close()
            }
        }
}
