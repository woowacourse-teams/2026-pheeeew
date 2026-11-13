package com.pheeeew.data.repository.press

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.press.retry.PressRetryPolicy
import com.pheeeew.data.remote.api.EmotionPressApi
import com.pheeeew.data.remote.datasource.PressApiSender
import com.pheeeew.data.remote.datasource.PressApiStatisticsDataSource
import com.pheeeew.data.repository.PressRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class PressHttpRetryTest {
    @Test
    fun `HTTP 429 twice preserves all input across reads reconnect and foreground until accepted`() =
        runTest {
            val counts = EmotionState.entries.associate { it.name to 0L }.toMutableMap()
            val inputTimes = mutableListOf<Long>()
            val bodies = mutableListOf<String>()
            val attempts = mutableListOf<Long>()
            val connection = MutableStateFlow(true)
            val client =
                createApiClient(
                    engine =
                        MockEngine.create {
                            dispatcher = StandardTestDispatcher(testScheduler)
                            addHandler { request ->
                                if (request.method == HttpMethod.Post) {
                                    val body = request.body.toByteArray().decodeToString()
                                    bodies += body
                                    attempts += testScheduler.currentTime
                                    if (attempts.size <= 2) {
                                        return@addHandler respond(
                                            """{"code":"RATE_LIMITED","message":"limited"}""",
                                            HttpStatusCode.TooManyRequests,
                                            headersOf("Retry-After", if (attempts.size == 1) "5" else "120"),
                                        )
                                    }
                                    val batch =
                                        Json
                                            .parseToJsonElement(body)
                                            .jsonObject
                                            .getValue("counts")
                                            .jsonObject
                                    batch.forEach { (emotion, count) ->
                                        counts[emotion] =
                                            counts.getValue(emotion) + count.jsonPrimitive.content.toLong()
                                    }
                                }
                                val total = counts.values.sum()
                                val totals = """"counts":${Json.encodeToString(counts)},"total":$total"""
                                val response =
                                    when {
                                        request.method == HttpMethod.Post -> "{$totals}"

                                        request.url.encodedPath.endsWith(
                                            "/me",
                                        ) -> """{"pressDate":"2026-10-28",$totals}"""

                                        else -> """{"pressDate":"2026-10-28","total":$total}"""
                                    }
                                respond(response, headers = headersOf("Content-Type", "application/json"))
                            }
                        },
                    config = ApiConfig("https://api.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val api = EmotionPressApi(client.requests)
                val repository =
                    PressRepositoryImpl(
                        PressApiStatisticsDataSource(api),
                        PressApiSender(api),
                        backgroundScope,
                        flushDelayMillis = PressDelayMillis.Zero,
                        retryPolicy = PressRetryPolicy(jitter = { 1.0 }),
                        connectivity = connection,
                        monitoring =
                            object : Monitoring by NoOpMonitoring {
                                override fun recordPressInput(occurredAt: Long) {
                                    inputTimes += occurredAt
                                }
                            },
                        nowMillis = { PressMonotonicMillis(testScheduler.currentTime) },
                    )
                repository.refreshToday()
                runCurrent()
                repeat(17) { repository.accept(EmotionState.ANGRY) }
                runCurrent()
                assertEquals(1, attempts.size)
                assertFalse(repository.state.value.isOutcomeUnknown)
                repeat(48) { repository.accept(EmotionState.FRUSTRATED) }
                repeat(4) {
                    repository.refreshToday(force = true)
                    runCurrent()
                }
                assertEquals(65L, repository.state.value.pendingPressCount)
                assertEquals(
                    65L,
                    repository.state.value.optimisticCounts.values
                        .sum(),
                )
                connection.value = false
                runCurrent()
                connection.value = true
                repeat(5) {
                    repository.onForeground()
                    repository.retryUnsent()
                }
                runCurrent()
                advanceTimeBy(4_999L)
                runCurrent()
                assertEquals(1, attempts.size)
                advanceTimeBy(1L)
                runCurrent()
                assertEquals(listOf(0L, 5_000L), attempts)
                assertEquals(bodies[0], bodies[1])
                assertEquals(65L, repository.state.value.pendingPressCount)
                repeat(4) {
                    repository.refreshToday(force = true)
                    repository.onForeground()
                    runCurrent()
                }
                advanceTimeBy(119_999L)
                runCurrent()
                assertEquals(2, attempts.size)
                assertEquals(65L, repository.state.value.pendingPressCount)
                advanceTimeBy(1L)
                runCurrent()
                assertEquals(125_000L, attempts[2])
                assertEquals(bodies[0], bodies[2])
                advanceTimeBy(1_000L)
                runCurrent()
                assertEquals(0L, repository.state.value.pendingPressCount)
                assertEquals(
                    65L,
                    repository.state.value.myToday
                        ?.total,
                )
                assertEquals(17L, counts.getValue("ANGRY"))
                assertEquals(48L, counts.getValue("FRUSTRATED"))
                assertEquals(5, attempts.size)
                assertEquals(65, inputTimes.size)
            } finally {
                client.close()
            }
        }
}
