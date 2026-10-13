package com.pheeeew.feature.screens.map

import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.screens.map.filter.FilterScope
import com.pheeeew.feature.screens.map.filter.MapFilterListRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapFilterApplyTest {
    @Test
    fun `취소 직후 다시 적용한 요청의 로딩 상태를 이전 요청이 지우지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val settings = SettingsRepository()
                val response = CompletableDeferred<EmotionResult<EmotionPage>>()
                val list = ListRefresher(visible = true, response = response)
                val vm = viewModel(settings, list)
                runCurrent()
                vm.onViewportChanged(BOUNDS)
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                vm.dismissFilter()
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                assertTrue(vm.filterUiModel.value.applying)
                assertTrue(vm.filterUiModel.value.visible)
                response.complete(EmotionResult.Success(EmotionPage(emptyList(), null)))
                advanceUntilIdle()
                assertEquals(MapFilterScope.MY, settings.saved?.scope)
                assertFalse(vm.filterUiModel.value.visible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `닫힌 목록에도 성공한 필터를 전달하고 저장한 뒤 다이얼로그를 닫는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val settings = SettingsRepository()
                val list = ListRefresher(visible = false)
                val vm = viewModel(settings, list)
                runCurrent()
                vm.onViewportChanged(BOUNDS)
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                advanceUntilIdle()
                assertEquals(MapFilterScope.MY, settings.saved?.scope)
                assertEquals(settings.saved, list.settings)
                assertEquals(0, list.requests)
                assertFalse(vm.filterUiModel.value.visible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `열린 목록의 요청이 완료되어야 설정을 저장하고 다이얼로그를 닫는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val settings = SettingsRepository()
                val response = CompletableDeferred<EmotionResult<EmotionPage>>()
                val list = ListRefresher(visible = true, response = response)
                val vm = viewModel(settings, list)
                runCurrent()
                vm.onViewportChanged(BOUNDS)
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                assertTrue(vm.filterUiModel.value.visible)
                assertTrue(vm.filterUiModel.value.applying)
                assertEquals(null, settings.saved)
                assertEquals(MapFilterScope.MY, list.filter?.scope)
                response.complete(EmotionResult.Success(EmotionPage(emptyList(), null)))
                advanceUntilIdle()
                assertEquals(settings.saved, list.settings)
                assertTrue(list.committed)
                assertFalse(vm.filterUiModel.value.visible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `목록 실패나 로딩 중 닫기에서는 새 필터를 저장하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (cancel in listOf(false, true)) {
                    val settings = SettingsRepository()
                    val response = CompletableDeferred<EmotionResult<EmotionPage>>()
                    val list = ListRefresher(visible = true, response = response)
                    val vm = viewModel(settings, list)
                    runCurrent()
                    vm.onViewportChanged(BOUNDS)
                    vm.openFilter()
                    vm.selectFilterScope(FilterScope.Mine)
                    vm.applyFilter()
                    runCurrent()
                    if (cancel) {
                        vm.dismissFilter()
                        runCurrent()
                        response.complete(EmotionResult.Success(EmotionPage(emptyList(), null)))
                    } else {
                        response.complete(EmotionResult.Failure(EmotionFailure.UNAVAILABLE))
                    }
                    advanceUntilIdle()
                    assertEquals(null, settings.saved)
                    assertFalse(list.committed)
                    assertEquals(cancel, list.cancelled)
                    assertFalse(vm.filterUiModel.value.applying)
                    assertEquals(!cancel, vm.filterUiModel.value.visible)
                    if (!cancel) assertTrue(vm.filterUiModel.value.applyError != null)
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(
        settings: SettingsRepository,
        list: ListRefresher,
    ): MapViewModel {
        val map =
            object : EmotionMapRepository {
                override suspend fun findPage(
                    bounds: EmotionMapBounds?,
                    groupId: String?,
                    cursor: String?,
                    forceRefresh: Boolean,
                ) = EmotionMapPageResult.Success(EmotionMapPage(emptyList(), false, null, 0))

                override fun findSnapshot(
                    bounds: EmotionMapBounds,
                    groupId: String?,
                ): EmotionMapSnapshot? = null
            }
        return MapViewModel(
            refreshLocation =
                RefreshLocationUseCase(
                    object : LocationPermissionController {
                        override suspend fun currentStatus() = LocationPermissionStatus.Granted

                        override suspend fun requestPermission() = LocationPermissionStatus.Granted
                    },
                    object : LocationRepository {
                        override val state = MutableStateFlow<LocationState>(LocationState.Loading)

                        override suspend fun refresh() = Unit
                    },
                ),
            findEmotionMapPage = FindEmotionMapPageUseCase(map),
            findEmotionMapSnapshot = FindEmotionMapSnapshotUseCase(map),
            filterSettingsRepository = settings,
            filterListRefresher = list,
        )
    }

    private class SettingsRepository : MapFilterSettingsRepository {
        var saved: MapFilterSettings? = null

        override suspend fun read() = MapFilterSettings.DEFAULT

        override suspend fun save(settings: MapFilterSettings) {
            saved = settings
        }
    }

    private class ListRefresher(
        private val visible: Boolean,
        private val response: CompletableDeferred<EmotionResult<EmotionPage>>? = null,
    ) : MapFilterListRefresher {
        var settings: MapFilterSettings? = null
        var filter: EmotionSearchFilter? = null
        var requests = 0
        var committed = false
        var cancelled = false

        override fun isVisible() = visible

        override suspend fun loadFirstPage(filter: EmotionSearchFilter): EmotionResult<EmotionPage> {
            this.filter = filter
            requests++
            return try {
                requireNotNull(response).await()
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            }
        }

        override fun commitFirstPage(
            settings: MapFilterSettings,
            page: EmotionPage,
        ) {
            this.settings = settings
            committed = true
        }

        override fun setAppliedSettings(settings: MapFilterSettings) {
            this.settings = settings
        }
    }

    private companion object {
        val BOUNDS = EmotionMapBounds(126.9, 37.5, 127.1, 37.6)
    }
}
