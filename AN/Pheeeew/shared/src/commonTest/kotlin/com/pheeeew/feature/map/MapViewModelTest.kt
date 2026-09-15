@file:Suppress("NonAsciiCharacters")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.pheeeew.feature.map

import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionSettingsLauncher
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.di.LocationDependencies
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.location.CurrentLocation
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPage
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.domain.service.SighLocationObfuscator
import com.pheeeew.domain.usecase.CreateSighUseCase
import com.pheeeew.feature.map.map.MapCameraCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MapViewModelTest {
    private val noOpPerformanceLogger: MapPerformanceLogger = {}
    private val firstBounds =
        SighBounds(
            minLongitude = 126.9,
            minLatitude = 37.5,
            maxLongitude = 127.0,
            maxLatitude = 37.6,
        )
    private val secondBounds =
        firstBounds.copy(
            minLongitude = 127.0,
            maxLongitude = 127.1,
        )
    private val thirdBounds =
        firstBounds.copy(
            minLongitude = 127.1,
            maxLongitude = 127.2,
        )

    @Test
    fun `초기 상태는 부분 상태의 기본값을 가진다`() =
        runTest {
            val viewModel = createViewModel()

            assertEquals(MapUiState(), viewModel.uiState.value)
        }

    @Test
    fun `동일한 bounds로 요청하면 API를 한 번만 호출한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                assertEquals(listOf(firstBounds.expandForPrefetch()), repository.requestedBounds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `조회에 실패한 bounds는 같은 bounds로 재시도할 수 있다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository(failNextRequest = true)
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()
                assertNotNull(viewModel.uiState.value.errors.refreshMessage)

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                assertEquals(
                    listOf(firstBounds.expandForPrefetch(), firstBounds.expandForPrefetch()),
                    repository.requestedBounds,
                )
                assertNull(viewModel.uiState.value.errors.refreshMessage)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `연속된 bounds 요청은 마지막 요청만 API를 호출한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(100)
                viewModel.loadSighs(secondBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                assertEquals(listOf(secondBounds.expandForPrefetch()), repository.requestedBounds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `유효하지 않은 bounds는 API를 호출하지 않는다`() =
        runTest {
            val repository = RecordingSighRepository()
            val viewModel = createViewModel(repository)
            viewModel.onMapForeground()

            viewModel.loadSighs(firstBounds.copy(minLatitude = Double.NaN))
            advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(emptyList(), repository.requestedBounds)
            viewModel.onMapBackground()
        }

    @Test
    fun `경도가 역순인 bounds는 API를 호출하지 않는다`() =
        runTest {
            val repository = RecordingSighRepository()
            val viewModel = createViewModel(repository)
            viewModel.onMapForeground()

            viewModel.loadSighs(firstBounds.copy(minLongitude = 127.1, maxLongitude = 126.9))
            advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
            runCurrent()

            assertEquals(emptyList(), repository.requestedBounds)
            viewModel.onMapBackground()
        }

    @Test
    fun `지도 목록은 식별자 기준으로 정렬되어 상태에 반영된다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        mapSighs =
                            listOf(
                                SighPin(id = 3L, coordinate = Coordinate(37.55, 126.95)),
                                SighPin(id = 1L, coordinate = Coordinate(37.55, 126.95)),
                                SighPin(id = 2L, coordinate = Coordinate(37.55, 126.95)),
                            ),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                val actualIds =
                    viewModel.uiState.value.sighs
                        .map(SighPin::id)
                assertEquals(listOf(1L, 2L, 3L), actualIds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `진행 중인 요청은 취소하지 않고 pending 중 최신 bounds만 후속 요청한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository(holdFirstResponse = true)
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                viewModel.loadSighs(secondBounds)
                viewModel.loadSighs(thirdBounds)
                runCurrent()
                assertEquals(1, repository.requestedBounds.size)
                assertEquals(false, repository.firstRequestCancelled)

                repository.releaseFirstResponse.complete(Unit)
                runCurrent()
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                val state = viewModel.uiState.value
                assertEquals(2L, state.sighs.single().id)
                assertEquals(false, repository.firstRequestCancelled)
                assertEquals(
                    listOf(firstBounds.expandForPrefetch(), thirdBounds.expandForPrefetch()),
                    repository.requestedBounds,
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `백그라운드 전환은 진행 중인 별 조회 요청을 취소하지 않는다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository(holdFirstResponse = true)
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                viewModel.onMapBackground()
                runCurrent()
                assertEquals(false, repository.firstRequestCancelled)

                repository.releaseFirstResponse.complete(Unit)
                runCurrent()

                assertEquals(false, repository.firstRequestCancelled)
                assertEquals(emptyList(), viewModel.uiState.value.sighs)

                viewModel.onMapForeground()
                runCurrent()

                assertEquals(1, repository.requestedBounds.size)
                assertEquals(
                    1L,
                    viewModel.uiState.value.sighs
                        .single()
                        .id,
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `조회 완료 범위 안에서 지도를 이동하면 추가 요청하지 않는다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel = createViewModel(repository)
                val coveredBounds =
                    firstBounds.copy(
                        minLongitude = 126.92,
                        maxLongitude = 127.02,
                        minLatitude = 37.52,
                        maxLatitude = 37.62,
                    )
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                viewModel.loadSighs(coveredBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                assertEquals(1, repository.requestedBounds.size)
                assertEquals(
                    1L,
                    viewModel.uiState.value.sighs
                        .single()
                        .id,
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `백그라운드에서는 조회하지 않고 포그라운드 복귀 시 마지막 bounds를 재조회한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                viewModel.onMapBackground()
                viewModel.loadSighs(secondBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()
                assertEquals(listOf(firstBounds.expandForPrefetch()), repository.requestedBounds)

                viewModel.onMapForeground()
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                assertEquals(
                    listOf(firstBounds.expandForPrefetch(), secondBounds.expandForPrefetch()),
                    repository.requestedBounds,
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `확대와 축소는 순차 ID를 가진 카메라 명령을 만든다`() {
        val viewModel = createViewModel()

        viewModel.onZoomInClick()
        val zoomIn = assertIs<MapCameraCommand.ZoomBy>(viewModel.uiState.value.viewport.cameraCommand)
        assertEquals(1L, zoomIn.id)
        assertEquals(1.0, zoomIn.delta)

        viewModel.onZoomOutClick()
        val zoomOut = assertIs<MapCameraCommand.ZoomBy>(viewModel.uiState.value.viewport.cameraCommand)
        assertEquals(2L, zoomOut.id)
        assertEquals(-1.0, zoomOut.delta)
    }

    @Test
    fun `온보딩 위치 권한 요청은 기존 영구 거부 판정을 거치지 않고 시스템 요청을 호출한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                var requestCount = 0
                val dependencies =
                    LocationDependencies(
                        permissionController =
                            object : LocationPermissionController {
                                override suspend fun currentStatus() = LocationPermissionStatus.PermanentlyDenied

                                override suspend fun requestPermission(): LocationPermissionStatus {
                                    requestCount += 1
                                    return LocationPermissionStatus.Denied
                                }
                            },
                        permissionSettingsLauncher =
                            object : LocationPermissionSettingsLauncher {
                                override suspend fun openAppSettings() = true

                                override suspend fun openLocationSettings() = true
                            },
                        repository = FakeLocationRepository(LocationState.Loading),
                    )
                val viewModel = createViewModel(locationDependencies = dependencies)

                val status = viewModel.requestLocationPermission()

                assertEquals(1, requestCount)
                assertEquals(LocationPermissionStatus.Denied, status)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `한숨 등록 성공 시 핀과 포커스 요청을 추가한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val location =
                    CurrentLocation(
                        latitude = 37.55,
                        longitude = 126.95,
                        accuracyMeters = 5f,
                        capturedAtMillis = 1L,
                    )
                val viewModel =
                    createViewModel(
                        repository = repository,
                        locationDependencies = locationDependencies(LocationState.Available(location)),
                    )
                val registrationEvent = async { viewModel.registrationEvents.first() }
                runCurrent()

                viewModel.beginSighRegistration()
                assertIs<SighReleaseState.EditingMemo>(viewModel.uiState.value.sighRelease)
                viewModel.submitMemo("  오늘은 조금 지쳤다  ")
                runCurrent()
                assertIs<SighReleaseState.AwaitingBreath>(viewModel.uiState.value.sighRelease)
                assertTrue(repository.createdCommands.isEmpty())

                viewModel.completeBreath()
                runCurrent()
                assertIs<SighReleaseState.Submitting>(viewModel.uiState.value.sighRelease)

                advanceTimeBy(2_000)
                runCurrent()

                val state = viewModel.uiState.value
                assertIs<SighReleaseState.Idle>(state.sighRelease)
                assertEquals(1L, state.sighs.single().id)
                assertEquals("1", state.viewport.focusRequest?.id)
                assertEquals("오늘은 조금 지쳤다", repository.createdCommands.single().memo)
                assertEquals(
                    SighRegistrationSucceeded(
                        requestId = repository.createdCommands.single().requestId,
                        sighId = 1L,
                    ),
                    registrationEvent.await(),
                )

                viewModel.consumeFocusRequest("other")
                val unconsumedFocusRequest = viewModel.uiState.value.viewport.focusRequest
                assertEquals("1", unconsumedFocusRequest?.id)

                viewModel.consumeFocusRequest("1")
                assertNull(viewModel.uiState.value.viewport.focusRequest)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `한숨 등록 재시도는 같은 요청 식별자와 난독화 좌표를 사용한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository(failNextCreate = true)
                val location =
                    CurrentLocation(
                        latitude = 37.55,
                        longitude = 126.95,
                        accuracyMeters = 5f,
                        capturedAtMillis = 1L,
                    )
                val viewModel =
                    createViewModel(
                        repository = repository,
                        locationDependencies = locationDependencies(LocationState.Available(location)),
                    )

                viewModel.beginSighRegistration()
                viewModel.submitMemo("같은 요청")
                assertIs<SighReleaseState.AwaitingBreath>(viewModel.uiState.value.sighRelease)
                viewModel.completeBreath()
                runCurrent()
                advanceTimeBy(2_000)
                runCurrent()
                assertIs<SighReleaseState.Error>(viewModel.uiState.value.sighRelease)

                viewModel.retrySighCreation()
                runCurrent()
                advanceTimeBy(2_000)
                runCurrent()

                assertEquals(2, repository.createdCommands.size)
                assertEquals(repository.createdCommands.first(), repository.createdCommands.last())
                assertIs<SighReleaseState.Idle>(viewModel.uiState.value.sighRelease)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `메모 건너뛰기는 null payload를 준비하고 한숨 완료 뒤 한 번만 제출한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel =
                    createViewModel(
                        repository = repository,
                        locationDependencies =
                            locationDependencies(
                                LocationState.Available(
                                    CurrentLocation(
                                        latitude = 37.55,
                                        longitude = 126.95,
                                        accuracyMeters = 5f,
                                        capturedAtMillis = 1L,
                                    ),
                                ),
                            ),
                    )

                viewModel.beginSighRegistration()
                viewModel.skipMemo()
                viewModel.skipMemo()
                runCurrent()

                val awaiting = assertIs<SighReleaseState.AwaitingBreath>(viewModel.uiState.value.sighRelease)
                assertEquals(null, awaiting.command.memo)
                assertTrue(repository.createdCommands.isEmpty())

                viewModel.completeBreath()
                viewModel.completeBreath()
                runCurrent()

                assertEquals(1, repository.createdCommands.size)
                assertEquals(null, repository.createdCommands.single().memo)
                advanceTimeBy(2_000)
                runCurrent()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `메모 작성 중 닫기는 서버 요청 없이 draft를 정리한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel =
                    createViewModel(
                        repository = repository,
                        locationDependencies =
                            locationDependencies(
                                LocationState.Available(
                                    CurrentLocation(
                                        latitude = 37.55,
                                        longitude = 126.95,
                                        accuracyMeters = 5f,
                                        capturedAtMillis = 1L,
                                    ),
                                ),
                            ),
                    )

                viewModel.beginSighRegistration()
                assertIs<SighReleaseState.EditingMemo>(viewModel.uiState.value.sighRelease)
                viewModel.dismissMemo()

                assertIs<SighReleaseState.Idle>(viewModel.uiState.value.sighRelease)
                assertTrue(repository.createdCommands.isEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `위치가 없으면 한숨 등록을 시작하지 않고 재시도 불가 오류를 표시한다`() {
        val viewModel = createViewModel()

        viewModel.beginSighRegistration()

        val error = assertIs<SighReleaseState.Error>(viewModel.uiState.value.sighRelease)
        assertEquals(false, error.canRetry)
        assertEquals("GPS 수신이 원활하지 않습니다.", error.message)

        viewModel.cancelFailedSighRegistration()
        assertIs<SighReleaseState.Idle>(viewModel.uiState.value.sighRelease)
    }

    @Test
    fun `한숨 목록을 열면 마지막 지도 영역의 첫 페이지를 조회한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val firstPage =
                    SighPage(
                        items = listOf(sigh(id = 1L, memo = "첫 번째 한숨")),
                        nextCursor = "next-cursor",
                    )
                val repository = RecordingSighRepository(firstPage = firstPage)
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)

                viewModel.setSighListVisible(true)
                runCurrent()

                val browser = viewModel.uiState.value.sighBrowser
                assertTrue(browser.isVisible)
                assertEquals(listOf(firstBounds), repository.requestedListBounds)
                assertEquals(listOf(1L), browser.items.map(Sigh::id))
                assertEquals("next-cursor", browser.nextCursor)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지도 영역을 받기 전에 연 목록은 영역 수신 후 조회한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(sigh(1L)), nextCursor = null),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()

                viewModel.setSighListVisible(true)
                runCurrent()
                assertTrue(repository.requestedListBounds.isEmpty())

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(firstBounds), repository.requestedListBounds)
                assertEquals(
                    listOf(1L),
                    viewModel.uiState.value.sighBrowser.items
                        .map(Sigh::id),
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지도 이동은 목록을 재조회하지 않고 새로고침은 최신 영역으로 조회한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(sigh(1L)), nextCursor = null),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                viewModel.setSighListVisible(true)
                runCurrent()
                val firstRefreshRevision = viewModel.uiState.value.sighBrowser.refreshRevision

                viewModel.loadSighs(secondBounds)
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(firstBounds), repository.requestedListBounds)

                viewModel.refreshSighList()
                runCurrent()

                assertEquals(listOf(firstBounds, secondBounds), repository.requestedListBounds)
                assertEquals(
                    firstRefreshRevision + 1L,
                    viewModel.uiState.value.sighBrowser.refreshRevision,
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `다음 페이지는 중복 요청과 중복 항목 없이 병합한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(sigh(1L)), nextCursor = "page-2"),
                        nextPages =
                            mapOf(
                                "page-2" to
                                    SighPage(
                                        items = listOf(sigh(1L), sigh(2L)),
                                        nextCursor = null,
                                    ),
                            ),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                viewModel.setSighListVisible(true)
                runCurrent()

                viewModel.loadNextSighPage()
                viewModel.loadNextSighPage()
                runCurrent()

                assertEquals(listOf("page-2"), repository.requestedCursors)
                assertEquals(
                    listOf(1L, 2L),
                    viewModel.uiState.value.sighBrowser.items
                        .map(Sigh::id),
                )
                assertNull(viewModel.uiState.value.sighBrowser.nextCursor)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지도 핀의 상세가 목록에 있으면 API 요청 없이 상세를 연다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(sigh(id = 1L)), nextCursor = null),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                viewModel.setSighListVisible(true)
                runCurrent()
                viewModel.setSighListVisible(false)

                viewModel.openSighFromPin(1L)

                assertTrue(viewModel.uiState.value.sighBrowser.isVisible)
                assertEquals(
                    1L,
                    viewModel.uiState.value.sighBrowser.selectedSigh
                        ?.id,
                )
                assertEquals(emptyList(), repository.requestedDetailIds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지도 핀의 상세가 목록에 없으면 상세 API 응답을 캐시에 합쳐 연다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val detail = sigh(id = 7L, memo = "핀 상세")
                val repository =
                    RecordingSighRepository(
                        detailSighs = mapOf(7L to detail),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)

                viewModel.openSighFromPin(7L)
                assertTrue(viewModel.uiState.value.sighBrowser.isVisible)
                assertTrue(viewModel.uiState.value.sighBrowser.isDetailLoading)
                runCurrent()

                assertEquals(listOf(7L), repository.requestedDetailIds)
                assertEquals(detail, viewModel.uiState.value.sighBrowser.selectedSigh)
                assertEquals(listOf(detail), viewModel.uiState.value.sighBrowser.items)
                assertEquals(false, viewModel.uiState.value.sighBrowser.isDetailLoading)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `한숨 선택은 별도 요청 없이 목록 데이터로 상세를 연다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val listSigh = sigh(id = 1L, memo = "목록 메모")
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(listSigh), nextCursor = null),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                viewModel.setSighListVisible(true)
                runCurrent()

                viewModel.selectSigh(1L)
                assertEquals(
                    "목록 메모",
                    viewModel.uiState.value.sighBrowser.selectedSigh
                        ?.memo,
                )
                val detailCamera =
                    assertIs<MapCameraCommand.MoveToCoordinate>(
                        viewModel.uiState.value.viewport.cameraCommand,
                    )
                assertEquals(37.55, detailCamera.latitude, 0.000_001)
                assertEquals(126.95, detailCamera.longitude, 0.000_001)
                assertEquals(17.0, detailCamera.zoom)
                assertEquals(0.28, detailCamera.verticalPosition)
                runCurrent()

                viewModel.loadSighs(secondBounds)
                viewModel.dismissSighDetail()
                assertEquals(detailCamera, viewModel.uiState.value.viewport.cameraCommand)
                assertNull(viewModel.uiState.value.sighBrowser.selectedSigh)
                assertTrue(viewModel.uiState.value.sighBrowser.isVisible)

                viewModel.setSighListVisible(false)
                val restoredCamera =
                    assertIs<MapCameraCommand.MoveToBounds>(
                        viewModel.uiState.value.viewport.cameraCommand,
                    )
                assertEquals(firstBounds, restoredCamera.bounds)
                assertTrue(restoredCamera.id > detailCamera.id)
                assertNull(viewModel.uiState.value.sighBrowser.selectedSigh)
                assertEquals(false, viewModel.uiState.value.sighBrowser.isVisible)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `상세 진입과 복귀 카메라 이동은 지도 핀을 재조회하지 않는다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(sigh(id = 1L)), nextCursor = null),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                viewModel.setSighListVisible(true)
                runCurrent()
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()
                assertEquals(listOf(firstBounds.expandForPrefetch()), repository.requestedBounds)

                viewModel.selectSigh(1L)
                viewModel.loadSighs(secondBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()
                assertEquals(listOf(firstBounds.expandForPrefetch()), repository.requestedBounds)

                viewModel.dismissSighDetail()
                assertIs<MapCameraCommand.MoveToCoordinate>(
                    viewModel.uiState.value.viewport.cameraCommand,
                )
                viewModel.setSighListVisible(false)
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()
                assertEquals(listOf(firstBounds.expandForPrefetch()), repository.requestedBounds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `복원 bounds와 다른 카메라 콜백은 이후 지도 조회를 막지 않는다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository =
                    RecordingSighRepository(
                        firstPage = SighPage(listOf(sigh(id = 1L)), nextCursor = null),
                    )
                val viewModel = createViewModel(repository)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                viewModel.setSighListVisible(true)
                runCurrent()
                viewModel.selectSigh(1L)
                viewModel.setSighListVisible(false)

                viewModel.loadSighs(secondBounds)
                advanceTimeBy(MAP_SIGH_QUERY_DEBOUNCE_MILLIS)
                runCurrent()

                assertEquals(
                    listOf(firstBounds.expandForPrefetch(), secondBounds.expandForPrefetch()),
                    repository.requestedBounds,
                )
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun locationDependencies(initialState: LocationState): LocationDependencies {
        val locationRepository = FakeLocationRepository(initialState)
        return LocationDependencies(
            permissionController =
                object : LocationPermissionController {
                    override suspend fun currentStatus() = LocationPermissionStatus.Granted

                    override suspend fun requestPermission() = LocationPermissionStatus.Granted
                },
            permissionSettingsLauncher =
                object : LocationPermissionSettingsLauncher {
                    override suspend fun openAppSettings() = true

                    override suspend fun openLocationSettings() = true
                },
            repository = locationRepository,
        )
    }

    private fun createViewModel(
        repository: RecordingSighRepository = RecordingSighRepository(),
        locationDependencies: LocationDependencies? = null,
    ): MapViewModel =
        MapViewModel(
            sighRepository = repository,
            createSigh =
                CreateSighUseCase(
                    repository = repository,
                    locationObfuscator = SighLocationObfuscator { coordinate, _ -> coordinate },
                ),
            locationDependencies = locationDependencies,
            mapPerformanceLogger = noOpPerformanceLogger,
        )

    private fun sigh(
        id: Long,
        memo: String = "한숨",
    ): Sigh =
        Sigh(
            id = id,
            coordinate = Coordinate(latitude = 37.55, longitude = 126.95),
            memo = memo,
            nickname = "테스터",
            createdAt = Instant.parse("2026-09-01T12:00:00Z"),
        )

    private class FakeLocationRepository(
        initialState: LocationState,
    ) : LocationRepository {
        override val locationState: StateFlow<LocationState> = MutableStateFlow(initialState)

        override suspend fun refreshCurrentLocation() = Unit
    }

    private class RecordingSighRepository(
        private val holdFirstResponse: Boolean = false,
        private var failNextRequest: Boolean = false,
        private var failNextCreate: Boolean = false,
        private val mapSighs: List<SighPin>? = null,
        private val firstPage: SighPage = SighPage(emptyList(), nextCursor = null),
        private val nextPages: Map<String, SighPage> = emptyMap(),
        private val detailSighs: Map<Long, Sigh> = emptyMap(),
    ) : SighRepository {
        val requestedBounds = mutableListOf<SighBounds>()
        val requestedListBounds = mutableListOf<SighBounds>()
        val requestedCursors = mutableListOf<String>()
        val requestedDetailIds = mutableListOf<Long>()
        val createdCommands = mutableListOf<CreateSighCommand>()
        val releaseFirstResponse = CompletableDeferred<Unit>()
        var firstRequestCancelled = false
            private set

        override suspend fun getFirstPage(bounds: SighBounds): SighPage {
            requestedListBounds += bounds
            return firstPage
        }

        override suspend fun getNextPage(cursor: String): SighPage {
            requestedCursors += cursor
            return checkNotNull(nextPages[cursor]) { "다음 페이지 테스트 응답이 없습니다: $cursor" }
        }

        override suspend fun getById(id: Long): Sigh {
            requestedDetailIds += id
            return checkNotNull(detailSighs[id]) { "상세 조회 테스트 응답이 없습니다: $id" }
        }

        override suspend fun create(command: CreateSighCommand): Sigh {
            createdCommands += command
            if (failNextCreate) {
                failNextCreate = false
                throw ApiException.Network(code = "TEST-002", message = "등록 실패")
            }
            return Sigh(
                id = 1L,
                coordinate = command.coordinate,
                memo = command.memo,
                createdAt = Instant.parse("2026-09-01T12:00:00Z"),
            )
        }

        override suspend fun getMapSighs(bounds: SighBounds): List<SighPin> {
            requestedBounds += bounds
            if (failNextRequest) {
                failNextRequest = false
                throw ApiException.Network(code = "TEST-001", message = "조회 실패")
            }
            val requestNumber = requestedBounds.size
            if (holdFirstResponse && requestNumber == 1) {
                try {
                    releaseFirstResponse.await()
                } catch (cancellation: CancellationException) {
                    firstRequestCancelled = true
                    throw cancellation
                }
            }
            return mapSighs
                ?: listOf(
                    SighPin(
                        id = requestNumber.toLong(),
                        coordinate =
                            Coordinate(
                                latitude = (bounds.minLatitude + bounds.maxLatitude) / 2.0,
                                longitude = (bounds.minLongitude + bounds.maxLongitude) / 2.0,
                            ),
                    ),
                )
        }
    }
}
