package com.pheeeew.core.di.group

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.repository.EmotionResult
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.home.GroupHomeViewModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.filter.MapFilterListRefresher
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class MetroGraphOwnershipTest {
    @Test
    fun `root caches are shared only within their graph`() {
        val client =
            createApiClient(
                MockEngine { error("Construction must not request HTTP") },
                ApiConfig("https://test.invalid"),
                null,
            )
        try {
            val first = createUiTestGraph(client)
            val second = createUiTestGraph(client)
            assertSame(first.services, first.services)
            assertSame(first.stamps, first.stamps)
            assertSame(first.services.groupStamps, first.stamps)
            assertNotSame(first.stamps, second.stamps)
            // Building the full provider table must not eagerly create unrelated auth/press services.
            first.factory
        } finally {
            client.close()
        }
    }

    @Test
    fun `ViewModelStore owns identity and home key reuse`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val client =
                createApiClient(MockEngine { error("Unexpected HTTP") }, ApiConfig("https://test.invalid"), null)
            val home = Owner()
            val otherHome = Owner()
            try {
                val graph = createUiTestGraph(client)
                val factory = graph.factory
                val provider = ViewModelProvider.create(home, factory)
                val first = provider["group-home", GroupHomeViewModel::class]
                assertSame(first, ViewModelProvider.create(home, factory)["group-home", GroupHomeViewModel::class])
                assertNotSame(
                    first,
                    ViewModelProvider.create(otherHome, factory)["group-home", GroupHomeViewModel::class],
                )
                val nearby = provider[NearbyEmotionViewModel::class]
                assertSame(nearby, provider[NearbyEmotionViewModel::class])
                home.viewModelStore.clear()
                val replacement = provider[NearbyEmotionViewModel::class]
                assertNotSame(nearby, replacement)
            } finally {
                home.viewModelStore.clear()
                otherHome.viewModelStore.clear()
                client.close()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `assisted details remain separate even for the same group ID`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val client =
                createApiClient(MockEngine { error("Unexpected HTTP") }, ApiConfig("https://test.invalid"), null)
            val groupEntry = Owner()
            val rankingEntry = Owner()
            try {
                val graph = createUiTestGraph(client)
                val factory = graph.factory.createManuallyAssistedFactory(GroupDetailViewModel.Factory::class)()
                val id = GroupId("00000000-0000-0000-0000-000000000001")
                val vmFactory = viewModelFactory { initializer { factory.create(id) } }
                val first = ViewModelProvider.create(groupEntry, vmFactory)[GroupDetailViewModel::class]
                assertEquals(id, first.groupId)
                assertSame(first, ViewModelProvider.create(groupEntry, vmFactory)[GroupDetailViewModel::class])
                assertNotSame(first, ViewModelProvider.create(rankingEntry, vmFactory)[GroupDetailViewModel::class])
            } finally {
                groupEntry.viewModelStore.clear()
                rankingEntry.viewModelStore.clear()
                client.close()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `map factory uses the owner supplied collaborator and starts with loading disabled`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var requests = 0
            val client =
                createApiClient(
                    MockEngine {
                        requests++
                        error("Map must wait for readiness")
                    },
                    ApiConfig("https://test.invalid"),
                    null,
                )
            val owner = Owner()
            try {
                val graph = createUiTestGraph(client)
                val collaborator = TrackingRefresher()
                val factory = viewModelFactory { initializer { graph.mapFactory.create(collaborator) } }
                ViewModelProvider.create(owner, factory)[MapViewModel::class]
                runCurrent()
                assertEquals(1, collaborator.settingsApplied)
                assertEquals(0, requests)
            } finally {
                owner.viewModelStore.clear()
                client.close()
                Dispatchers.resetMain()
            }
        }

    private class Owner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class TrackingRefresher : MapFilterListRefresher {
        var settingsApplied = 0

        override fun isVisible(): Boolean = false

        override suspend fun loadFirstPage(filter: EmotionSearchFilter): EmotionResult<EmotionPage> =
            error("Not visible")

        override fun commitFirstPage(
            settings: MapFilterSettings,
            page: EmotionPage,
        ) = Unit

        override fun setAppliedSettings(settings: MapFilterSettings) {
            settingsApplied++
        }
    }
}
