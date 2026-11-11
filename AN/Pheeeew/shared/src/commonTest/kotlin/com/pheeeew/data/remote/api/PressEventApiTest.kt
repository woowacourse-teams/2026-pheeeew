package com.pheeeew.data.remote.api

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.repository.PressEventRepositoryImpl
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.pressevent.PressEventAttempt
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.domain.model.pressevent.PressEventRankingPage
import com.pheeeew.domain.repository.PressEventFailure
import com.pheeeew.domain.repository.PressEventResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PressEventApiTest {
    @Test
    fun networkTimeoutAllowsRetryWithSamePayload() =
        runTest {
            val bodies = mutableListOf<String>()
            val client =
                client(
                    MockEngine { request ->
                        bodies += request.body.toByteArray().decodeToString()
                        if (bodies.size == 1) throw HttpRequestTimeoutException(request.url.toString(), 1_000)
                        respond(ATTEMPT, headers = headers)
                    },
                )
            try {
                val repository = PressEventRepositoryImpl(PressEventApi(client.requests))
                val attempt = PressEventAttempt(ID, mapOf(EmotionState.FRUSTRATED to 5))
                assertEquals(
                    PressEventFailure.Retryable,
                    assertIs<PressEventResult.Failure>(repository.submit("1", attempt, 1)).reason,
                )
                assertIs<PressEventResult.Success<*>>(repository.submit("1", attempt, 1))
                assertEquals(2, bodies.size)
                assertEquals(bodies[0], bodies[1])
            } finally {
                client.close()
            }
        }

    @Test
    fun retriesWithSameFrozenFiveEmotionPayloadAndBearerAndMapsReplayedResult() =
        runTest {
            val bodies = mutableListOf<String>()
            val client =
                client(
                    MockEngine { request ->
                        assertEquals("/api/v1/press-events/1/attempts", request.url.encodedPath)
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        bodies += request.body.toByteArray().decodeToString()
                        if (bodies.size == 1) {
                            respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                        } else {
                            respond(ATTEMPT, headers = headers)
                        }
                    },
                )
            try {
                val repository = PressEventRepositoryImpl(PressEventApi(client.requests))
                val request = PressEventAttempt(ID, mapOf(EmotionState.FRUSTRATED to 5))
                assertEquals(
                    PressEventFailure.Retryable,
                    assertIs<PressEventResult.Failure>(repository.submit("1", request, 1)).reason,
                )
                val response = assertIs<PressEventResult.Success<*>>(repository.submit("1", request, 1))
                val result = response.value as PressEventAttemptResult
                assertTrue(result.replayed)
                assertEquals(3L, result.rank)
                assertEquals(156L, result.bestScore)
                assertEquals(bodies[0], bodies[1])
                val body = Json.parseToJsonElement(bodies[0]).jsonObject
                assertEquals(ID, body.getValue("requestId").jsonPrimitive.content)
                val counts = body.getValue("counts").jsonObject
                assertEquals(EmotionState.entries.map { it.name }.toSet(), counts.keys)
                assertEquals("0", counts.getValue("ANGRY").jsonPrimitive.content)
            } finally {
                client.close()
            }
        }

    @Test
    fun cursorIsAbsentOnFirstPageAndPassedUnchangedOnNextPageWithMeOutsideItems() =
        runTest {
            var calls = 0
            val client =
                client(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/api/v1/press-events/1/rankings", request.url.encodedPath)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals("1", request.url.parameters["limit"])
                        assertEquals(if (calls++ == 0) null else "opaque +/cursor", request.url.parameters["cursor"])
                        respond(RANKINGS, headers = headers)
                    },
                )
            try {
                val api = PressEventApi(client.requests)
                assertIs<ApiResult.Success<*>>(api.rankings("1", 1, 1, null))
                val repository = PressEventRepositoryImpl(api)
                val page =
                    assertIs<PressEventResult.Success<*>>(
                        repository.rankings("1", 1, 1, "opaque +/cursor"),
                    ).value as PressEventRankingPage
                assertEquals(3L, page.me?.rank)
                assertEquals(210L, page.leader?.bestScore)
                assertEquals(1, page.items.size)
                assertTrue(page.hasNext)
                assertEquals(2, calls)
            } finally {
                client.close()
            }
        }

    @Test
    fun mapsEmptyRankingWithoutInventingParticipantsOrMyRank() =
        runTest {
            val client = client(MockEngine { respond(EMPTY, headers = headers) })
            try {
                val page =
                    assertIs<PressEventResult.Success<*>>(
                        PressEventRepositoryImpl(PressEventApi(client.requests)).rankings("event", 1),
                    ).value as PressEventRankingPage
                assertEquals(null, page.leader)
                assertEquals(null, page.me)
                assertTrue(page.items.isEmpty())
                assertEquals(null, page.nextCursor)
            } finally {
                client.close()
            }
        }

    @Test
    fun mapsConflictSeparatelyAndRejectsMismatchedSuccessBody() =
        runTest {
            var calls = 0
            val client =
                client(
                    MockEngine {
                        if (calls++ == 0) {
                            respond("{}", HttpStatusCode.Conflict, headers)
                        } else {
                            respond(ATTEMPT.replace("\"total\": 5", "\"total\": 6"), headers = headers)
                        }
                    },
                )
            try {
                val repo = PressEventRepositoryImpl(PressEventApi(client.requests))
                val request = PressEventAttempt(ID, mapOf(EmotionState.FRUSTRATED to 5))
                assertEquals(
                    PressEventFailure.Conflict,
                    assertIs<PressEventResult.Failure>(repo.submit("event", request, 1)).reason,
                )
                assertEquals(
                    PressEventFailure.Retryable,
                    assertIs<PressEventResult.Failure>(repo.submit("event", request, 1)).reason,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun neverSendsAttemptUnderDifferentSessionAndPropagatesCancellation() =
        runTest {
            var sent = 0
            val client =
                client(
                    MockEngine {
                        sent++
                        throw CancellationException("cancelled")
                    },
                )
            try {
                val repo = PressEventRepositoryImpl(PressEventApi(client.requests))
                val request = PressEventAttempt(ID, emptyMap())
                assertEquals(
                    PressEventFailure.SessionChanged,
                    assertIs<PressEventResult.Failure>(repo.submit("event", request, 2)).reason,
                )
                assertEquals(0, sent)
                assertFailsWith<CancellationException> { repo.submit("event", request, 1) }
            } finally {
                client.close()
            }
        }

    @Test
    fun repositoryRejectsResponseThatRepeatsTheRequestedCursor() =
        runTest {
            val client = client(MockEngine { respond(RANKINGS, headers = headers) })
            try {
                val repository = PressEventRepositoryImpl(PressEventApi(client.requests))
                val failure = assertIs<PressEventResult.Failure>(repository.rankings("1", 1, cursor = "next"))
                assertEquals(PressEventFailure.Retryable, failure.reason)
            } finally {
                client.close()
            }
        }

    @Test
    fun snapshotsCountsAndValidatesNonnegativeValues() {
        val original = mutableMapOf(EmotionState.ANGRY to 400)
        val request = PressEventAttempt(ID, original)
        original[EmotionState.ANGRY] = 1
        assertEquals(400L, request.total)
        assertEquals(400, request.counts[EmotionState.ANGRY])
        assertFailsWith<IllegalArgumentException> { PressEventAttempt(ID, mapOf(EmotionState.ANGRY to -1)) }
    }

    private fun client(engine: MockEngine) =
        createApiClient(
            engine = engine,
            config =
                ApiConfig(
                    "https://api.test",
                ),
            accessTokenProvider =
                AccessTokenProvider {
                    AccessToken("token", 1)
                },
        )

    private companion object {
        const val ID = "d40d8d95-30a5-4c52-84da-63e6481b8b56"
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        val ATTEMPT =
            """
            {
              "attemptId": "d40d8d95-30a5-4c52-84da-63e6481b8b56",
              "total": 5,
              "bestScore": 156,
              "rank": 3,
              "rankedAt": "2026-11-11T03:00:00Z",
              "replayed": true
            }
            """.trimIndent()
        val RANKINGS =
            """
            {
              "leader": {
                "publicId": "leader",
                "nickname": "히유",
                "bestScore": 210
              },
              "rankings": {
                "items": [
                  {
                    "rank": 1,
                    "publicId": "leader",
                    "nickname": "히유",
                    "bestScore": 210,
                    "mine": false
                  }
                ],
                "hasNext": true,
                "nextCursor": "next"
              },
              "me": {
                "bestScore": 156,
                "rank": 3
              },
              "rankedAt": "2026-11-11T03:00:01Z"
            }
            """.trimIndent()
        val EMPTY =
            """
            {
              "leader": null,
              "rankings": {
                "items": [],
                "hasNext": false,
                "nextCursor": null
              },
              "me": null,
              "rankedAt": "2026-11-11T03:00:01Z"
            }
            """.trimIndent()
    }
}
