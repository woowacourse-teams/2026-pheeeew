package com.pheeeew.data.repository.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupStampListApi
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupStampListRepositoryImplTest {
    @Test
    fun `loads authenticated group stamps in server order`() =
        runTest {
            val client =
                createClient(stampsJson) { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/api/v2/groups/stamps", request.url.encodedPath)
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                }
            try {
                val groups = assertIs<GroupStampListLoadResult.Loaded>(repository(client).findMyStamps()).groups
                assertEquals(listOf("첫째", "둘째"), groups.map { it.name })
                assertEquals("10000000-0000-0000-0000-000000000001", groups.first().id.value)
                assertEquals(GroupStampFrame.STUB, groups.last().stamp.frame)
                assertEquals(
                    0x80445566L,
                    groups
                        .last()
                        .stamp.backgroundColor.argb,
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `empty list is successful while unauthorized and invalid stamps are unavailable`() =
        runTest {
            val empty = createClient("[]")
            val unauthorized = createClient("{}", HttpStatusCode.Unauthorized)
            val invalid = createClient(stampsJson.replace("STUB", "UNKNOWN"))
            try {
                assertEquals(GroupStampListLoadResult.Loaded(emptyList()), repository(empty).findMyStamps())
                assertEquals(GroupStampListLoadResult.Unavailable, repository(unauthorized).findMyStamps())
                assertEquals(GroupStampListLoadResult.Unavailable, repository(invalid).findMyStamps())
            } finally {
                empty.close()
                unauthorized.close()
                invalid.close()
            }
        }

    @Test
    fun `successful response is reused for five minutes then refreshed`() =
        runTest {
            var now = 1_000L
            var requestCount = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            requestCount++
                            respond(
                                if (requestCount == 1) stampsJson else "[]",
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                val repository = GroupStampListRepositoryImpl(GroupStampListApi(client.requests)) { now }
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                now += 5 * 60 * 1000L - 1
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                assertEquals(1, requestCount)
                now++
                assertEquals(emptyList(), assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups)
                assertEquals(2, requestCount)
                assertEquals(emptyList(), assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups)
                assertEquals(2, requestCount)
            } finally {
                client.close()
            }
        }

    @Test
    fun `membership change invalidates even an empty cached list before five minutes`() =
        runTest {
            var body = "[]"
            var requests = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            requests++
                            respond(
                                body,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                val repository = repository(client)
                assertEquals(0, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                body = stampsJson
                repository.invalidate()
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                assertEquals(2, requests)
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                assertEquals(2, requests)
                body = "[]"
                repository.invalidate()
                assertEquals(0, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                assertEquals(3, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun `response started before membership change is retried and never returned or cached`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var requests = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            val body =
                                if (++requests == 1) {
                                    started.complete(Unit)
                                    release.await()
                                    "[]"
                                } else {
                                    stampsJson
                                }
                            respond(
                                body,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                val repository = repository(client)
                val first = async { repository.findMyStamps() }
                started.await()
                repository.invalidate()
                val second = async { repository.findMyStamps() }
                release.complete(Unit)
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(first.await()).groups.size)
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(second.await()).groups.size)
                assertEquals(2, requests)
                assertEquals(2, assertIs<GroupStampListLoadResult.Loaded>(repository.findMyStamps()).groups.size)
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }

    private fun repository(client: ApiClient) = GroupStampListRepositoryImpl(GroupStampListApi(client.requests))

    private fun createClient(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        inspect: (HttpRequestData) -> Unit = {},
    ) = createApiClient(
        engine =
            MockEngine { request ->
                inspect(request)
                respond(
                    body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        config = ApiConfig("https://api.example.test"),
        accessTokenProvider = { AccessToken("access-token") },
    )

    private companion object {
        val stampsJson =
            """
            [
              {"groupId":"10000000-0000-0000-0000-000000000001","name":"첫째","stamp":{"text":"일번","textColor":"#112233","backgroundColor":"#FFE164","frame":"CIRCLE"}},
              {"groupId":"10000000-0000-0000-0000-000000000002","name":"둘째","stamp":{"text":"이번","textColor":"#FFFFFF","backgroundColor":"#44556680","frame":"STUB"}}
            ]
            """.trimIndent()
    }
}
