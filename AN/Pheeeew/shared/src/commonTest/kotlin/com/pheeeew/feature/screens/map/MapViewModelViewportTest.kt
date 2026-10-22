package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapFailure
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionMapViewport
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.MapFilterPeriod
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.permission.LocationPermissionStatus
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.repository.EmotionRegionMapRepository
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.screens.map.filter.FilterScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelViewportTest {
    @Test
    fun `splash preloads location without prompting and defers denied permission until start`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requests = 0
                val refreshCompleted = CompletableDeferred<Unit>()
                val permission =
                    object : LocationPermissionController {
                        override suspend fun currentStatus() = LocationPermissionStatus.Denied

                        override suspend fun requestPermission(): LocationPermissionStatus {
                            requests++
                            return LocationPermissionStatus.Denied
                        }
                    }
                val location =
                    object : LocationRepository {
                        override val state =
                            MutableStateFlow<LocationState>(LocationState.Unavailable(LocationError.PermissionDenied))

                        override suspend fun refresh() {
                            refreshCompleted.await()
                        }
                    }
                val pins = QueuedEmotionMapRepository()
                val vm =
                    MapViewModel(
                        RefreshLocationUseCase(permission, location),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                    )
                vm.prepareCurrentLocation()
                vm.prepareCurrentLocation()
                runCurrent()
                assertEquals(0, requests)
                assertFalse(vm.uiModel.value.showLocationPermissionDialog)
                // Even when splash expires during the silent request, permission waits for that request.
                vm.start()
                runCurrent()
                assertEquals(0, requests)
                refreshCompleted.complete(Unit)
                runCurrent()
                assertEquals(1, requests)
                assertTrue(vm.uiModel.value.showLocationPermissionDialog)
                vm.start()
                runCurrent()
                assertEquals(1, requests)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `location acquired during splash is reused after splash without a duplicate request`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var refreshes = 0
                val acquired = LocationState.Available(CurrentLocation(37.5, 127.0, 5f, 0))
                val permission =
                    object : LocationPermissionController {
                        override suspend fun currentStatus() = LocationPermissionStatus.Granted

                        override suspend fun requestPermission(): LocationPermissionStatus = error("Unexpected prompt")
                    }
                val location =
                    object : LocationRepository {
                        override val state = MutableStateFlow<LocationState>(LocationState.Loading)

                        override suspend fun refresh() {
                            refreshes++
                            state.value = acquired
                        }
                    }
                val pins = QueuedEmotionMapRepository()
                val vm =
                    MapViewModel(
                        RefreshLocationUseCase(permission, location),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                    )
                vm.prepareCurrentLocation()
                runCurrent()
                vm.start()
                runCurrent()
                assertEquals(1, refreshes)
                assertEquals(acquired, vm.uiModel.value.locationState)
                assertFalse(vm.uiModel.value.showLocationPermissionDialog)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지도 재시도는 복구까지 같은 안내를 유지하고 중복 및 오래된 콜백을 무시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = QueuedEmotionMapRepository()
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.onMapError(MapErrorUiModel.StyleLoadFailed)
                val feedback = vm.uiModel.value.primaryFeedback()
                vm.retryMap()
                vm.retryMap()
                assertEquals(1, vm.uiModel.value.mapRevision)
                assertTrue(vm.uiModel.value.isRetryingMap)
                assertEquals(feedback, vm.uiModel.value.primaryFeedback())
                vm.onMapRecovered(revision = 0)
                vm.onMapError(MapErrorUiModel.RendererUnavailable, revision = 0)
                assertTrue(vm.uiModel.value.isRetryingMap)
                assertEquals(feedback, vm.uiModel.value.primaryFeedback())
                vm.onMapError(MapErrorUiModel.StyleLoadFailed, revision = 1)
                assertFalse(vm.uiModel.value.isRetryingMap)
                assertEquals(feedback, vm.uiModel.value.primaryFeedback())
                vm.retryMap()
                vm.onMapRecovered(revision = 2)
                assertFalse(vm.uiModel.value.isRetryingMap)
                assertEquals(null, vm.uiModel.value.primaryFeedback())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `핀 모드에서 삭제되어도 지역 캐시를 무효화한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var invalidations = 0
                val pins =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(emptyPage()),
                        CompletableDeferred(emptyPage()),
                    )
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        invalidateRegionCache = { invalidations++ },
                    )

                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 16.0))
                runCurrent()
                viewModel.onEmotionDeleted()
                runCurrent()

                assertEquals(1, invalidations)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `준비 해제 후 지도 조회를 멈추고 새 세션에서 늦은 이전 응답을 무시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requests = 0
                var invalidations = 0
                val staleResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository =
                    object : EmotionMapRepository {
                        override fun findSnapshot(
                            bounds: EmotionMapBounds,
                            groupId: String?,
                        ): EmotionMapSnapshot? = null

                        override suspend fun findPage(
                            bounds: EmotionMapBounds?,
                            groupId: String?,
                            cursor: String?,
                            forceRefresh: Boolean,
                        ): EmotionMapPageResult =
                            when (++requests) {
                                1 -> page(listOf(pin(1, 127.02, 37.55)))
                                2 -> withContext(NonCancellable) { staleResponse.await() }
                                else -> page(listOf(pin(3, 127.12, 37.65)))
                            }
                    }
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                        dataLoadingEnabled = false,
                        invalidateSessionCache = { invalidations++ },
                    )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 16.0))
                viewModel.updateReadiness(true, 1)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                viewModel.refreshEmotionPins()
                runCurrent()
                assertEquals(2, requests)

                viewModel.updateReadiness(false, 1)
                assertTrue(viewModel.pinIds().isEmpty())
                assertFalse(viewModel.uiModel.value.isLoadingEmotionPins)
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.1, 37.6), 16.0))
                viewModel.retryEmotionPins()
                viewModel.onConnectivityChanged(false)
                viewModel.onConnectivityChanged(true)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(2, requests)
                assertEquals(1, invalidations)

                viewModel.updateReadiness(true, 2)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(3L), viewModel.pinIds())
                assertEquals(2, invalidations)
                staleResponse.complete(page(listOf(pin(2, 127.02, 37.55))))
                runCurrent()
                assertEquals(listOf(3L), viewModel.pinIds())
                assertFalse(viewModel.uiModel.value.isLoadingEmotionPins)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `같은 세션의 준비 복구도 현재 뷰포트로 지역 요약을 다시 조회한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requests = 0
                val pins = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    requests++
                                    EmotionRegionResult.Success(
                                        listOf(EmotionRegion("city", "서울", 127.0, 37.5, 3, null)),
                                    )
                                },
                            ),
                        dataLoadingEnabled = false,
                    )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 10.0))
                viewModel.updateReadiness(true, 1)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, requests)
                assertEquals(1, viewModel.uiModel.value.regionClusters.size)

                viewModel.updateReadiness(false, 1)
                assertTrue(
                    viewModel.uiModel.value.regionClusters
                        .isEmpty(),
                )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 10.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, requests)

                viewModel.updateReadiness(true, 1)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(2, requests)
                assertEquals(1, viewModel.uiModel.value.regionClusters.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `닉네임 확인 전 지도는 표시해도 위치와 감정 조회를 시작하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var locationRefreshes = 0
                val repository = QueuedEmotionMapRepository(CompletableDeferred(emptyPage()))
                val viewModel =
                    MapViewModel(
                        RefreshLocationUseCase(
                            object : LocationPermissionController {
                                override suspend fun currentStatus() = LocationPermissionStatus.Granted

                                override suspend fun requestPermission() = LocationPermissionStatus.Granted
                            },
                            object : LocationRepository {
                                override val state = MutableStateFlow<LocationState>(LocationState.Loading)

                                override suspend fun refresh() {
                                    locationRefreshes++
                                }
                            },
                        ),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                        dataLoadingEnabled = false,
                    )
                val latestBounds = bounds(127.1, 37.6)
                viewModel.start()
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 16.0))
                viewModel.onViewportChanged(EmotionMapViewport(latestBounds, 16.0))
                viewModel.refreshEmotionPins()
                viewModel.onConnectivityChanged(false)
                viewModel.onConnectivityChanged(true)
                advanceTimeBy(700)
                runCurrent()
                assertTrue(repository.requestedBounds.isEmpty())
                assertEquals(0, locationRefreshes)

                viewModel.enableDataLoading()
                viewModel.start()
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(latestBounds.expandedForPinQuery()), repository.requestedBounds)
                assertEquals(1, locationRefreshes)

                viewModel.enableDataLoading()
                viewModel.start()
                runCurrent()
                assertEquals(1, repository.requestedBounds.size)
                assertEquals(1, locationRefreshes)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `위치 대기 중 중복 요청은 막고 준비 해제 시 현재 요청을 취소한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var refreshes = 0
                var cancelled = false
                val locationState = MutableStateFlow<LocationState>(LocationState.Loading)
                val locationRepository =
                    object : LocationRepository {
                        override val state = locationState

                        override suspend fun refresh() {
                            refreshes++
                            try {
                                awaitCancellation()
                            } catch (cancellation: CancellationException) {
                                cancelled = true
                                throw cancellation
                            }
                        }
                    }
                val permission =
                    object : LocationPermissionController {
                        override suspend fun currentStatus() = LocationPermissionStatus.Granted

                        override suspend fun requestPermission() = LocationPermissionStatus.Granted
                    }
                val pins = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        RefreshLocationUseCase(permission, locationRepository),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        dataLoadingEnabled = false,
                    )

                viewModel.updateReadiness(true, 1)
                runCurrent()
                assertTrue(viewModel.uiModel.value.isRequestingLocation)
                viewModel.onMyLocationClick()
                runCurrent()
                assertEquals(1, refreshes)

                viewModel.updateReadiness(false, 1)
                runCurrent()

                assertTrue(cancelled)
                assertFalse(viewModel.uiModel.value.isRequestingLocation)
                assertEquals(1, refreshes)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `닉네임 확인 후 보류한 지도 영역으로 클러스터 조회를 시작한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var regionQueries = 0
                val pins = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    regionQueries++
                                    EmotionRegionResult.Success(emptyList())
                                },
                            ),
                        dataLoadingEnabled = false,
                    )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 10.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(0, regionQueries)

                viewModel.enableDataLoading()
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, regionQueries)
                assertTrue(pins.requestedBounds.isEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `기본 한달 필터와 내 기록 필터 적용 후에도 클러스터를 조회한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val filters = mutableListOf<EmotionSearchFilter?>()
                val pins = QueuedEmotionMapRepository()
                val region = EmotionRegion("city", "서울", 127.0, 37.5, 3, null)
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository(onFilter = { filters += it }) { _, _, _ ->
                                    EmotionRegionResult.Success(listOf(region))
                                },
                            ),
                    )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 10.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(MapFilterSettings.DEFAULT.toSearchFilter(), filters.single())
                assertEquals(EmotionRegionLevel.SIGUNGU, viewModel.uiModel.value.displayedRegionLevel)
                viewModel.openFilter()
                viewModel.selectFilterScope(FilterScope.Mine)
                viewModel.applyFilter()
                runCurrent()
                assertEquals(MapFilterScope.MY, filters.last()?.scope)
                assertEquals(EmotionRegionLevel.SIGUNGU, viewModel.uiModel.value.displayedRegionLevel)
                assertEquals(
                    listOf("city"),
                    viewModel.uiModel.value.regionClusters
                        .map { it.id },
                )
                assertFalse(viewModel.filterUiModel.value.visible)
                assertTrue(
                    viewModel.uiModel.value.emotionPins
                        .isEmpty(),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `핀과 클러스터 전환은 요청 실패 시 기존 데이터를 유지하고 성공 후 교체한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val pinResponse = CompletableDeferred<EmotionMapPageResult>()
                val pins =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(listOf(pin(1, 127.02, 37.55)))),
                        pinResponse,
                    )
                val failedRegion = CompletableDeferred<EmotionRegionResult>()
                val nextRegion = CompletableDeferred<EmotionRegionResult>()
                val responses = ArrayDeque(listOf(failedRegion, nextRegion))
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository {
                                    _,
                                    _,
                                    _,
                                    ->
                                    responses.removeFirst().await()
                                },
                            ),
                    )
                val area = bounds(127.0, 37.5)
                viewModel.onViewportChanged(EmotionMapViewport(area, 11.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                failedRegion.complete(EmotionRegionResult.Failure)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                viewModel.retryRegionClusters()
                advanceTimeBy(700)
                runCurrent()
                nextRegion.complete(EmotionRegionResult.Success(listOf(EmotionRegion("a", "지역", 127.0, 37.5, 3, null))))
                runCurrent()
                assertEquals(emptyList(), viewModel.pinIds())
                assertEquals(
                    listOf("a"),
                    viewModel.uiModel.value.regionClusters
                        .map { it.id },
                )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(128.0, 37.5), 11.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(
                    listOf("a"),
                    viewModel.uiModel.value.regionClusters
                        .map { it.id },
                )
                pinResponse.complete(page(listOf(pin(2, 128.02, 37.55))))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `진행 중 서울에서 세계를 거쳐 부산으로 이동해도 요청을 공유하고 최신 화면만 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val response = CompletableDeferred<EmotionRegionResult>()
                val pins = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    calls++
                                    response.await()
                                },
                            ),
                    )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 10.0))
                runCurrent()
                assertEquals(1, calls)
                viewModel.onViewportChanged(
                    EmotionMapViewport(
                        EmotionMapBounds(-180.0, -85.0, 180.0, 85.0),
                        10.0,
                    ),
                )
                viewModel.onViewportChanged(EmotionMapViewport(bounds(129.0, 35.0), 10.0))
                runCurrent()
                assertEquals(1, calls)
                response.complete(
                    EmotionRegionResult.Success(
                        listOf(
                            EmotionRegion("seoul", "서울", 127.05, 37.55, 3, null),
                            EmotionRegion("busan", "부산", 129.05, 35.05, 5, null),
                        ),
                    ),
                )
                runCurrent()
                assertEquals(
                    listOf("busan"),
                    viewModel.uiModel.value.regionClusters
                        .map { it.id },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `넓어진 조회 범위의 주변 핀도 표시하되 보관 상한은 실제 화면 기준으로 적용한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val surrounding = (1L..1_001L).map { pin(it, 127.115, 37.55) }
                val visible = pin(2_000, 127.05, 37.55)
                val repository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(surrounding + visible)),
                        CompletableDeferred(page(surrounding + visible)),
                    )
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()

                val query = repository.requestedBounds.single()!!
                assertEquals(126.975, query.minLongitude, 0.000001)
                assertEquals(127.125, query.maxLongitude, 0.000001)
                assertEquals(37.475, query.minLatitude, 0.000001)
                assertEquals(37.625, query.maxLatitude, 0.000001)
                assertEquals(MAXIMUM_RETAINED_EMOTION_PINS, vm.uiModel.value.emotionPins.size)
                assertTrue(visible.id in vm.pinIds())
                assertEquals(999, vm.pinIds().count { it != visible.id })

                vm.refreshEmotionPins()
                runCurrent()
                assertEquals(query, repository.requestedBounds.last())
                assertEquals(null, vm.uiModel.value.emotionPinsError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `캐시가 없어도 작은 왕복 이동 후 동일한 핀 목록을 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val first = pin(1, 127.02, 37.55)
                val shared = pin(2, 127.08, 37.55)
                var requestCount = 0
                val repository =
                    object : EmotionMapRepository {
                        override fun findSnapshot(
                            bounds: EmotionMapBounds,
                            groupId: String?,
                        ): EmotionMapSnapshot? = null

                        override suspend fun findPage(
                            bounds: EmotionMapBounds?,
                            groupId: String?,
                            cursor: String?,
                            forceRefresh: Boolean,
                        ): EmotionMapPageResult {
                            requestCount++
                            return page(if (requestCount == 2) listOf(shared) else listOf(first, shared))
                        }
                    }
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val originalBounds = bounds(127.0, 37.5)
                vm.onViewportChanged(originalBounds)
                advanceTimeBy(700)
                runCurrent()
                val originalPins = vm.uiModel.value.emotionPins

                vm.onViewportChanged(bounds(127.05, 37.5))
                advanceTimeBy(700)
                runCurrent()
                assertSame(originalPins, vm.uiModel.value.emotionPins)

                vm.onViewportChanged(originalBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(3, requestCount)
                assertSame(originalPins, vm.uiModel.value.emotionPins)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `화면 핀은 보관 상한을 넘어도 유지하고 이동 후에는 주변 핀부터 정리한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val visiblePins = (1L..1_002L).map { pin(it, 127.02, 37.55) }
                val nextPin = pin(2_000, 128.02, 37.55)
                val repository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(visiblePins)),
                        CompletableDeferred(page(listOf(nextPin))),
                    )
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val originalBounds = bounds(127.0, 37.5)
                vm.onViewportChanged(originalBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1_002, vm.uiModel.value.emotionPins.size)

                vm.onViewportChanged(bounds(128.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(MAXIMUM_RETAINED_EMOTION_PINS, vm.uiModel.value.emotionPins.size)
                assertTrue(nextPin.id in vm.pinIds())

                vm.onViewportChanged(originalBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(visiblePins.map { it.id }, vm.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `페이지 요청은 화면 발행 간격을 기다리지 않고 중간 핀은 최신 페이지로 묶는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val firstPage = CompletableDeferred<EmotionMapPageResult>()
                val secondPage = CompletableDeferred<EmotionMapPageResult>()
                val thirdPage = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(firstPage, secondPage, thirdPage)
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                        pinSnapshotIntervalMillis = 300,
                    )
                val area = bounds(127.0, 37.5)

                viewModel.onViewportChanged(area)
                advanceTimeBy(700)
                runCurrent()
                firstPage.complete(page(listOf(pin(1, 127.01, 37.51)), hasNext = true))
                runCurrent()

                assertEquals(listOf(1L), viewModel.pinIds())
                assertEquals(2, repository.requestedBounds.size)

                secondPage.complete(
                    page(listOf(pin(2, 127.02, 37.52)), hasNext = true, nextCursor = "cursor-2"),
                )
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                assertEquals(3, repository.requestedBounds.size)

                advanceTimeBy(300)
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())

                thirdPage.complete(page(listOf(pin(3, 127.03, 37.53))))
                runCurrent()
                assertEquals(listOf(1L, 2L, 3L), viewModel.pinIds())
                advanceTimeBy(600)
                runCurrent()
                assertEquals(listOf(1L, 2L, 3L), viewModel.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `다른 영역으로 이동해도 주변 핀을 유지하고 목록 좌표로 반복 이동한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(listOf(pin(1, 127.02, 37.55)))),
                        CompletableDeferred(page(listOf(pin(2, 128.02, 37.55)))),
                    )
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                viewModel.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                assertTrue(viewModel.focusOnEmotion(1))
                assertEquals(1L, viewModel.uiModel.value.focusedEmotionId)

                viewModel.onViewportChanged(bounds(128.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
                assertEquals(1L, viewModel.uiModel.value.focusedEmotionId)
                assertTrue(viewModel.focusOnEmotion(1))

                val coordinate = GeoCoordinate(37.55, 127.02)
                assertTrue(viewModel.focusOnEmotion(1, coordinate))
                val command = viewModel.uiModel.value.cameraCommand!!
                assertEquals(MapCameraActionUiModel.MoveToCoordinate, command.action)
                assertEquals(coordinate.latitude, command.latitude)
                assertEquals(coordinate.longitude, command.longitude)
                assertEquals(0.33, command.verticalPosition, 0.000001)
                assertTrue(viewModel.focusOnEmotion(1, coordinate))
                val repeatedCommand = viewModel.uiModel.value.cameraCommand
                assertEquals(command.id + 1, repeatedCommand?.id)

                viewModel.clearFocusedEmotion()
                assertEquals(null, viewModel.uiModel.value.focusedEmotionId)
                assertEquals(repeatedCommand, viewModel.uiModel.value.cameraCommand)
                assertTrue(viewModel.focusOnEmotion(2))
                assertEquals(2L, viewModel.uiModel.value.focusedEmotionId)
                assertTrue(viewModel.focusOnEmotion(1, coordinate))
                viewModel.focusOnCoordinate(coordinate)
                assertEquals(1L, viewModel.uiModel.value.focusedEmotionId)
                viewModel.onMyLocationClick()
                assertEquals(1L, viewModel.uiModel.value.focusedEmotionId)
                val lastCommand = viewModel.uiModel.value.cameraCommand

                viewModel.onEmotionHidden(1)
                assertEquals(null, viewModel.uiModel.value.focusedEmotionId)
                assertFalse(viewModel.focusOnEmotion(1, coordinate))
                assertEquals(lastCommand, viewModel.uiModel.value.cameraCommand)

                viewModel.focusOnCoordinate(coordinate)
                val centeredCommand = viewModel.uiModel.value.cameraCommand!!
                assertEquals(0.5, centeredCommand.verticalPosition)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `스탬프 위치 선택 중에도 진행 중인 조회와 지도 이동 후 핀 갱신을 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val firstResponse = CompletableDeferred<EmotionMapPageResult>()
                val movedResponse = CompletableDeferred<EmotionMapPageResult>()
                val refreshResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(firstResponse, movedResponse, refreshResponse)
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val firstBounds = bounds(127.0, 37.5)
                val movedBounds = bounds(128.0, 37.5)

                viewModel.onViewportChanged(firstBounds)
                advanceTimeBy(700)
                runCurrent()
                viewModel.onRecordLocationPickingChanged(true)
                firstResponse.complete(page(listOf(pin(1, 127.02, 37.55))))
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())

                viewModel.onViewportChanged(movedBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(firstBounds.expandedForPinQuery(), movedBounds.expandedForPinQuery()),
                    repository.requestedBounds,
                )
                movedResponse.complete(page(listOf(pin(2, 128.02, 37.55))))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())

                viewModel.refreshEmotionPins()
                runCurrent()
                refreshResponse.complete(page(listOf(pin(2, 128.02, 37.55), pin(3, 128.03, 37.55))))
                runCurrent()
                assertEquals(listOf(1L, 2L, 3L), viewModel.pinIds())
                assertEquals(true, viewModel.uiModel.value.isRecordLocationPicking)
                assertEquals(1, repository.maximumConcurrentRequests)
                assertEquals(false, viewModel.uiModel.value.isLoadingEmotionPins)

                viewModel.onRecordLocationPickingChanged(false)
                assertEquals(listOf(1L, 2L, 3L), viewModel.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `등록 좌표로 이동할 때 핀 조회 완료를 기다리지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val coordinate = GeoCoordinate(37.567, 126.979)
                viewModel.onRecordLocationPickingChanged(true)
                viewModel.onRecordLocationPickingChanged(false)
                viewModel.focusOnCoordinate(coordinate)
                val command = viewModel.uiModel.value.cameraCommand!!
                assertEquals(MapCameraActionUiModel.MoveToCoordinate, command.action)
                assertEquals(coordinate.latitude, command.latitude)
                assertEquals(coordinate.longitude, command.longitude)
                assertEquals(false, viewModel.uiModel.value.isRecordLocationPicking)
                assertEquals(emptyList(), viewModel.pinIds())
                viewModel.focusOnCoordinate(coordinate)
                val nextCommand = viewModel.uiModel.value.cameraCommand
                assertEquals(command.id + 1, nextCommand?.id)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `이전 화면 응답을 캐시하고 현재 영역의 핀만 표시하며 재방문 시 재사용한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val firstResponse = CompletableDeferred<EmotionMapPageResult>()
                val latestResponse = CompletableDeferred<EmotionMapPageResult>()
                val refreshResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(firstResponse, latestResponse, refreshResponse)
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val firstBounds = bounds(127.0, 37.5)
                val latestBounds = bounds(127.05, 37.5)
                val outsideLatest = pin(1, 127.02, 37.55)
                val shared = pin(2, 127.08, 37.55)

                viewModel.onViewportChanged(firstBounds)
                advanceTimeBy(700)
                runCurrent()
                firstResponse.complete(page(listOf(outsideLatest, shared)))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())

                viewModel.onViewportChanged(latestBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(firstBounds.expandedForPinQuery(), latestBounds.expandedForPinQuery()),
                    repository.requestedBounds,
                )
                assertEquals(1, repository.maximumConcurrentRequests)

                latestResponse.complete(emptyPage())
                runCurrent()
                viewModel.onViewportChanged(firstBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
                assertEquals(2, repository.requestedBounds.size)
                assertEquals(false, viewModel.uiModel.value.isLoadingEmotionPins)

                viewModel.refreshEmotionPins()
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(
                        firstBounds.expandedForPinQuery(),
                        latestBounds.expandedForPinQuery(),
                        firstBounds.expandedForPinQuery(),
                    ),
                    repository.requestedBounds,
                )
                refreshResponse.complete(emptyPage())
                runCurrent()
                assertEquals(emptyList(), viewModel.uiModel.value.emotionPins)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `이전 화면의 부분 페이지 캐시는 재방문 시 전체 조회를 생략하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val firstResponse = CompletableDeferred<EmotionMapPageResult>()
                val firstPaginationResponse = CompletableDeferred<EmotionMapPageResult>()
                val latestResponse = CompletableDeferred<EmotionMapPageResult>()
                val revisitedResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository =
                    QueuedEmotionMapRepository(
                        firstResponse,
                        firstPaginationResponse,
                        latestResponse,
                        revisitedResponse,
                    )
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val firstBounds = bounds(127.0, 37.5)
                val latestBounds = bounds(128.0, 37.5)

                viewModel.onViewportChanged(firstBounds)
                advanceTimeBy(700)
                runCurrent()
                firstResponse.complete(page(listOf(pin(1, 127.02, 37.55)), hasNext = true))
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                viewModel.onViewportChanged(latestBounds)
                advanceTimeBy(700)
                runCurrent()
                latestResponse.complete(emptyPage())
                runCurrent()

                viewModel.onViewportChanged(firstBounds)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                advanceTimeBy(700)
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(
                        firstBounds.expandedForPinQuery(),
                        firstBounds.expandedForPinQuery(),
                        latestBounds.expandedForPinQuery(),
                        firstBounds.expandedForPinQuery(),
                    ),
                    repository.requestedBounds,
                )
                revisitedResponse.complete(page(listOf(pin(1, 127.02, 37.55), pin(2, 127.03, 37.55))))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `뷰포트 변경은 이전 요청을 취소하고 최신 경계를 즉시 요청한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val firstResponse = CompletableDeferred<EmotionMapPageResult>()
                val skippedResponse = CompletableDeferred<EmotionMapPageResult>()
                val latestResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(firstResponse, skippedResponse, latestResponse)
                val viewModel =
                    MapViewModel(
                        refreshLocation = noLocationRefreshUseCase(),
                        findEmotionMapPage = FindEmotionMapPageUseCase(repository),
                        findEmotionMapSnapshot = FindEmotionMapSnapshotUseCase(repository),
                    )
                val firstBounds = bounds(126.9, 37.5)
                val skippedBounds = bounds(127.0, 37.6)
                val latestBounds = bounds(127.1, 37.7)

                viewModel.onViewportChanged(firstBounds)
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(firstBounds.expandedForPinQuery()), repository.requestedBounds)

                viewModel.onViewportChanged(skippedBounds)
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(firstBounds.expandedForPinQuery(), skippedBounds.expandedForPinQuery()),
                    repository.requestedBounds,
                )

                viewModel.onViewportChanged(latestBounds)
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(
                        firstBounds.expandedForPinQuery(),
                        skippedBounds.expandedForPinQuery(),
                        latestBounds.expandedForPinQuery(),
                    ),
                    repository.requestedBounds,
                )

                latestResponse.complete(page(listOf(pin(3, 127.12, 37.72))))
                runCurrent()
                assertEquals(listOf(3L), viewModel.pinIds())

                firstResponse.complete(page(listOf(pin(1, 126.92, 37.52))))
                skippedResponse.complete(page(listOf(pin(2, 127.02, 37.62))))
                runCurrent()
                assertEquals(listOf(3L), viewModel.pinIds())
                assertEquals(false, viewModel.uiModel.value.isLoadingEmotionPins)

                viewModel.onViewportChanged(EmotionMapBounds(127.1, 37.6, 127.2, 37.5))
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(
                        firstBounds.expandedForPinQuery(),
                        skippedBounds.expandedForPinQuery(),
                        latestBounds.expandedForPinQuery(),
                    ),
                    repository.requestedBounds,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed pin refresh preserves displayed pins and clears its banner after retry succeeds`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val pins = listOf(pin(1, 127.02, 37.55))
                val retryResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(pins)),
                        CompletableDeferred(EmotionMapPageResult.Failure(EmotionMapFailure.Unavailable)),
                        retryResponse,
                    )
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                vm.refreshEmotionPins()
                runCurrent()
                assertEquals(listOf(1L), vm.pinIds())
                assertEquals(
                    MapFeedbackAction.RetryPins,
                    vm.uiModel.value
                        .primaryFeedback()
                        ?.action,
                )
                vm.retryEmotionPins()
                runCurrent()
                assertEquals(
                    MapFeedbackAction.RetryPins,
                    vm.uiModel.value
                        .primaryFeedback()
                        ?.action,
                )
                assertEquals("감정 핀을 불러오지 못했어요", vm.uiModel.value.emotionPinsError)
                retryResponse.complete(page(pins))
                runCurrent()
                assertEquals(null, vm.uiModel.value.primaryFeedback())
                assertEquals(null, vm.uiModel.value.emotionPinsError)
                assertEquals(listOf(1L), vm.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `reconnection retries failed map and pin reads once without changing existing pins`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val pins = listOf(pin(1, 127.02, 37.55))
                val repository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(pins)),
                        CompletableDeferred(page(pins)),
                    )
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                vm.onConnectivityChanged(true)
                assertEquals(1, repository.requestedBounds.size)
                vm.onMapError(MapErrorUiModel.StyleLoadFailed)
                vm.onConnectivityChanged(false)
                assertEquals(
                    null,
                    vm.uiModel.value
                        .primaryFeedback()
                        ?.action,
                )
                assertEquals(listOf(1L), vm.pinIds())
                assertEquals(0, vm.uiModel.value.mapRevision)
                vm.onConnectivityChanged(true)
                runCurrent()
                assertEquals(1, vm.uiModel.value.mapRevision)
                assertEquals(MapErrorUiModel.StyleLoadFailed, vm.uiModel.value.mapError)
                vm.onMapRecovered()
                assertEquals(null, vm.uiModel.value.primaryFeedback())
                assertEquals(listOf(1L), vm.pinIds())
                assertEquals(2, repository.requestedBounds.size)
                vm.onConnectivityChanged(true)
                runCurrent()
                assertEquals(2, repository.requestedBounds.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `실패한 지역 조회는 반복 요청을 막고 재연결 재시도 중 기존 안내를 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var requests = 0
                val retryResponse = CompletableDeferred<EmotionRegionResult>()
                val repository = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    if (++requests == 1) EmotionRegionResult.Failure else retryResponse.await()
                                },
                            ),
                    )
                val viewport = EmotionMapViewport(bounds(127.0, 37.5), 10.0)
                viewModel.onViewportChanged(viewport)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, requests)
                assertEquals("지역별 감정을 불러오지 못했어요", viewModel.uiModel.value.regionClustersError)

                viewModel.onViewportChanged(viewport)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, requests)

                viewModel.onConnectivityChanged(false)
                viewModel.onConnectivityChanged(true)
                assertEquals("지역별 감정을 불러오지 못했어요", viewModel.uiModel.value.regionClustersError)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(2, requests)
                assertTrue(viewModel.uiModel.value.isLoadingRegionClusters)
                assertEquals("지역별 감정을 불러오지 못했어요", viewModel.uiModel.value.regionClustersError)

                retryResponse.complete(
                    EmotionRegionResult.Success(
                        listOf(EmotionRegion("city", "서울", 127.0, 37.5, 3, null)),
                    ),
                )
                runCurrent()
                assertEquals(null, viewModel.uiModel.value.regionClustersError)
                assertEquals(1, viewModel.uiModel.value.regionClusters.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `explicit location failures show a banner and successful retry clears it`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val location =
                    MutableStateFlow<LocationState>(LocationState.Unavailable(LocationError.PermissionDenied))
                val refresh =
                    RefreshLocationUseCase(
                        object : LocationPermissionController {
                            override suspend fun currentStatus() = LocationPermissionStatus.Granted

                            override suspend fun requestPermission() = LocationPermissionStatus.Granted
                        },
                        object : LocationRepository {
                            override val state = location

                            override suspend fun refresh() = Unit
                        },
                    )
                val repository = QueuedEmotionMapRepository()
                val vm =
                    MapViewModel(
                        refresh,
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.start()
                runCurrent()
                assertEquals(null, vm.uiModel.value.primaryFeedback())
                vm.onMyLocationClick()
                runCurrent()
                assertEquals(
                    MapFeedbackAction.OpenSettings,
                    vm.uiModel.value
                        .primaryFeedback()
                        ?.action,
                )
                location.value = LocationState.Unavailable(LocationError.LocationTimeout)
                vm.onMyLocationClick()
                runCurrent()
                assertEquals(
                    MapFeedbackAction.RetryLocation,
                    vm.uiModel.value
                        .primaryFeedback()
                        ?.action,
                )
                location.value = LocationState.Available(CurrentLocation(37.5, 127.0, 1f, 0L))
                vm.onMyLocationClick()
                runCurrent()
                assertEquals(null, vm.uiModel.value.primaryFeedback())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `등록 핀은 즉시 보이고 빈 재조회에도 유지되며 서버 핀 확인 후 교체된다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val initial = CompletableDeferred<EmotionMapPageResult>()
                val stale = CompletableDeferred<EmotionMapPageResult>()
                val acknowledged = CompletableDeferred<EmotionMapPageResult>()
                val deleted = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(initial, stale, acknowledged, deleted)
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                initial.complete(page(listOf(pin(1, 127.01, 37.51))))
                runCurrent()
                vm.onRecordLocationPickingChanged(true)
                vm.onEmotionRegistered(pin(42, 127.02, 37.52).toUiModel())
                assertEquals(false, vm.uiModel.value.isRecordLocationPicking)
                assertEquals(listOf(1L, 42L), vm.pinIds())
                runCurrent()
                stale.complete(emptyPage())
                runCurrent()
                assertEquals(listOf(42L), vm.pinIds())
                vm.refreshEmotionPins()
                runCurrent()
                val serverPin = pin(42, 127.021, 37.521).copy(rotationDegrees = 45.0)
                acknowledged.complete(page(listOf(serverPin)))
                runCurrent()
                assertEquals(listOf(serverPin.toUiModel()), vm.uiModel.value.emotionPins)
                vm.refreshEmotionPins()
                runCurrent()
                deleted.complete(emptyPage())
                runCurrent()
                assertEquals(emptyList(), vm.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `오래된 캐시를 표시해도 등록 핀을 유지하고 숨긴 핀은 다시 나타나지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val initial = CompletableDeferred<EmotionMapPageResult>()
                val refreshing = CompletableDeferred<EmotionMapPageResult>()
                val viewportRefresh = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(initial, refreshing, viewportRefresh)
                val vm =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                vm.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                initial.complete(page(listOf(pin(1, 127.02, 37.52))))
                runCurrent()
                vm.onEmotionRegistered(pin(42, 127.03, 37.53).toUiModel())
                runCurrent()
                vm.onViewportChanged(EmotionMapBounds(127.01, 37.51, 127.08, 37.58))
                assertEquals(listOf(1L, 42L), vm.pinIds())
                vm.onEmotionHidden(42)
                refreshing.complete(emptyPage())
                runCurrent()
                advanceTimeBy(700)
                runCurrent()
                viewportRefresh.complete(emptyPage())
                runCurrent()
                assertEquals(emptyList(), vm.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `새 영역의 여러 페이지를 모두 불러와도 주변의 기존 핀을 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val initialResponse = CompletableDeferred<EmotionMapPageResult>()
                val firstViewportPage = CompletableDeferred<EmotionMapPageResult>()
                val secondViewportPage = CompletableDeferred<EmotionMapPageResult>()
                val repository =
                    QueuedEmotionMapRepository(
                        initialResponse,
                        firstViewportPage,
                        secondViewportPage,
                    )
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )
                val initialBounds = bounds(127.0, 37.5)
                val nextBounds = bounds(128.0, 37.5)

                viewModel.onViewportChanged(initialBounds)
                advanceTimeBy(700)
                runCurrent()
                initialResponse.complete(page(listOf(pin(1, 127.02, 37.52))))
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())

                viewModel.onViewportChanged(nextBounds)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())

                firstViewportPage.complete(page(listOf(pin(2, 128.02, 37.52)), hasNext = true))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
                assertTrue(viewModel.uiModel.value.isLoadingMoreEmotionPins)

                secondViewportPage.complete(page(listOf(pin(3, 128.03, 37.53))))
                runCurrent()
                assertEquals(listOf(1L, 2L, 3L), viewModel.pinIds())
                assertFalse(viewModel.uiModel.value.isLoadingMoreEmotionPins)
                assertEquals(false, viewModel.uiModel.value.hasPartialEmotionPins)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `다중 페이지 조회 실패 시 기존 핀과 먼저 받은 페이지를 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val initialResponse = CompletableDeferred<EmotionMapPageResult>()
                val firstViewportPage = CompletableDeferred<EmotionMapPageResult>()
                val failedViewportPage = CompletableDeferred<EmotionMapPageResult>()
                val repository =
                    QueuedEmotionMapRepository(
                        initialResponse,
                        firstViewportPage,
                        failedViewportPage,
                    )
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                    )

                viewModel.onViewportChanged(bounds(127.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                initialResponse.complete(page(listOf(pin(1, 127.02, 37.52))))
                runCurrent()

                viewModel.onViewportChanged(bounds(128.0, 37.5))
                advanceTimeBy(700)
                runCurrent()
                firstViewportPage.complete(page(listOf(pin(2, 128.02, 37.52)), hasNext = true))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
                assertTrue(viewModel.uiModel.value.isLoadingMoreEmotionPins)

                failedViewportPage.complete(EmotionMapPageResult.Failure(EmotionMapFailure.Unavailable))
                runCurrent()
                assertEquals(listOf(1L, 2L), viewModel.pinIds())
                assertFalse(viewModel.uiModel.value.isLoadingMoreEmotionPins)
                assertTrue(viewModel.uiModel.value.hasPartialEmotionPins)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `실제 핀 줌의 지연 조회 중 클러스터를 유지하고 핀 성공 후 교체한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val area = bounds(127.0, 37.5)
                val delayedPinPage = CompletableDeferred<EmotionMapPageResult>()
                val pinRepository = QueuedEmotionMapRepository(delayedPinPage)
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pinRepository),
                        FindEmotionMapSnapshotUseCase(pinRepository),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    EmotionRegionResult.Success(
                                        listOf(EmotionRegion("district", "강남구", 127.05, 37.55, 4, null)),
                                    )
                                },
                            ),
                    )

                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(EmotionRegionLevel.SIGUNGU, viewModel.uiModel.value.displayedRegionLevel)
                assertEquals(
                    "강남구",
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .name,
                )

                viewModel.onViewportChanged(EmotionMapViewport(area, 15.5))
                assertEquals(EmotionRegionLevel.SIGUNGU, viewModel.uiModel.value.displayedRegionLevel)
                assertEquals(
                    "강남구",
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .name,
                )

                advanceTimeBy(700)
                runCurrent()
                assertTrue(viewModel.uiModel.value.isLoadingEmotionPins)
                assertEquals(EmotionRegionLevel.SIGUNGU, viewModel.uiModel.value.displayedRegionLevel)
                assertEquals(
                    "강남구",
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .name,
                )

                delayedPinPage.complete(page(listOf(pin(7, 127.02, 37.55))))
                runCurrent()
                assertEquals(listOf(7L), viewModel.pinIds())
                assertEquals(null, viewModel.uiModel.value.displayedRegionLevel)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `핀 줌 조회 실패 뒤에도 클러스터를 남기지 않고 재시도 핀을 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val area = bounds(127.0, 37.5)
                val pinRepository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(EmotionMapPageResult.Failure(EmotionMapFailure.Unavailable)),
                        CompletableDeferred(page(listOf(pin(8, 127.02, 37.55)))),
                    )
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pinRepository),
                        FindEmotionMapSnapshotUseCase(pinRepository),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    EmotionRegionResult.Success(
                                        listOf(EmotionRegion("district", "강남구", 127.05, 37.55, 4, null)),
                                    )
                                },
                            ),
                    )

                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                advanceTimeBy(700)
                runCurrent()
                viewModel.onViewportChanged(EmotionMapViewport(area, 15.5))
                advanceTimeBy(700)
                runCurrent()

                assertEquals(null, viewModel.uiModel.value.displayedRegionLevel)
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
                assertEquals("감정 핀을 불러오지 못했어요", viewModel.uiModel.value.emotionPinsError)

                viewModel.retryEmotionPins()
                runCurrent()
                assertEquals(listOf(8L), viewModel.pinIds())
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
                assertEquals(null, viewModel.uiModel.value.emotionPinsError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `핀 줌의 정상 빈 응답은 이전 클러스터를 복원하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val area = bounds(127.0, 37.5)
                val pinRepository = QueuedEmotionMapRepository(CompletableDeferred(emptyPage()))
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pinRepository),
                        FindEmotionMapSnapshotUseCase(pinRepository),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    EmotionRegionResult.Success(
                                        listOf(EmotionRegion("district", "강남구", 127.05, 37.55, 4, null)),
                                    )
                                },
                            ),
                    )

                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                advanceTimeBy(700)
                runCurrent()
                viewModel.onViewportChanged(EmotionMapViewport(area, 15.5))
                advanceTimeBy(700)
                runCurrent()

                assertEquals(emptyList(), viewModel.pinIds())
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
                assertEquals(null, viewModel.uiModel.value.displayedRegionLevel)
                assertEquals(null, viewModel.uiModel.value.emotionPinsError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `늦은 지역 응답은 새 계층을 덮지 않고 가까운 줌에서 핀으로 전환한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val old = CompletableDeferred<EmotionRegionResult>()
                val current = CompletableDeferred<EmotionRegionResult>()
                val pinRepository = QueuedEmotionMapRepository(CompletableDeferred(page(listOf(pin(7, 127.02, 37.55)))))
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pinRepository),
                        FindEmotionMapSnapshotUseCase(pinRepository),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, level, _ ->
                                    when (level) {
                                        EmotionRegionLevel.SIDO -> old.await()
                                        EmotionRegionLevel.SIGUNGU -> current.await()
                                    }
                                },
                            ),
                    )
                val area = bounds(127.0, 37.5)
                viewModel.onViewportChanged(EmotionMapViewport(area, 8.0))
                advanceTimeBy(700)
                runCurrent()
                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                advanceTimeBy(700)
                runCurrent()
                old.complete(EmotionRegionResult.Success(listOf(EmotionRegion("old", "서울", 127.0, 37.5, 1, null))))
                runCurrent()
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
                current.complete(
                    EmotionRegionResult.Success(
                        listOf(EmotionRegion("new", "강남구", 127.0, 37.5, 48, EmotionState.ANGRY)),
                    ),
                )
                runCurrent()
                assertEquals(
                    "강남구",
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .name,
                )
                viewModel.focusOnRegionCluster("new")
                assertEquals(
                    11.0,
                    viewModel.uiModel.value.cameraCommand
                        ?.value,
                )
                viewModel.onViewportChanged(EmotionMapViewport(area, 11.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
                assertEquals(listOf(7L), viewModel.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `전국 지역 응답은 같은 단계 이동 중 재사용하고 최신 화면 범위로 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                listOf(8.0, 10.0).forEach { zoom ->
                    val firstBounds = bounds(127.0, 37.5)
                    val latestBounds = bounds(128.0, 37.5)
                    val response = CompletableDeferred<EmotionRegionResult>()
                    val requests = mutableListOf<EmotionMapBounds>()
                    val regions =
                        listOf(
                            EmotionRegion("west", "서쪽", 127.05, 37.55, 1, null),
                            EmotionRegion("east", "동쪽", 128.05, 37.55, 2, null),
                        )
                    val repository = QueuedEmotionMapRepository()
                    val viewModel =
                        MapViewModel(
                            noLocationRefreshUseCase(),
                            FindEmotionMapPageUseCase(repository),
                            FindEmotionMapSnapshotUseCase(repository),
                            filterSettingsRepository = allPeriodSettingsRepository(),
                            findRegions =
                                FindEmotionRegionsUseCase(
                                    regionRepository { bounds, _, _ ->
                                        requests += bounds
                                        response.await()
                                    },
                                ),
                        )
                    runCurrent()

                    viewModel.onViewportChanged(EmotionMapViewport(firstBounds, zoom))
                    runCurrent()
                    assertEquals(1, requests.size)

                    viewModel.onViewportChanged(EmotionMapViewport(latestBounds, zoom))
                    runCurrent()
                    assertEquals(listOf(firstBounds), requests)

                    response.complete(EmotionRegionResult.Success(regions))
                    runCurrent()
                    assertEquals(
                        listOf("east"),
                        viewModel.uiModel.value.regionClusters
                            .map { it.id },
                    )

                    viewModel.onViewportChanged(EmotionMapViewport(bounds(128.01, 37.5), zoom))
                    runCurrent()
                    assertEquals(listOf(firstBounds), requests)
                    assertEquals(
                        listOf("east"),
                        viewModel.uiModel.value.regionClusters
                            .map { it.id },
                    )
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `같은 단계에서 지역 조회가 실패하면 이동은 재시도하지 않고 명시적 재시도는 즉시 요청한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val failed = CompletableDeferred<EmotionRegionResult>()
                val retry = CompletableDeferred<EmotionRegionResult>()
                var requests = 0
                val repository = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, _, _ ->
                                    if (requests++ == 0) failed.await() else retry.await()
                                },
                            ),
                    )
                runCurrent()

                viewModel.onViewportChanged(EmotionMapViewport(bounds(127.0, 37.5), 8.0))
                runCurrent()
                assertEquals(1, requests)
                failed.complete(EmotionRegionResult.Failure)
                runCurrent()
                assertTrue(viewModel.uiModel.value.regionClustersError != null)

                viewModel.onViewportChanged(EmotionMapViewport(bounds(128.0, 37.5), 8.0))
                runCurrent()
                assertEquals(1, requests)
                assertTrue(viewModel.uiModel.value.regionClustersError != null)

                viewModel.retryRegionClusters()
                runCurrent()
                assertEquals(2, requests)
                retry.complete(EmotionRegionResult.Success(emptyList()))
                runCurrent()
                assertEquals(null, viewModel.uiModel.value.regionClustersError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `핀 시도 시군구 전환은 카메라 idle 뒤 대기 없이 조회한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val area = bounds(127.0, 37.5)
                val pins =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(emptyPage()),
                        CompletableDeferred(emptyPage()),
                    )
                val requestedLevels = mutableListOf<EmotionRegionLevel>()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository { _, level, _ ->
                                    requestedLevels += level
                                    EmotionRegionResult.Success(emptyList())
                                },
                            ),
                    )
                runCurrent()

                viewModel.onViewportChanged(EmotionMapViewport(area, 11.0))
                runCurrent()
                assertEquals(1, pins.requestedBounds.size)

                viewModel.onViewportChanged(EmotionMapViewport(area, 8.0))
                runCurrent()
                assertEquals(listOf(EmotionRegionLevel.SIDO), requestedLevels)

                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                runCurrent()
                assertEquals(listOf(EmotionRegionLevel.SIDO, EmotionRegionLevel.SIGUNGU), requestedLevels)

                viewModel.onViewportChanged(EmotionMapViewport(bounds(128.0, 37.5), 11.0))
                runCurrent()
                assertEquals(2, pins.requestedBounds.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지역 재방문은 캐시를 즉시 표시하고 새로고침은 서버 응답을 기다린다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val area = bounds(127.0, 37.5)
                val cached = EmotionRegion("a", "강남구", 127.0, 37.5, 3, null)
                var networkCalls = 0
                var refreshCalls = 0
                val refresh = CompletableDeferred<EmotionRegionResult>()
                val regionsRepository =
                    regionRepository(
                        snapshot = { listOf(cached) },
                        find = { _, _, forceRefresh ->
                            if (forceRefresh) {
                                refreshCalls++
                                refresh.await()
                            } else {
                                networkCalls++
                                EmotionRegionResult.Success(emptyList())
                            }
                        },
                    )
                val repository = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(repository),
                        FindEmotionMapSnapshotUseCase(repository),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions = FindEmotionRegionsUseCase(regionsRepository),
                        findRegionSnapshot = FindEmotionRegionSnapshotUseCase(regionsRepository),
                    )
                runCurrent()
                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                assertEquals(
                    3L,
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .count,
                )
                advanceTimeBy(700)
                runCurrent()
                assertEquals(0, networkCalls)

                viewModel.onViewportChanged(EmotionMapViewport(bounds(128.0, 37.5), 10.0))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(emptyList(), viewModel.uiModel.value.regionClusters)
                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                assertEquals(
                    3L,
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .count,
                )
                assertEquals(0, networkCalls)

                viewModel.refreshEmotionPins()
                assertEquals(
                    3L,
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .count,
                )
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, refreshCalls)
                refresh.complete(EmotionRegionResult.Success(listOf(cached.copy(count = 7))))
                runCurrent()
                assertEquals(
                    7L,
                    viewModel.uiModel.value.regionClusters
                        .single()
                        .count,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `다른 계층 조회 중과 실패 후에도 보이는 지역의 계층으로 확대한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val city = EmotionRegion("city", "서울", 127.0, 37.5, 3, null)
                val first = CompletableDeferred<EmotionRegionResult>(EmotionRegionResult.Success(listOf(city)))
                val failed = CompletableDeferred<EmotionRegionResult>()
                val next = CompletableDeferred<EmotionRegionResult>()
                val responses = ArrayDeque(listOf(first, failed, next))
                val pins = QueuedEmotionMapRepository()
                val viewModel =
                    MapViewModel(
                        noLocationRefreshUseCase(),
                        FindEmotionMapPageUseCase(pins),
                        FindEmotionMapSnapshotUseCase(pins),
                        filterSettingsRepository = allPeriodSettingsRepository(),
                        findRegions =
                            FindEmotionRegionsUseCase(
                                regionRepository {
                                    _,
                                    _,
                                    _,
                                    ->
                                    responses.removeFirst().await()
                                },
                            ),
                    )
                val area = bounds(127.0, 37.5)
                viewModel.onViewportChanged(EmotionMapViewport(area, 8.0))
                advanceTimeBy(700)
                runCurrent()
                viewModel.onViewportChanged(EmotionMapViewport(area, 10.0))
                advanceTimeBy(700)
                runCurrent()
                viewModel.focusOnRegionCluster("city")
                assertEquals(
                    9.0,
                    viewModel.uiModel.value.cameraCommand
                        ?.value,
                )
                failed.complete(EmotionRegionResult.Failure)
                runCurrent()
                viewModel.focusOnRegionCluster("city")
                assertEquals(
                    9.0,
                    viewModel.uiModel.value.cameraCommand
                        ?.value,
                )
                assertEquals(EmotionRegionLevel.SIDO, viewModel.uiModel.value.displayedRegionLevel)

                viewModel.retryRegionClusters()
                advanceTimeBy(700)
                runCurrent()
                next.complete(EmotionRegionResult.Success(listOf(city.copy(id = "district", name = "강남구"))))
                runCurrent()
                viewModel.focusOnRegionCluster("district")
                assertEquals(
                    11.0,
                    viewModel.uiModel.value.cameraCommand
                        ?.value,
                )
                assertEquals(EmotionRegionLevel.SIGUNGU, viewModel.uiModel.value.displayedRegionLevel)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun allPeriodSettingsRepository(): MapFilterSettingsRepository =
        object : MapFilterSettingsRepository {
            override suspend fun read() = MapFilterSettings(period = MapFilterPeriod.ALL)

            override suspend fun save(settings: MapFilterSettings) = Unit
        }

    private fun regionRepository(
        snapshot: (EmotionRegionLevel) -> List<EmotionRegion>? = { null },
        onFilter: (EmotionSearchFilter?) -> Unit = {},
        find: suspend (EmotionMapBounds, EmotionRegionLevel, Boolean) -> EmotionRegionResult,
    ): EmotionRegionMapRepository =
        object : EmotionRegionMapRepository {
            override suspend fun findRegions(
                bounds: EmotionMapBounds,
                level: EmotionRegionLevel,
                groupId: String?,
                forceRefresh: Boolean,
                filter: EmotionSearchFilter?,
            ): EmotionRegionResult {
                onFilter(filter)
                return find(bounds, level, forceRefresh)
            }

            override fun findSnapshot(
                level: EmotionRegionLevel,
                groupId: String?,
                filter: EmotionSearchFilter?,
            ) = snapshot(level)
        }

    private fun bounds(
        minLongitude: Double,
        minLatitude: Double,
    ) = EmotionMapBounds(
        minLongitude = minLongitude,
        minLatitude = minLatitude,
        maxLongitude = minLongitude + 0.1,
        maxLatitude = minLatitude + 0.1,
    )

    private fun emptyPage() =
        EmotionMapPageResult.Success(
            EmotionMapPage(
                pins = emptyList(),
                hasNext = false,
                nextCursor = null,
                invalidItemCount = 0,
            ),
        )

    private fun page(
        pins: List<EmotionMapPin>,
        hasNext: Boolean = false,
        nextCursor: String? = if (hasNext) "next" else null,
    ) = EmotionMapPageResult.Success(EmotionMapPage(pins, hasNext, nextCursor, 0))

    private fun pin(
        id: Long,
        longitude: Double,
        latitude: Double,
    ) = EmotionMapPin(id, longitude, latitude, "2026-09-27T00:00:00Z", EmotionState.ANGRY, 0.0, null)

    private fun MapViewModel.pinIds() = uiModel.value.emotionPins.map { it.id }

    private fun noLocationRefreshUseCase() =
        RefreshLocationUseCase(
            permissionController =
                object : LocationPermissionController {
                    override suspend fun currentStatus() = LocationPermissionStatus.Granted

                    override suspend fun requestPermission() = LocationPermissionStatus.Granted
                },
            repository =
                object : LocationRepository {
                    override val state = MutableStateFlow<LocationState>(LocationState.Loading)

                    override suspend fun refresh() = Unit
                },
        )

    private class QueuedEmotionMapRepository(
        private vararg val responses: CompletableDeferred<EmotionMapPageResult>,
    ) : EmotionMapRepository {
        val requestedBounds = mutableListOf<EmotionMapBounds?>()
        private val cachedRegions = mutableListOf<CachedRegion>()
        var maximumConcurrentRequests = 0
            private set
        private var activeRequests = 0

        override suspend fun findPage(
            bounds: EmotionMapBounds?,
            groupId: String?,
            cursor: String?,
            forceRefresh: Boolean,
        ): EmotionMapPageResult {
            if (!forceRefresh && cursor == null && bounds != null) {
                cachedRegions.lastOrNull { it.complete && it.bounds.contains(bounds) }?.let { cached ->
                    return EmotionMapPageResult.Success(
                        cached.page.copy(pins = cached.page.pins.filter { bounds.contains(it.longitude, it.latitude) }),
                    )
                }
            }
            requestedBounds += bounds
            activeRequests++
            maximumConcurrentRequests = maxOf(maximumConcurrentRequests, activeRequests)
            return try {
                val result = responses[requestedBounds.lastIndex].await()
                if (bounds != null && cursor == null && result is EmotionMapPageResult.Success) {
                    cachedRegions.removeAll { it.bounds == bounds }
                    cachedRegions += CachedRegion(bounds, result.page, complete = !result.page.hasNext)
                }
                result
            } finally {
                activeRequests--
            }
        }

        override fun findSnapshot(
            bounds: EmotionMapBounds,
            groupId: String?,
        ) = cachedRegions
            .filter { it.bounds.intersects(bounds) }
            .flatMap { it.page.pins }
            .filter { bounds.contains(it.longitude, it.latitude) }
            .distinctBy { it.id }
            .takeIf { it.isNotEmpty() }
            ?.let(::EmotionMapSnapshot)

        private data class CachedRegion(
            val bounds: EmotionMapBounds,
            val page: EmotionMapPage,
            val complete: Boolean,
        )
    }
}

private fun EmotionMapBounds.contains(other: EmotionMapBounds): Boolean =
    minLongitude <= other.minLongitude && minLatitude <= other.minLatitude &&
        maxLongitude >= other.maxLongitude && maxLatitude >= other.maxLatitude

private fun EmotionMapBounds.intersects(other: EmotionMapBounds): Boolean =
    minLongitude <= other.maxLongitude && maxLongitude >= other.minLongitude &&
        minLatitude <= other.maxLatitude && maxLatitude >= other.minLatitude

private fun EmotionMapBounds.contains(
    longitude: Double,
    latitude: Double,
): Boolean = longitude in minLongitude..maxLongitude && latitude in minLatitude..maxLatitude
