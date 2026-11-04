package com.pheeeew.developertools.data.simulation

import com.pheeeew.core.location.PlatformLocationProvider
import com.pheeeew.data.repository.LocationRepositoryImpl
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.permission.LocationPermissionController
import com.pheeeew.domain.permission.LocationPermissionStatus
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class DebugLocationProviderTest {
    @AfterTest
    fun reset() = ErrorSimulation.clear()

    @Test
    fun `Debug 조립은 실제 위치 저장소를 사용하고 기본 설정은 실제 제공자에 위임한다`() =
        runTest {
            var called = 0
            val factory = DebugAppServiceFactory(TestPlatformFactory())
            val dependencies =
                factory.createLocationDependencies(
                    granted(),
                    provider {
                        called++
                        location()
                    },
                ) {}
            assertIs<LocationRepositoryImpl>(dependencies.repository)
            assertIs<LocationState.Available>(
                RefreshLocationUseCase(dependencies.permissionController, dependencies.repository)(false),
            )
            assertEquals(1, called)
        }

    @Test
    fun `지정 좌표는 실제 저장소에서 지연 이후 성공한다`() =
        runTest {
            val repository = repository(LocationSimulationSettings(LocationSimulationMode.FIXED, delayMillis = 500))
            val request = async { repository.refresh() }
            runCurrent()
            advanceTimeBy(499)
            assertEquals(LocationState.Loading, repository.state.value)
            advanceTimeBy(1)
            runCurrent()
            request.await()
            assertIs<LocationState.Available>(repository.state.value)
        }

    @Test
    fun `실제 위치의 추가 지연과 획득 지연이 같은 제한 시간에 포함된다`() =
        runTest {
            var calls = 0
            val repository =
                repository(
                    LocationSimulationSettings(delayMillis = 500),
                    provider {
                        calls++
                        delay(300)
                        location()
                    },
                )
            val request = async { repository.refresh() }
            runCurrent()
            advanceTimeBy(799)
            assertEquals(LocationState.Loading, repository.state.value)
            advanceTimeBy(1)
            runCurrent()
            request.await()
            assertIs<LocationState.Available>(repository.state.value)
            assertEquals(1, calls)
        }

    @Test
    fun `응답 없음은 제품의 30초 제한 시간을 거친다`() =
        runTest {
            val repository = repository(LocationSimulationSettings(neverResponds = true))
            val request = async { repository.refresh() }
            runCurrent()
            advanceTimeBy(29_999)
            runCurrent()
            assertTrue(request.isActive)
            advanceTimeBy(1)
            runCurrent()
            request.await()
            assertEquals(LocationState.Unavailable(LocationError.LocationTimeout), repository.state.value)
        }

    @Test
    fun `추가 지연이 제한 시간을 넘으면 실제 위치 제공자를 호출하지 않는다`() =
        runTest {
            var calls = 0
            val repository =
                repository(
                    LocationSimulationSettings(delayMillis = 31_000),
                    provider {
                        calls++
                        location()
                    },
                )
            repository.refresh()
            assertEquals(30_000, testScheduler.currentTime)
            assertEquals(0, calls)
            assertEquals(LocationState.Unavailable(LocationError.LocationTimeout), repository.state.value)
        }

    @Test
    fun `권한과 서비스 오류에는 획득 지연을 적용하거나 제공자를 호출하지 않는다`() =
        runTest {
            for ((mode, error) in listOf(
                LocationSimulationMode.PERMISSION_DENIED to LocationError.PermissionDenied,
                LocationSimulationMode.SERVICES_DISABLED to LocationError.ServicesDisabled,
            )) {
                ErrorSimulation.replaceSettings(
                    ErrorSimulationState(
                        location = LocationSimulationSettings(mode, delayMillis = 5_000, neverResponds = true),
                    ),
                )
                val dependencies =
                    DebugAppServiceFactory(
                        TestPlatformFactory(),
                    ).createLocationDependencies(
                        granted(),
                        provider {
                            error("Unexpected acquisition")
                        },
                    ) {}
                val useCase = RefreshLocationUseCase(dependencies.permissionController, dependencies.repository)
                assertEquals(LocationState.Unavailable(error), useCase(true))
            }
            assertEquals(0, testScheduler.currentTime)
        }

    @Test
    fun `취소된 대기는 이후 조건 해제에도 실제 제공자를 호출하지 않고 다음 요청은 성공한다`() =
        runTest {
            var calls = 0
            ErrorSimulation.replaceSettings(
                ErrorSimulationState(location = LocationSimulationSettings(delayMillis = 5_000)),
            )
            val repository =
                LocationRepositoryImpl(
                    granted(),
                    DebugLocationProvider(
                        provider {
                            calls++
                            location()
                        },
                    ),
                )
            val request = async { repository.refresh() }
            runCurrent()
            advanceTimeBy(200)
            request.cancel()
            request.join()
            ErrorSimulation.clear()
            advanceTimeBy(5_000)
            assertEquals(0, calls)
            repository.refresh()
            assertEquals(1, calls)
        }

    @Test
    fun `획득 시작 후 변경은 다음 요청에 적용하며 기존 제한 시간을 다시 시작하지 않는다`() =
        runTest {
            ErrorSimulation.replaceSettings(
                ErrorSimulationState(location = LocationSimulationSettings(neverResponds = true)),
            )
            val repository = LocationRepositoryImpl(granted(), DebugLocationProvider(provider { location() }))
            val request = async { repository.refresh() }
            runCurrent()
            advanceTimeBy(10_000)
            ErrorSimulation.clear()
            advanceTimeBy(19_999)
            runCurrent()
            assertTrue(request.isActive)
            advanceTimeBy(1)
            runCurrent()
            request.await()
            assertEquals(LocationState.Unavailable(LocationError.LocationTimeout), repository.state.value)
            repository.refresh()
            assertIs<LocationState.Available>(repository.state.value)
        }

    @Test
    fun `위치 확인 실패는 실제 제공자의 null 응답과 같은 제품 경로를 사용한다`() =
        runTest {
            val repository = repository(LocationSimulationSettings(LocationSimulationMode.UNAVAILABLE))
            repository.refresh()
            val nullRepository = LocationRepositoryImpl(granted(), provider { null })
            nullRepository.refresh()
            assertEquals(LocationState.Unavailable(LocationError.LocationTimeout), nullRepository.state.value)
            assertEquals(nullRepository.state.value, repository.state.value)
            assertEquals(0L, testScheduler.currentTime)
        }

    private fun repository(
        settings: LocationSimulationSettings,
        actual: PlatformLocationProvider =
            provider {
                error("Unexpected real acquisition")
            },
    ) = LocationRepositoryImpl(
        granted(),
        DebugLocationProvider(actual, LocationSimulationSettingsProvider { settings }),
    )

    private fun granted() =
        object : LocationPermissionController {
            override suspend fun currentStatus() = LocationPermissionStatus.Granted

            override suspend fun requestPermission() = LocationPermissionStatus.Granted
        }

    private fun provider(block: suspend () -> CurrentLocation?) =
        object : PlatformLocationProvider {
            override suspend fun getCurrentLocation() = block()
        }

    private fun location() = CurrentLocation(37.0, 127.0, 5f, Clock.System.now().toEpochMilliseconds())
}
