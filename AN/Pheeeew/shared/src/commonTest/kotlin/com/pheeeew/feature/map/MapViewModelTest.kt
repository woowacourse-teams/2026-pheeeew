@file:Suppress("NonAsciiCharacters")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.pheeeew.feature.map

import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.SighRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class MapViewModelTest {
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
    fun `동일한 bounds로 요청하면 API를 한 번만 호출한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val repository = RecordingSighRepository()
                val viewModel = MapViewModel(repository, locationDependencies = null)
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
                val viewModel = MapViewModel(repository, locationDependencies = null)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()
                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                assertEquals(listOf(firstBounds, firstBounds), repository.requestedBounds)
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
                val viewModel = MapViewModel(repository, locationDependencies = null)
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
                val viewModel = MapViewModel(repository, locationDependencies = null)
                viewModel.onMapForeground()

                viewModel.loadSighs(firstBounds)
                advanceTimeBy(250)
                runCurrent()

                viewModel.onMapBackground()
                viewModel.onMapForeground()
                advanceTimeBy(1_000)
                runCurrent()

                val state = viewModel.uiState.value as MapUiState.Success
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
                val viewModel = MapViewModel(repository, locationDependencies = null)
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
