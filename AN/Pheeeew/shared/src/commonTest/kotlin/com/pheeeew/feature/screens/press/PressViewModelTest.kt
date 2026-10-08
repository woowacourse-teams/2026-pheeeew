package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModelStore
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressRepository
import com.pheeeew.domain.repository.press.PressSubmitResult
import com.pheeeew.feature.emotion.model.EmotionKind
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
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class PressViewModelTest {
    @Test
    fun `loads independent today totals and maps personal emotion counts`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val repository = FakePressRepository()
                val viewModel = PressViewModel(repository, locationDependencies())
                store.put("press", viewModel)

                viewModel.onScreenResumed()
                advanceTimeBy(0)
                runCurrent()

                assertEquals(
                    9L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
                assertEquals(
                    82L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertEquals(2L, viewModel.uiState.value.myEmotionCounts[EmotionKind.Blocked])
                assertEquals(0L, viewModel.uiState.value.myEmotionCounts[EmotionKind.Defeated])
                assertEquals(1, repository.myReads)
                assertEquals(1, repository.allReads)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `coalesces taps and refreshes totals after the batch is accepted`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val repository = FakePressRepository()
                val viewModel = PressViewModel(repository, locationDependencies())
                store.put("press", viewModel)

                assertTrue(viewModel.onEmotionTap(EmotionKind.Blocked))
                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                assertEquals(2, viewModel.uiState.value.pendingPressCount)

                advanceTimeBy(500)
                runCurrent()

                assertEquals(1, repository.submissions.size)
                assertEquals(
                    2,
                    repository.submissions
                        .single()
                        .values
                        .sum(),
                )
                assertEquals(0, repository.myReads)

                advanceTimeBy(300)
                runCurrent()

                assertEquals(1, repository.myReads)
                assertEquals(1, repository.allReads)
                assertEquals(0, viewModel.uiState.value.pendingPressCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `keeps first tap pending until location becomes available and retries without another tap`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val repository = FakePressRepository()
                val location = testLocation()
                val locationRepository =
                    FakeLocationRepository(
                        initial = LocationState.Unavailable(LocationError.ServicesDisabled),
                        refreshedState = LocationState.Unavailable(LocationError.ServicesDisabled),
                    )
                var permissionStatus = LocationPermissionStatus.ServicesDisabled
                val dependencies =
                    LocationDependencies(
                        permissionController =
                            object : LocationPermissionController {
                                override suspend fun currentStatus() = permissionStatus

                                override suspend fun requestPermission() = permissionStatus
                            },
                        repository = locationRepository,
                    )
                val viewModel = PressViewModel(repository, dependencies)
                store.put("press", viewModel)

                assertTrue(viewModel.onEmotionTap(EmotionKind.Blocked))
                assertEquals(1, viewModel.uiState.value.pendingPressCount)
                assertEquals(PressNotice.LocationPreparing, viewModel.uiState.value.notice)

                runCurrent()

                assertEquals(1, viewModel.uiState.value.pendingPressCount)
                assertEquals(PressNotice.LocationUnavailable, viewModel.uiState.value.notice)
                assertTrue(repository.submissions.isEmpty())

                permissionStatus = LocationPermissionStatus.Granted
                locationRepository.refreshedState = LocationState.Available(location)
                viewModel.retryLocation()
                runCurrent()

                assertEquals(1, viewModel.uiState.value.pendingPressCount)
                assertEquals(null, viewModel.uiState.value.notice)
                advanceTimeBy(500)
                runCurrent()

                assertEquals(1, repository.submissions.size)
                assertEquals(1, repository.submissions.single()[EmotionState.FRUSTRATED])
                assertEquals(0, viewModel.uiState.value.pendingPressCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    private fun locationDependencies(): LocationDependencies =
        LocationDependencies(
            permissionController =
                object : LocationPermissionController {
                    override suspend fun currentStatus() = LocationPermissionStatus.Granted

                    override suspend fun requestPermission() = LocationPermissionStatus.Granted
                },
            repository = FakeLocationRepository(LocationState.Available(testLocation())),
        )

    private fun testLocation() = CurrentLocation(37.5, 127.0, 8f, Clock.System.now().toEpochMilliseconds())

    private class FakeLocationRepository(
        initial: LocationState,
        var refreshedState: LocationState = initial,
    ) : LocationRepository {
        override val state = MutableStateFlow(initial)

        override suspend fun refresh() {
            state.value = refreshedState
        }
    }

    private class FakePressRepository : PressRepository {
        var myReads = 0
        var allReads = 0
        val submissions = mutableListOf<Map<EmotionState, Int>>()

        override suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot> {
            myReads++
            return PressReadResult.Loaded(mySnapshot())
        }

        override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> {
            allReads++
            return PressReadResult.Loaded(AllDailyPressSnapshot("2026-10-08", 82))
        }

        override suspend fun submit(
            location: CurrentLocation,
            counts: Map<EmotionState, Int>,
        ): PressSubmitResult {
            submissions += counts
            return PressSubmitResult.Submitted
        }

        private fun mySnapshot() =
            MyDailyPressSnapshot(
                pressDate = "2026-10-08",
                counts =
                    mapOf(
                        EmotionState.FRUSTRATED to 2,
                        EmotionState.IRRITATED to 3,
                        EmotionState.EXHAUSTED to 4,
                        EmotionState.DISCOURAGED to 0,
                        EmotionState.ANGRY to 0,
                    ),
                total = 9,
            )
    }
}
