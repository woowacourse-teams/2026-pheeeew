package com.pheeeew.data.repository.group

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.remote.group.api.GroupExploreApi
import com.pheeeew.domain.repository.group.GroupExploreResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GroupExploreRepositoryImplTest {
    @Test
    fun `empty response is successful and cursor errors are distinct from other failures`() =
        runTest {
            assertIs<GroupExploreResult.Loaded>(result("""{"items":[],"hasNext":false,"nextCursor":null}"""))
            assertEquals(
                GroupExploreResult.InvalidCursor,
                result("""{"code":"GROUP-010","message":"invalid cursor"}""", HttpStatusCode.BadRequest),
            )
            val statuses = listOf(HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.BadGateway)
            statuses.forEach { status ->
                assertEquals(GroupExploreResult.Unavailable, result("""{"code":"OTHER"}""", status))
            }
        }

    @Test
    fun `invalid fields and inconsistent pages never become empty successes`() =
        runTest {
            val invalid =
                listOf(
                    page.replace("\"hasNext\":true", "\"hasNext\":false"),
                    page.replace("\"nextCursor\":\" opaque+cursor/= \"", "\"nextCursor\":null"),
                    page.replace("\"nextCursor\":\" opaque+cursor/= \"", "\"nextCursor\":\" \""),
                    page.replace(",\"nextCursor\":\" opaque+cursor/= \"", ""),
                    page.replace("\"joined\":true,", ""),
                    page.replace("\"joined\":true", "\"joined\":null"),
                    page.replace("PUBLIC", "UNKNOWN"),
                    page.replace(ID, "not-a-uuid"),
                    page.replace("\"memberCount\":7", "\"memberCount\":-1"),
                    page.replace("CIRCLE", "UNKNOWN"),
                    """{"items":[],"hasNext":true,"nextCursor":"next"}""",
                )
            invalid.forEach { body -> assertEquals(GroupExploreResult.Unavailable, result(body), body) }
        }

    @Test
    fun `transport failures are unavailable but cancellation propagates`() =
        runTest {
            val client =
                createApiClient(
                    engine = MockEngine { throw kotlin.IllegalStateException("connection failed") },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                assertEquals(
                    GroupExploreResult.Unavailable,
                    GroupExploreRepositoryImpl(GroupExploreApi(client.requests)).loadPage(null, null),
                )
            } finally {
                client.close()
            }
            val cancelled =
                createApiClient(
                    engine = MockEngine { throw CancellationException("cancelled") },
                    config = ApiConfig("https://api.example.test"),
                    accessTokenProvider = { AccessToken("token") },
                )
            try {
                assertFailsWith<CancellationException> {
                    GroupExploreRepositoryImpl(GroupExploreApi(cancelled.requests)).loadPage(null, null)
                }
            } finally {
                cancelled.close()
            }
        }

    private suspend fun result(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): GroupExploreResult {
        val client =
            createApiClient(
                engine = MockEngine { respond(body, status, headers) },
                config = ApiConfig("https://api.example.test"),
                accessTokenProvider = { AccessToken("token") },
            )
        return try {
            GroupExploreRepositoryImpl(GroupExploreApi(client.requests)).loadPage(null, null)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val ID = "10000000-0000-0000-0000-000000000001"
        const val SECOND_ID = "10000000-0000-0000-0000-000000000002"
        val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val page =
            """{"items":[{"groupId":"$ID","name":"서버 그룹","description":null,"joined":true,"memberCount":7,"joinType":"PUBLIC","stamp":{"text":"휴식","textColor":"#FFFFFF","backgroundColor":"#112233","frame":"CIRCLE"}},{"groupId":"$SECOND_ID","name":"비공개 그룹","joined":false,"memberCount":2,"joinType":"PRIVATE","stamp":{"text":"기록","textColor":"#FFFFFF","backgroundColor":"#112233","frame":"CIRCLE"}}],"hasNext":true,"nextCursor":" opaque+cursor/= "}"""
    }
}
