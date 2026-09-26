package com.pheeeew.data.repository

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.GroupListApi
import com.pheeeew.domain.repository.GroupListLoadResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupListRepositoryImplTest {
    @Test
    fun `GET mine is authenticated and preserves response order and Long counts`() =
        runTest {
            val client =
                createApiClient(
                    engine =
                        MockEngine { request ->
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("/api/v2/groups", request.url.encodedPath)
                            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                            respond(groupsJson, headers = jsonHeaders())
                        },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("access-token") },
                )
            try {
                val result = GroupListRepositoryImpl(GroupListApi(client.requests)).findMine()
                val groups = assertIs<GroupListLoadResult.Loaded>(result).groups

                assertEquals(
                    listOf(
                        "10000000-0000-0000-0000-000000000001",
                        "10000000-0000-0000-0000-000000000002",
                    ),
                    groups.map {
                        it.id.value
                    },
                )
                assertEquals(listOf("서버 첫째", "서버 둘째"), groups.map { it.name })
                assertEquals(3_000_000_000L, groups.first().memberCount)
            } finally {
                client.close()
            }
        }

    @Test
    fun `successful empty array remains a successful empty list`() =
        runTest {
            val client = createClient("[]")
            try {
                assertEquals(
                    GroupListLoadResult.Loaded(emptyList()),
                    GroupListRepositoryImpl(GroupListApi(client.requests)).findMine(),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `authentication and malformed contracts are unavailable rather than empty`() =
        runTest {
            val unauthorized =
                createClient("{\"code\":\"AUTH-001\"}", HttpStatusCode.Unauthorized)
            val malformed = createClient(groupsJson.replace("\"CIRCLE\"", "\"UNKNOWN_FRAME\""))
            try {
                val authResult = GroupListRepositoryImpl(GroupListApi(unauthorized.requests)).findMine()
                val contractResult = GroupListRepositoryImpl(GroupListApi(malformed.requests)).findMine()
                assertEquals(GroupListLoadResult.Unavailable, authResult)
                assertEquals(GroupListLoadResult.Unavailable, contractResult)
                assertIs<ApiResult.Failure>(GroupListApi(unauthorized.requests).findMine())
            } finally {
                unauthorized.close()
                malformed.close()
            }
        }

    private fun createClient(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = createApiClient(
        engine = MockEngine { respond(body, status = status, headers = jsonHeaders()) },
        config = ApiConfig("https://api.example.test"),
        accessTokenProvider = { AccessToken("access-token") },
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private companion object {
        val groupsJson =
            """
            [
                {"groupId":"10000000-0000-0000-0000-000000000001","name":"서버 첫째","description":null,"inviteCode":"ABC123","role":"OWNER","memberCount":3000000000,"stamp":{"text":"첫째","textColor":"#112233","backgroundColor":"#445566","frame":"CIRCLE"}},
                {"groupId":"10000000-0000-0000-0000-000000000002","name":"서버 둘째","description":"두 번째","inviteCode":"DEF456","role":"MEMBER","memberCount":2,"stamp":{"text":"둘째","textColor":"#FFFFFF","backgroundColor":"#01020380","frame":"STUB"}}
            ]
            """.trimIndent()
    }
}
