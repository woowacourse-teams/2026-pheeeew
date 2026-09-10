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
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.feature.map.map.MapCameraCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun `초기 상태는 부분 상태의 기본값을 가진다`() =
        runTest {
            val viewModel =
                MapViewModel(
                    sighRepository = RecordingSighRepository(),
                    locationDependencies = null,
                    mapPerformanceLogger = noOpPerformanceLogger,
                )

            assertEquals(MapUiState(), viewModel.uiState.value)
        }

    @Test
    fun `동일한 bounds로 요청하면 API를 한 번만 호출한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel = MapViewModel(repository, null, noOpPerformanceLogger)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(firstBounds), repository.requestedBounds)
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
                val viewModel = MapViewModel(repository, null, noOpPerformanceLogger)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()
                assertNotNull(viewModel.uiState.value.errors.refreshMessage)

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(firstBounds, firstBounds), repository.requestedBounds)
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
                val viewModel = MapViewModel(repository, null, noOpPerformanceLogger)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(100)
                viewModel.loadSighs(secondBounds)
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(secondBounds), repository.requestedBounds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `오래된 요청의 응답은 최신 요청 결과를 덮어쓰지 않는다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository(delayFirstResponse = true)
                val viewModel = MapViewModel(repository, null, noOpPerformanceLogger)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                viewModel.onMapBackground()
                viewModel.onMapForeground()
                advanceTimeBy(1_000)
                runCurrent()

                val state = viewModel.uiState.value
                assertEquals(2L, state.sighs.single().id)
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
                val viewModel = MapViewModel(repository, null, noOpPerformanceLogger)
                viewModel.onMapForeground()
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                viewModel.onMapBackground()
                viewModel.loadSighs(secondBounds)
                advanceTimeBy(250)
                runCurrent()
                assertEquals(listOf(firstBounds), repository.requestedBounds)

                viewModel.onMapForeground()
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(firstBounds, secondBounds), repository.requestedBounds)
                viewModel.onMapBackground()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `확대와 축소는 순차 ID를 가진 카메라 명령을 만든다`() {
        val viewModel = MapViewModel(RecordingSighRepository(), null, noOpPerformanceLogger)

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
                    MapViewModel(
                        sighRepository = repository,
                        locationDependencies = locationDependencies(LocationState.Available(location)),
                        mapPerformanceLogger = noOpPerformanceLogger,
                    )

                viewModel.registerSighAfterExplosion()
                runCurrent()
                assertIs<SighReleaseState.Submitting>(viewModel.uiState.value.sighRelease)

                advanceTimeBy(2_000)
                runCurrent()

                val state = viewModel.uiState.value
                assertIs<SighReleaseState.Idle>(state.sighRelease)
                assertEquals(1L, state.sighs.single().id)
                assertEquals("1", state.viewport.focusRequest?.id)

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
    fun `위치가 없으면 한숨 등록을 시작하지 않고 재시도 불가 오류를 표시한다`() {
        val viewModel = MapViewModel(RecordingSighRepository(), null, noOpPerformanceLogger)

        viewModel.registerSighAfterExplosion()

        val error = assertIs<SighReleaseState.Error>(viewModel.uiState.value.sighRelease)
        assertEquals(false, error.canRetry)
        assertEquals("GPS 수신이 원활하지 않습니다.", error.message)

        viewModel.cancelFailedSighRegistration()
        assertIs<SighReleaseState.Idle>(viewModel.uiState.value.sighRelease)
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

    private class FakeLocationRepository(
        initialState: LocationState,
    ) : LocationRepository {
        override val locationState: StateFlow<LocationState> = MutableStateFlow(initialState)

        override suspend fun refreshCurrentLocation() = Unit
    }

    private class RecordingSighRepository(
        private val delayFirstResponse: Boolean = false,
        private var failNextRequest: Boolean = false,
    ) : SighRepository {
        val requestedBounds = mutableListOf<SighBounds>()

        override suspend fun getSighs(bounds: SighBounds): List<SighPin> {
            requestedBounds += bounds
            if (failNextRequest) {
                failNextRequest = false
                throw ApiException.Network(code = "TEST-001", message = "조회 실패")
            }
            val requestNumber = requestedBounds.size
            if (delayFirstResponse && requestNumber == 1) {
                withContext(NonCancellable) { delay(1_000) }
            }
            return listOf(
                SighPin(
                    id = requestNumber.toLong(),
                    coordinate = Coordinate(latitude = 37.55, longitude = 126.95),
                ),
            )
        }

        override suspend fun registerSigh(
            requestId: String,
            coordinate: Coordinate,
        ): SighPin = SighPin(id = 1L, coordinate = coordinate)
    }
}
