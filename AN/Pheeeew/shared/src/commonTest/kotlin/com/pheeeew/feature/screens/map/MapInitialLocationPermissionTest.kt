package com.pheeeew.feature.screens.map

import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.data.location.repository.LocationRepositoryImpl
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class MapInitialLocationPermissionTest {
    @Test
    fun `첫 진입에서 시스템 응답을 기다린 후 거절 안내를 표시하고 재진입에는 반복하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val permission = FakePermissionController(LocationPermissionStatus.Denied)
                val viewModel = createViewModel(permission)

                viewModel.start()
                runCurrent()
                assertEquals(1, permission.requests)
                assertFalse(viewModel.uiModel.value.showLocationPermissionDialog)
                assertTrue(viewModel.uiModel.value.isRequestingLocation)

                viewModel.start()
                permission.response.complete(LocationPermissionStatus.Denied)
                runCurrent()
                assertTrue(viewModel.uiModel.value.showLocationPermissionDialog)
                assertEquals(
                    LocationState.Unavailable(LocationError.PermissionDenied),
                    viewModel.uiModel.value.locationState,
                )

                viewModel.dismissLocationPermissionDialog()
                viewModel.start()
                runCurrent()
                assertFalse(viewModel.uiModel.value.showLocationPermissionDialog)
                assertEquals(1, permission.requests)

                viewModel.onMyLocationClick()
                runCurrent()
                assertEquals(2, permission.requests)
                assertTrue(viewModel.uiModel.value.showLocationPermissionDialog)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `첫 진입에서 권한을 허용하면 위치를 조회하고 거절 안내를 표시하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val permission = FakePermissionController(LocationPermissionStatus.Denied)
                permission.response.complete(LocationPermissionStatus.Granted)
                val viewModel = createViewModel(permission)

                viewModel.start()
                runCurrent()

                assertEquals(1, permission.requests)
                assertIs<LocationState.Available>(viewModel.uiModel.value.locationState)
                assertFalse(viewModel.uiModel.value.showLocationPermissionDialog)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `이미 허용되면 요청을 생략하고 영구 거절이면 시스템 요청 후 설정 안내를 표시한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (status in listOf(LocationPermissionStatus.Granted, LocationPermissionStatus.PermanentlyDenied)) {
                    val permission = FakePermissionController(status)
                    permission.response.complete(status)
                    val viewModel = createViewModel(permission)

                    viewModel.start()
                    runCurrent()

                    assertEquals(if (status == LocationPermissionStatus.Granted) 0 else 1, permission.requests)
                    assertEquals(
                        status == LocationPermissionStatus.PermanentlyDenied,
                        viewModel.uiModel.value.showLocationPermissionDialog,
                    )
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(permission: FakePermissionController): MapViewModel {
        val locationRepository =
            LocationRepositoryImpl(
                permission,
                object : PlatformLocationProvider {
                    override suspend fun getCurrentLocation() =
                        CurrentLocation(37.5, 127.0, 1f, Clock.System.now().toEpochMilliseconds())
                },
            )
        val emotionRepository =
            object : EmotionMapRepository {
                override suspend fun findPage(
                    bounds: EmotionMapBounds?,
                    groupId: String?,
                    cursor: String?,
                    forceRefresh: Boolean,
                ): EmotionMapPageResult = error("No map page requested")

                override fun findSnapshot(
                    bounds: EmotionMapBounds,
                    groupId: String?,
                ): EmotionMapSnapshot? = null
            }
        return MapViewModel(
            RefreshLocationUseCase(permission, locationRepository),
            FindEmotionMapPageUseCase(emotionRepository),
            FindEmotionMapSnapshotUseCase(emotionRepository),
        )
    }

    private class FakePermissionController(
        private var status: LocationPermissionStatus,
    ) : LocationPermissionController {
        val response = CompletableDeferred<LocationPermissionStatus>()
        var requests = 0

        override suspend fun currentStatus() = status

        override suspend fun requestPermission(): LocationPermissionStatus {
            requests++
            return response.await().also { status = it }
        }
    }
}
