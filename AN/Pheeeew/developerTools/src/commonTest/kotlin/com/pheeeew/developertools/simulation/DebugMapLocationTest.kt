package com.pheeeew.developertools.simulation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pheeeew.core.di.AppUiSession
import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.permission.LocationPermissionStatus
import com.pheeeew.domain.repository.EmotionMapRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.screens.map.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebugMapLocationTest {
    @Test
    fun `실제 지도 모델이 위치 없음 응답을 처리하고 중복 없는 지연 재시도 성공까지 기존 오류를 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = AppUiSession()
            val dependencies =
                DebugAppServiceFactory(TestPlatformFactory()).createLocationDependencies(
                    object : LocationPermissionController {
                        override suspend fun currentStatus() = LocationPermissionStatus.Granted

                        override suspend fun requestPermission() = LocationPermissionStatus.Granted
                    },
                    object : PlatformLocationProvider {
                        override suspend fun getCurrentLocation(): CurrentLocation? =
                            error("Unexpected real acquisition")
                    },
                ) {}
            val maps =
                object : EmotionMapRepository {
                    override suspend fun findPage(
                        bounds: EmotionMapBounds?,
                        cursor: String?,
                        forceRefresh: Boolean,
                    ): EmotionMapPageResult = error("Unexpected map query")

                    override fun findSnapshot(bounds: EmotionMapBounds): EmotionMapSnapshot? = null
                }
            try {
                val factory =
                    viewModelFactory {
                        initializer {
                            MapViewModel(
                                RefreshLocationUseCase(dependencies.permissionController, dependencies.repository),
                                FindEmotionMapPageUseCase(maps),
                                FindEmotionMapSnapshotUseCase(maps),
                            )
                        }
                    }
                val model = ViewModelProvider.create(owner, factory)[MapViewModel::class]
                ErrorSimulation.replaceSettings(
                    ErrorSimulationState(location = LocationSimulationSettings(LocationSimulationMode.UNAVAILABLE)),
                )
                model.onMyLocationClick()
                runCurrent()
                assertEquals(LocationError.LocationTimeout, model.uiModel.value.locationError)
                assertFalse(model.uiModel.value.isRequestingLocation)
                ErrorSimulation.replaceSettings(
                    ErrorSimulationState(
                        location = LocationSimulationSettings(LocationSimulationMode.FIXED, delayMillis = 500),
                    ),
                )
                model.onMyLocationClick()
                runCurrent()
                model.onMyLocationClick()
                runCurrent()
                assertTrue(model.uiModel.value.isRequestingLocation)
                assertEquals(LocationError.LocationTimeout, model.uiModel.value.locationError)
                advanceTimeBy(500)
                runCurrent()
                assertIs<LocationState.Available>(model.uiModel.value.locationState)
                assertFalse(model.uiModel.value.isRequestingLocation)
                assertEquals(null, model.uiModel.value.locationError)
            } finally {
                owner.close()
                dependencies.close()
                ErrorSimulation.clear()
                Dispatchers.resetMain()
            }
        }
}
