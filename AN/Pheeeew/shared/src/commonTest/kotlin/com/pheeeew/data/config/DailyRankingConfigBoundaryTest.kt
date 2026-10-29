package com.pheeeew.data.config

import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.data.local.config.AppConfigStore
import com.pheeeew.data.remote.api.PressRankingApi
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.data.repository.PressRankingRepositoryImpl
import com.pheeeew.domain.model.config.AppConfig
import com.pheeeew.domain.model.config.AppConfigSnapshot
import com.pheeeew.domain.repository.DailyPressRankingLoadResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DailyRankingConfigBoundaryTest {
    @Test
    fun disabledConfigBlocksRequestsAndInterruptsInFlightRanking() =
        runTest {
            val config =
                AppConfigRepository(
                    object : AppConfigStore {
                        override suspend fun read(): String? = null

                        override suspend fun clear() = Unit

                        override suspend fun write(value: String) = Unit
                    },
                    backgroundScope,
                    { AppConfigSnapshot(1, false, "", "2026-10-29T03:00:00Z") },
                )
            config.refresh()
            var requests = 0
            val entered = CompletableDeferred<Unit>()
            var cancelled = false
            var successful = false
            val client =
                createApiClient(
                    engine =
                        MockEngine {
                            requests++
                            if (successful) {
                                return@MockEngine respond(
                                    """{"date":"2026-10-29","startAt":"2026-10-28T15:00:00Z","endAt":"2026-10-29T15:00:00Z","items":[],"me":{"rank":150,"nickname":"익명","pressCount":31}}""",
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                            entered.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                cancelled = true
                            }
                            @Suppress("UNREACHABLE_CODE")
                            respond("{}")
                        },
                    config = ApiConfig("https://api-dev.pheeeew.com"),
                    accessTokenProvider =
                        AccessTokenProvider {
                            com.pheeeew.core.network
                                .AccessToken("test")
                        },
                )
            try {
                val repository = PressRankingRepositoryImpl(PressRankingApi(client.requests), config)
                assertEquals(DailyPressRankingLoadResult.Unavailable, repository.findDaily("2026-10-29", null))
                assertEquals(0, requests)
                config.setOverride(AppConfig(true))
                val pending = async { repository.findDaily("2026-10-29", null) }
                entered.await()
                config.setOverride(null)
                runCurrent()
                assertEquals(DailyPressRankingLoadResult.Unavailable, pending.await())
                assertTrue(cancelled)
                assertEquals(1, requests)
                successful = true
                config.setOverride(AppConfig(true))
                assertTrue(repository.findDaily("2026-10-29", null) is DailyPressRankingLoadResult.Loaded)
            } finally {
                client.close()
            }
        }
}
