package com.pheeeew.data.config

import com.pheeeew.data.local.config.AppConfigStore
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.domain.model.config.AppConfig
import com.pheeeew.domain.model.config.AppConfigSnapshot
import com.pheeeew.domain.model.config.AppConfigSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigRepositoryTest {
    private class Store : AppConfigStore {
        var raw: String? = null
        var failWrite = false

        override suspend fun read() = raw

        override suspend fun clear() {
            raw = null
        }

        override suspend fun write(value: String) {
            check(!failWrite)
            raw = value
        }
    }

    private fun snapshot(
        revision: Long = 1,
        enabled: Boolean = false,
        notice: String = "점검",
    ) = AppConfigSnapshot(revision, enabled, notice, "2026-10-29T03:00:00Z")

    @Test
    fun defaultsSurviveOfflineAndCorruptCache() =
        runTest {
            val store = Store().apply { raw = "broken" }
            val repo = AppConfigRepository(store, backgroundScope, { error("offline") })
            repo.refresh()
            assertTrue(repo.state.value.initialized)
            assertTrue(repo.state.value.config.dailyPressRankingEnabled)
            assertEquals("", repo.state.value.config.noticeMessage)
            assertEquals(AppConfigSource.DEFAULT, repo.state.value.source)
            assertNotNull(repo.state.value.error)
            assertNull(repo.state.value.snapshot)
        }

    @Test
    fun disabledCacheSurvivesRestartAndFailedRefresh() =
        runTest {
            val store = Store()
            AppConfigRepository(store, backgroundScope, { snapshot() }).refresh()
            val restarted = AppConfigRepository(store, backgroundScope, { error("offline") })
            restarted.refresh()
            assertFalse(restarted.state.value.config.dailyPressRankingEnabled)
            assertEquals(AppConfigSource.CACHE, restarted.state.value.source)
            assertEquals(
                1L,
                restarted.state.value.snapshot
                    ?.revision,
            )
        }

    @Test
    fun staleAndConflictingRevisionsCannotUndoKillSwitch() =
        runTest {
            var next = snapshot(3)
            val store = Store()
            val repo = AppConfigRepository(store, backgroundScope, { next })
            repo.refresh()
            val originalCache = store.raw
            next = snapshot(2, true)
            repo.refresh()
            assertEquals(
                3L,
                repo.state.value.snapshot
                    ?.revision,
            )
            next = snapshot(3, true)
            repo.refresh()
            assertFalse(repo.state.value.config.dailyPressRankingEnabled)
            assertEquals(originalCache, store.raw)
            next = snapshot(4, true)
            repo.refresh()
            assertTrue(repo.state.value.config.dailyPressRankingEnabled)
        }

    @Test
    fun overrideDoesNotPollutePersistedSnapshotAndClearUsesLatestServer() =
        runTest {
            val store = Store()
            var next = snapshot(1, true, "")
            val repo = AppConfigRepository(store, backgroundScope, { next })
            repo.refresh()
            repo.setOverride(AppConfig(true, "임시"))
            next = snapshot(2, false, "서버")
            repo.refresh()
            assertEquals("임시", repo.state.value.config.noticeMessage)
            val restarted = AppConfigRepository(store, backgroundScope, { next })
            restarted.initialize()
            assertFalse(restarted.state.value.config.dailyPressRankingEnabled)
            assertEquals("서버", restarted.state.value.config.noticeMessage)
            repo.setOverride(null)
            assertEquals(restarted.state.value.config, repo.state.value.config)
        }

    @Test
    fun simultaneousManualAndAutomaticRefreshShareOneRequest() =
        runTest {
            val response = CompletableDeferred<AppConfigSnapshot>()
            var requests = 0
            val repo =
                AppConfigRepository(Store(), backgroundScope, {
                    requests++
                    response.await()
                })
            val first = async { repo.refresh() }
            val second = async { repo.refresh(false) }
            runCurrent()
            assertEquals(1, requests)
            response.complete(snapshot())
            first.await()
            second.await()
            assertFalse(repo.state.value.refreshing)
        }

    @Test
    fun foregroundPollsAtSixtySecondsAndCancellationStopsRequest() =
        runTest {
            var requests = 0
            var cancelled = false
            val repo =
                AppConfigRepository(
                    Store(),
                    backgroundScope,
                    {
                        requests++
                        if (requests == 2) {
                            try {
                                delay(10_000)
                            } finally {
                                cancelled = true
                            }
                        }
                        snapshot(requests.toLong())
                    },
                    elapsedMillis = { testScheduler.currentTime },
                )
            val foreground = backgroundScope.launch { repo.runForeground() }
            runCurrent()
            assertEquals(1, requests)
            advanceTimeBy(59_999)
            runCurrent()
            assertEquals(1, requests)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, requests)
            foreground.cancel()
            runCurrent()
            assertTrue(cancelled)
            assertEquals(
                1L,
                repo.state.value.snapshot
                    ?.revision,
            )
            assertFalse(repo.state.value.refreshing)
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(2, requests)
        }

    @Test
    fun timeoutAndStorageFailureAreDistinguishedFromSuccessfulPersistence() =
        runTest {
            val store = Store().apply { failWrite = true }
            var slow = false
            val repo =
                AppConfigRepository(store, backgroundScope, {
                    if (slow) delay(4_000)
                    snapshot()
                })
            repo.refresh()
            assertTrue(repo.state.value.storageError)
            assertFalse(repo.state.value.config.dailyPressRankingEnabled)
            assertNull(store.raw)
            slow = true
            repo.refresh()
            assertNotNull(repo.state.value.error)
            assertFalse(repo.state.value.config.dailyPressRankingEnabled)
        }

    @Test
    fun cacheResetOnlyRemovesNormalSnapshotAndDoesNotPersistOverride() =
        runTest {
            val store = Store()
            val repo = AppConfigRepository(store, backgroundScope, { snapshot() })
            repo.refresh()
            repo.setOverride(AppConfig(false, "임시"))
            repo.clearCache()
            assertNull(store.raw)
            assertEquals(AppConfigSource.OVERRIDE, repo.state.value.source)
            repo.setOverride(null)
            assertTrue(repo.state.value.config.dailyPressRankingEnabled)
            assertEquals(AppConfigSource.DEFAULT, repo.state.value.source)
            assertNull(repo.state.value.lastFetchSucceededAt)
        }

    @Test
    fun sameSnapshotUpdatesFetchTimeWithoutPretendingValueChanged() =
        runTest {
            var now = "2026-10-29T03:00:00Z"
            val repo = AppConfigRepository(Store(), backgroundScope, { snapshot() }, now = { now })
            repo.refresh()
            val applied = repo.state.value.lastAppliedAt
            now = "2026-10-29T03:01:00Z"
            repo.refresh()
            assertEquals(now, repo.state.value.lastFetchSucceededAt)
            assertEquals(applied, repo.state.value.lastAppliedAt)
        }

    @Test
    fun failedAttemptsAreThrottledButManualRefreshBypassesInterval() =
        runTest {
            var requests = 0
            val repo =
                AppConfigRepository(Store(), backgroundScope, {
                    requests++
                    error("offline")
                }, elapsedMillis = { testScheduler.currentTime })
            repo.refresh(false)
            repo.refresh(false)
            assertEquals(1, requests)
            repo.refresh(true)
            assertEquals(2, requests)
        }
}
