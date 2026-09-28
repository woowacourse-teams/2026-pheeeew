package com.pheeeew.feature.screens.map

import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapFailure
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelViewportTest {
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
                viewModel.onViewportChanged(latestBounds)
                advanceTimeBy(700)
                runCurrent()
                firstResponse.complete(page(listOf(outsideLatest, shared)))
                runCurrent()

                assertEquals(listOf(2L), viewModel.pinIds())
                assertEquals(listOf<EmotionMapBounds?>(firstBounds, latestBounds), repository.requestedBounds)
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
                    listOf<EmotionMapBounds?>(firstBounds, latestBounds, firstBounds),
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
                val latestResponse = CompletableDeferred<EmotionMapPageResult>()
                val revisitedResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(firstResponse, latestResponse, revisitedResponse)
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
                viewModel.onViewportChanged(latestBounds)
                advanceTimeBy(700)
                runCurrent()
                firstResponse.complete(page(listOf(pin(1, 127.02, 37.55)), hasNext = true))
                runCurrent()
                latestResponse.complete(emptyPage())
                runCurrent()

                viewModel.onViewportChanged(firstBounds)
                runCurrent()
                assertEquals(listOf(1L), viewModel.pinIds())
                advanceTimeBy(700)
                runCurrent()
                assertEquals(
                    listOf<EmotionMapBounds?>(firstBounds, latestBounds, firstBounds),
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
    fun `뷰포트 변경은 700ms 디바운스 후 최신 경계만 순차 요청한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val firstResponse = CompletableDeferred<EmotionMapPageResult>()
                val latestResponse = CompletableDeferred<EmotionMapPageResult>()
                val repository = QueuedEmotionMapRepository(firstResponse, latestResponse)
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
                advanceTimeBy(699)
                runCurrent()
                assertEquals(emptyList<EmotionMapBounds?>(), repository.requestedBounds)

                advanceTimeBy(1)
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(firstBounds), repository.requestedBounds)

                viewModel.onViewportChanged(skippedBounds)
                runCurrent()
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(firstBounds), repository.requestedBounds)

                viewModel.onViewportChanged(latestBounds)
                runCurrent()
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(firstBounds), repository.requestedBounds)

                firstResponse.complete(emptyPage())
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(firstBounds, latestBounds), repository.requestedBounds)
                assertEquals(1, repository.maximumConcurrentRequests)

                latestResponse.complete(emptyPage())
                runCurrent()
                assertEquals(1, repository.maximumConcurrentRequests)
                assertEquals(false, viewModel.uiModel.value.isLoadingEmotionPins)

                viewModel.onViewportChanged(EmotionMapBounds(127.1, 37.6, 127.2, 37.5))
                advanceTimeBy(700)
                runCurrent()
                assertEquals(listOf<EmotionMapBounds?>(firstBounds, latestBounds), repository.requestedBounds)
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
                val repository =
                    QueuedEmotionMapRepository(
                        CompletableDeferred(page(pins)),
                        CompletableDeferred(EmotionMapPageResult.Failure(EmotionMapFailure.Unavailable)),
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
                assertEquals(null, vm.uiModel.value.primaryFeedback())
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
                val repository = QueuedEmotionMapRepository(initial, refreshing)
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
                advanceTimeBy(700)
                runCurrent()
                assertEquals(emptyList(), vm.pinIds())
            } finally {
                Dispatchers.resetMain()
            }
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
    ) = EmotionMapPageResult.Success(EmotionMapPage(pins, hasNext, if (hasNext) "next" else null, 0))

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
