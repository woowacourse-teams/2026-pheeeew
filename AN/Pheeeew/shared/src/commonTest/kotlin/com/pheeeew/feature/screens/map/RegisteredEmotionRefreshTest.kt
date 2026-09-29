package com.pheeeew.feature.screens.map

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createApiClient
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.data.remote.emotion.EmotionMapApi
import com.pheeeew.data.repository.EmotionMapRepositoryImpl
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.repository.LocationRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegisteredEmotionRefreshTest {
    private val wide = EmotionMapBounds(126.0, 36.0, 128.0, 38.0)
    private val small = EmotionMapBounds(126.95, 36.95, 127.05, 37.05)
    private val finalView = EmotionMapBounds(126.9, 36.9, 127.1, 37.1)

    @Test
    fun `등록 새로고침이 대기 중이어도 카메라 이동 후 서버에서 새 핀을 조회한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val firstResponse = CompletableDeferred<Unit>()
            var requestCount = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine.create {
                            dispatcher = StandardTestDispatcher(testScheduler)
                            addHandler {
                                val first = ++requestCount == 1
                                if (first) firstResponse.await()
                                respond(pinsJson(if (first) listOf(1) else listOf(1, 2)), headers = jsonHeaders())
                            }
                        },
                    config = ApiConfig("https://example.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val vm = model(EmotionMapRepositoryImpl(EmotionMapApi(client.requests)))
                vm.onViewportChanged(wide)
                advanceTimeBy(700)
                runCurrent()
                assertEquals(1, requestCount)

                vm.focusOnCoordinate(GeoCoordinate(37.0, 127.0))
                vm.refreshEmotionPins()
                vm.onViewportChanged(finalView)
                advanceTimeBy(700)
                runCurrent()
                firstResponse.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(1L, 2L), vm.pinIds())
                assertEquals(2, requestCount)
            } finally {
                client.close()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `등록 새로고침 응답 전에 이동해도 마지막 영역을 강제로 갱신한다`() = verifyRefreshAndCameraMove(moveBeforeResponse = true)

    @Test
    fun `등록 새로고침 응답 후 이동해도 이전 넓은 영역 캐시가 새 핀을 지우지 않는다`() = verifyRefreshAndCameraMove(moveBeforeResponse = false)

    @Test
    fun `새로고침 전에 시작한 늦은 응답이 새 캐시를 오염시키지 않는다`() =
        runTest {
            val oldResponse = CompletableDeferred<Unit>()
            var requestCount = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine.create {
                            dispatcher = StandardTestDispatcher(testScheduler)
                            addHandler {
                                val first = ++requestCount == 1
                                if (first) oldResponse.await()
                                respond(pinsJson(if (first) listOf(1) else listOf(1, 2)), headers = jsonHeaders())
                            }
                        },
                    config = ApiConfig("https://example.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val repository = EmotionMapRepositoryImpl(EmotionMapApi(client.requests))
                val oldRequest = async { repository.findPage(wide, null, null) }
                runCurrent()
                assertEquals(1, requestCount)
                repository.findPage(small, null, null, forceRefresh = true)
                oldResponse.complete(Unit)
                oldRequest.await()
                assertEquals(listOf(1L, 2L), repository.findSnapshot(finalView)?.pins?.map { it.id })
                repository.findPage(finalView, null, null)
                assertEquals(3, requestCount)
            } finally {
                client.close()
            }
        }

    private fun verifyRefreshAndCameraMove(moveBeforeResponse: Boolean) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val refreshResponse = CompletableDeferred<Unit>()
            var requestCount = 0
            val client =
                createApiClient(
                    engine =
                        MockEngine.create {
                            dispatcher = StandardTestDispatcher(testScheduler)
                            addHandler {
                                val number = ++requestCount
                                if (number == 2) refreshResponse.await()
                                respond(pinsJson(if (number == 1) listOf(1) else listOf(1, 2)), headers = jsonHeaders())
                            }
                        },
                    config = ApiConfig("https://example.test"),
                    accessTokenProvider = AccessTokenProvider { AccessToken("test-token") },
                )
            try {
                val vm = model(EmotionMapRepositoryImpl(EmotionMapApi(client.requests)))
                vm.onViewportChanged(wide)
                advanceUntilIdle()
                vm.onViewportChanged(small)
                advanceUntilIdle()
                assertEquals(listOf(1L), vm.pinIds())
                assertEquals(1, requestCount)

                vm.refreshEmotionPins()
                runCurrent()
                assertEquals(2, requestCount)
                if (moveBeforeResponse) {
                    vm.onViewportChanged(finalView)
                    advanceTimeBy(700)
                    runCurrent()
                }
                refreshResponse.complete(Unit)
                runCurrent()
                assertEquals(listOf(1L, 2L), vm.pinIds())
                if (!moveBeforeResponse) vm.onViewportChanged(finalView)
                assertEquals(listOf(1L, 2L), vm.pinIds())
                advanceUntilIdle()
                assertEquals(listOf(1L, 2L), vm.pinIds())
                assertEquals(3, requestCount)
            } finally {
                client.close()
                Dispatchers.resetMain()
            }
        }

    private fun model(repository: EmotionMapRepositoryImpl) =
        MapViewModel(
            noLocationRefreshUseCase(),
            FindEmotionMapPageUseCase(repository),
            FindEmotionMapSnapshotUseCase(repository),
        )

    private fun MapViewModel.pinIds() = uiModel.value.emotionPins.map { it.id }

    private fun pinsJson(ids: List<Int>): String {
        val items =
            ids.joinToString(",") { id ->
                """{"type":"Feature","id":$id,"geometry":{"type":"Point","coordinates":[127.0,37.0]},"properties":{"createdAt":"2026-09-28T00:00:00Z","state":"ANGRY","rotationDegrees":0,"groupStamp":null}}"""
            }
        return """{"items":[$items],"hasNext":false,"nextCursor":null}"""
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/geo+json")

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
}
