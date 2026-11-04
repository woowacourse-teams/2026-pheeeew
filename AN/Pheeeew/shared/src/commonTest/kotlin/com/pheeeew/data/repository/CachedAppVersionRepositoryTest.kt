package com.pheeeew.data.repository

import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.domain.model.version.AppVersionPolicy
import com.pheeeew.domain.model.version.evaluateAppVersion
import com.pheeeew.domain.repository.AppVersionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CachedAppVersionRepositoryTest {
    private class Storage : AppVersionCacheStorage {
        var value: String? = null
        var failWrite = false

        override fun read() = value

        override fun write(value: String) {
            if (failWrite) error("disk full")
            this.value = value
        }
    }

    private val policy = AppVersionPolicy("2.5.0", "2.6.0", "https://play.google.com/store/apps/details?id=com.pheeeew")

    @Test
    fun `restart uses a valid policy and reevaluates the updated installed version`() =
        runTest {
            val storage = Storage()
            val repo = CachedAppVersionRepository(AppVersionRepository { policy }, storage, "android", "dev") { 1000 }
            repo.getPolicy()
            val restarted =
                CachedAppVersionRepository(
                    AppVersionRepository { error("offline") },
                    storage,
                    "android",
                    "dev",
                ) { 2000 }
            val cached = assertNotNull(restarted.cachedPolicy())
            assertIs<AppVersionDecision.UpdateRequired>(evaluateAppVersion("2.4.0", cached))
            assertIs<AppVersionDecision.UpdateSuggested>(evaluateAppVersion("2.5.0", cached))
            assertEquals(AppVersionDecision.Current, evaluateAppVersion("2.6.0", cached))
            assertFails { restarted.getPolicy() }
            assertEquals(policy, restarted.cachedPolicy())
        }

    @Test
    fun `expiration boundary and future timestamp cannot block from disk`() =
        runTest {
            val storage = Storage()
            var now = 1000L
            val repo = CachedAppVersionRepository(AppVersionRepository { policy }, storage, "android", "dev") { now }
            repo.getPolicy()
            now += CachedAppVersionRepository.MAX_AGE_MILLIS - 1
            assertNotNull(repo.cachedPolicy())
            now++
            assertNull(repo.cachedPolicy())
            now = 999
            assertNull(repo.cachedPolicy())
        }

    @Test
    fun `platform environment schema and corrupt entries are isolated`() =
        runTest {
            val storage = Storage()
            CachedAppVersionRepository(AppVersionRepository { policy }, storage, "android", "dev") { 1000 }.getPolicy()
            assertNull(
                CachedAppVersionRepository(
                    AppVersionRepository { policy },
                    storage,
                    "ios",
                    "dev",
                ) { 1000 }.cachedPolicy(),
            )
            assertNull(
                CachedAppVersionRepository(
                    AppVersionRepository {
                        policy
                    },
                    storage,
                    "android",
                    "prod",
                ) { 1000 }.cachedPolicy(),
            )
            val saved = storage.value!!
            storage.value = saved.replace("\"schema\":1", "\"schema\":2")
            assertNull(
                CachedAppVersionRepository(
                    AppVersionRepository {
                        policy
                    },
                    storage,
                    "android",
                    "dev",
                ) { 1000 }.cachedPolicy(),
            )
            storage.value = "broken"
            assertNull(
                CachedAppVersionRepository(
                    AppVersionRepository {
                        policy
                    },
                    storage,
                    "android",
                    "dev",
                ) { 1000 }.cachedPolicy(),
            )
        }

    @Test
    fun `invalid responses never replace last good policy and write failure does not fail network`() =
        runTest {
            val storage = Storage()
            var response = policy
            val repo = CachedAppVersionRepository(AppVersionRepository { response }, storage, "android", "dev") { 1000 }
            repo.getPolicy()
            for (invalid in listOf(
                policy.copy(minSupportedVersion = "9.0.0"),
                policy.copy(storeUrl = "invalid"),
                policy.copy(latestVersion = "01.0.0"),
            )) {
                response = invalid
                assertFails { repo.getPolicy() }
                assertEquals(policy, repo.cachedPolicy())
            }
            storage.failWrite = true
            response = policy.copy(minSupportedVersion = "1.0.0")
            assertEquals(response, repo.getPolicy())
        }
}
