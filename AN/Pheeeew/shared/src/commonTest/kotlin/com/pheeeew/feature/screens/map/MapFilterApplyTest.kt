package com.pheeeew.feature.screens.map

import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionMapViewport
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.repository.EmotionRegionMapRepository
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.screens.map.filter.FilterScope
import com.pheeeew.feature.screens.map.filter.MapFilterListRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    fun `집계가 성공해도 열린 목록이 실패하면 기존 필터와 집계를 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val settings = SettingsRepository()
                val response = CompletableDeferred<EmotionResult<EmotionPage>>()
                val list = ListRefresher(visible = true, response = response)
                val regions =
                    regionRepository { _, filter ->
                        regionResult(if (filter?.scope == MapFilterScope.MY) "candidate" else "original")
                    }
                val vm = viewModel(settings, list, regions = regions)
                runCurrent()
                vm.onViewportChanged(EmotionMapViewport(BOUNDS, 11.0))
                advanceUntilIdle()
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                assertTrue(vm.filterUiModel.value.applying)
                assertEquals(MapFilterScope.MY, list.filter?.scope)
                assertEquals(null, settings.saved)
                response.complete(EmotionResult.Failure(EmotionFailure.UNAVAILABLE))
                advanceUntilIdle()
                assertEquals(null, settings.saved)
                assertFalse(list.committed)
                assertEquals(
                    listOf("original"),
                    vm.uiModel.value.regionClusters
                        .map { it.id },
                )
                assertTrue(vm.filterUiModel.value.visible)
                assertTrue(vm.filterUiModel.value.applyError != null)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `설정 저장 중 이동하면 이전 집계를 표시하지 않고 저장한 필터로 최신 영역을 조회한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val gate = CompletableDeferred<Unit>()
                val settings = SettingsRepository(saveGate = gate)
                val latest = BOUNDS.copy(minLongitude = 128.0, maxLongitude = 128.2)
                val requests = mutableListOf<Pair<EmotionMapBounds, EmotionSearchFilter?>>()
                val regions =
                    regionRepository { bounds, filter ->
                        requests += bounds to filter
                        regionResult(
                            if (bounds ==
                                latest
                            ) {
                                "latest"
                            } else if (filter?.scope == MapFilterScope.MY) {
                                "stale"
                            } else {
                                "original"
                            },
                        )
                    }
                val vm = viewModel(settings, ListRefresher(visible = false), regions = regions)
                runCurrent()
                vm.onViewportChanged(EmotionMapViewport(BOUNDS, 11.0))
                advanceUntilIdle()
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                vm.onViewportChanged(EmotionMapViewport(latest, 11.0))
                gate.complete(Unit)
                runCurrent()
                assertEquals(
                    listOf("original"),
                    vm.uiModel.value.regionClusters
                        .map { it.id },
                )
                advanceUntilIdle()
                assertEquals(latest, requests.last().first)
                assertEquals(MapFilterScope.MY, requests.last().second?.scope)
                assertEquals(
                    listOf("latest"),
                    vm.uiModel.value.regionClusters
                        .map { it.id },
                )
                assertEquals(MapFilterScope.MY, settings.saved?.scope)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `필터 집계 응답 대기 중 이동하면 최신 영역을 다시 조회한 뒤 적용한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val settings = SettingsRepository()
                val response = CompletableDeferred<EmotionRegionResult>()
                val requests = mutableListOf<Pair<EmotionMapBounds, EmotionSearchFilter?>>()
                val regions =
                    regionRepository { bounds, filter ->
                        requests += bounds to filter
                        if (filter?.scope != MapFilterScope.MY) {
                            regionResult("original")
                        } else if (requests.count { it.second?.scope == MapFilterScope.MY } == 1) {
                            response.await()
                        } else {
                            regionResult("latest")
                        }
                    }
                val vm = viewModel(settings, ListRefresher(visible = false), regions = regions)
                runCurrent()
                vm.onViewportChanged(EmotionMapViewport(BOUNDS, 11.0))
                advanceUntilIdle()
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                val latest = BOUNDS.copy(minLongitude = 128.0, maxLongitude = 128.2)
                vm.onViewportChanged(EmotionMapViewport(latest, 11.0))
                runCurrent()
                assertEquals(null, settings.saved)
                assertEquals(
                    listOf("original"),
                    vm.uiModel.value.regionClusters
                        .map { it.id },
                )
                response.complete(regionResult("stale"))
                advanceUntilIdle()
                assertEquals(latest, requests.last().first)
                assertEquals(MapFilterScope.MY, requests.last().second?.scope)
                assertEquals(
                    listOf("latest"),
                    vm.uiModel.value.regionClusters
                        .map { it.id },
                )
                assertEquals(MapFilterScope.MY, settings.saved?.scope)
                assertFalse(vm.filterUiModel.value.visible)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `필터 집계 중 가까이 확대하면 이전 집계 대신 새 필터의 개별 핀을 적용한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val response = CompletableDeferred<EmotionRegionResult>()
                val map =
                    FilteredMapRepository(load = { _, filter, _ ->
                        assertEquals(MapFilterScope.MY, filter.scope)
                        page(pin(42))
                    })
                val regions =
                    regionRepository { _, filter ->
                        if (filter?.scope == MapFilterScope.MY) response.await() else regionResult("original")
                    }
                val vm = viewModel(SettingsRepository(), ListRefresher(visible = false), map, regions)
                runCurrent()
                vm.onViewportChanged(EmotionMapViewport(BOUNDS, 11.0))
                advanceUntilIdle()
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                vm.onViewportChanged(EmotionMapViewport(BOUNDS, 13.0))
                response.complete(regionResult("stale"))
                advanceUntilIdle()
                assertEquals(listOf(42L), vm.pinIds())
                assertTrue(
                    vm.uiModel.value.regionClusters
                        .isEmpty(),
                )
                assertEquals(null, vm.uiModel.value.displayedRegionLevel)
                assertFalse(vm.filterUiModel.value.applying)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `필터 집계 실패는 기존 필터를 유지하고 빈 집계 성공은 기존 표시를 지운다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (fail in listOf(true, false)) {
                    val settings = SettingsRepository()
                    val regions =
                        regionRepository { _, filter ->
                            if (filter?.scope != MapFilterScope.MY) {
                                regionResult("original")
                            } else if (fail) {
                                EmotionRegionResult.Failure
                            } else {
                                EmotionRegionResult.Success(emptyList())
                            }
                        }
                    val vm = viewModel(settings, ListRefresher(visible = false), regions = regions)
                    runCurrent()
                    vm.onViewportChanged(EmotionMapViewport(BOUNDS, 11.0))
                    advanceUntilIdle()
                    vm.openFilter()
                    vm.selectFilterScope(FilterScope.Mine)
                    vm.applyFilter()
                    advanceUntilIdle()
                    assertEquals(if (fail) null else MapFilterScope.MY, settings.saved?.scope)
                    assertEquals(
                        if (fail) listOf("original") else emptyList(),
                        vm.uiModel.value.regionClusters
                            .map { it.id },
                    )
                    assertEquals(fail, vm.filterUiModel.value.visible)
                    assertEquals(fail, vm.filterUiModel.value.applyError != null)
                    assertFalse(vm.filterUiModel.value.applying)
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

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

    @Test
    fun `필터 적용은 대기 중인 이전 핀 발행을 취소하고 확장 영역의 새 핀만 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val pending = CompletableDeferred<EmotionMapPageResult>()
                val requestedBounds = mutableListOf<EmotionMapBounds?>()
                val map =
                    FilteredMapRepository(
                        load = { bounds, filter, cursor ->
                            requestedBounds += bounds
                            if (filter.scope == MapFilterScope.MY) {
                                page(pin(3))
                            } else {
                                when (cursor) {
                                    null -> page(pin(1), nextCursor = "second")
                                    "second" -> page(pin(2), nextCursor = "third")
                                    else -> pending.await()
                                }
                            }
                        },
                    )
                val vm = viewModel(SettingsRepository(), ListRefresher(visible = false), map)
                runCurrent()
                vm.onViewportChanged(BOUNDS)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L), vm.pinIds())

                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                runCurrent()
                assertEquals(BOUNDS.expandedForPinQuery(), requestedBounds.last())
                assertEquals(listOf(3L), vm.pinIds())
                advanceUntilIdle()
                assertEquals(listOf(3L), vm.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `필터 적용 뒤 화면 이동은 현재 필터의 캐시를 발행한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val snapshotFilters = mutableListOf<EmotionSearchFilter>()
                val map =
                    FilteredMapRepository(
                        load = { _, filter, _ -> page(pin(if (filter.scope == MapFilterScope.MY) 2 else 1)) },
                        snapshot = { _, filter ->
                            snapshotFilters += filter
                            if (filter.scope == MapFilterScope.MY) {
                                EmotionMapSnapshot(listOf(pin(3).copy(longitude = 127.15)))
                            } else {
                                null
                            }
                        },
                    )
                val vm = viewModel(SettingsRepository(), ListRefresher(visible = false), map)
                runCurrent()
                vm.onViewportChanged(BOUNDS)
                advanceUntilIdle()
                vm.openFilter()
                vm.selectFilterScope(FilterScope.Mine)
                vm.applyFilter()
                advanceUntilIdle()

                vm.onViewportChanged(BOUNDS.copy(minLongitude = 127.0, maxLongitude = 127.2))
                assertEquals(MapFilterScope.MY, snapshotFilters.last().scope)
                assertTrue(3L in vm.pinIds())
                assertFalse(1L in vm.pinIds())
                advanceUntilIdle()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(
        settings: SettingsRepository,
        list: ListRefresher,
        mapOverride: EmotionMapRepository? = null,
        regions: EmotionRegionMapRepository? = null,
    ): MapViewModel {
        val map =
            mapOverride ?: object : EmotionMapRepository {
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
            findRegions = regions?.let(::FindEmotionRegionsUseCase),
        )
    }

    private fun regionResult(id: String) =
        EmotionRegionResult.Success(listOf(EmotionRegion(id, id, 127.0, 37.55, 3, EmotionState.ANGRY)))

    private fun regionRepository(
        load: suspend (EmotionMapBounds, EmotionSearchFilter?) -> EmotionRegionResult,
    ): EmotionRegionMapRepository =
        object : EmotionRegionMapRepository {
            override suspend fun findRegions(
                bounds: EmotionMapBounds,
                level: EmotionRegionLevel,
                groupId: String?,
                forceRefresh: Boolean,
                filter: EmotionSearchFilter?,
            ) = load(bounds, filter)

            override fun findSnapshot(
                bounds: EmotionMapBounds,
                level: EmotionRegionLevel,
                groupId: String?,
                filter: EmotionSearchFilter?,
            ): List<EmotionRegion>? = null
        }

    private class FilteredMapRepository(
        private val load: suspend (EmotionMapBounds?, EmotionSearchFilter, String?) -> EmotionMapPageResult,
        private val snapshot: (EmotionMapBounds, EmotionSearchFilter) -> EmotionMapSnapshot? = { _, _ -> null },
    ) : EmotionMapRepository {
        override suspend fun findPage(
            bounds: EmotionMapBounds?,
            groupId: String?,
            cursor: String?,
            forceRefresh: Boolean,
        ): EmotionMapPageResult = error("Filtered queries must include the applied settings")

        override suspend fun findFilteredPage(
            bounds: EmotionMapBounds?,
            filter: EmotionSearchFilter,
            cursor: String?,
            forceRefresh: Boolean,
        ) = load(bounds, filter, cursor)

        override fun findSnapshot(
            bounds: EmotionMapBounds,
            groupId: String?,
        ): EmotionMapSnapshot? = error("Filtered snapshots must include the applied settings")

        override fun findFilteredSnapshot(
            bounds: EmotionMapBounds,
            filter: EmotionSearchFilter,
        ) = snapshot(bounds, filter)
    }

    private fun MapViewModel.pinIds() = uiModel.value.emotionPins.map { it.id }

    private fun pin(id: Long) = EmotionMapPin(id, 127.0, 37.55, "2026-10-13T00:00:00Z", EmotionState.ANGRY, 0.0, null)

    private fun page(
        pin: EmotionMapPin,
        nextCursor: String? = null,
    ) = EmotionMapPageResult.Success(EmotionMapPage(listOf(pin), nextCursor != null, nextCursor, 0))

    private class SettingsRepository(
        private val saveGate: CompletableDeferred<Unit>? = null,
    ) : MapFilterSettingsRepository {
        var saved: MapFilterSettings? = null

        override suspend fun read() = MapFilterSettings.DEFAULT

        override suspend fun save(settings: MapFilterSettings) {
            saveGate?.await()
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
