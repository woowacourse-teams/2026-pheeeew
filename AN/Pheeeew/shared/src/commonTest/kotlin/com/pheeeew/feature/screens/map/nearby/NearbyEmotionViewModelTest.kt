package com.pheeeew.feature.screens.map.nearby

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.domain.repository.BlockRepository
import com.pheeeew.domain.repository.EmotionFailure
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.repository.EmotionRepository
import com.pheeeew.domain.repository.EmotionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyEmotionViewModelTest {
    @Test
    fun `닫힌 목록에 적용한 설정은 다음 열기와 후속 조회에도 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repo = FakeRepository()
                val vm = viewModel(repo)
                val settings = MapFilterSettings(content = MapFilterContent.AUDIO, scope = MapFilterScope.MY)
                vm.setAppliedSettings(settings)
                assertTrue(repo.filters.isEmpty())
                vm.open()
                advanceUntilIdle()
                assertEquals(listOf(settings.toSearchFilter()), repo.filters)
                vm.dismiss()
                vm.open()
                advanceUntilIdle()
                assertEquals(List(2) { settings.toSearchFilter() }, repo.filters)
                assertTrue(repo.bounds.isEmpty())
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `지도 영역 변경 뒤 목록 좌표로 이동해도 목록과 조회 상태를 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val coordinate = GeoCoordinate(37.55, 127.02)
                val repo = FakeRepository()
                repo.first = {
                    EmotionResult.Success(EmotionPage(listOf(emotion(1).copy(coordinate = coordinate)), "next"))
                }
                val vm = viewModel(repo)
                vm.open()
                advanceUntilIdle()
                val loaded = vm.state.value
                vm.select(1)
                vm.openOnMap(1) { id, target ->
                    assertEquals(1L, id)
                    assertEquals(coordinate, target)
                    true
                }
                assertEquals(loaded, vm.state.value)
                vm.openOnMap(1) { _, _ -> false }
                assertTrue(vm.state.value.message != null)
                assertEquals(1, repo.filters.size)
                assertTrue(repo.bounds.isEmpty())
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `새로고침해도 내 기록 필터를 유지한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repo = FakeRepository()
                val vm = viewModel(repo)
                val settings = MapFilterSettings(scope = MapFilterScope.MY)
                vm.setAppliedSettings(settings)
                vm.open()
                advanceUntilIdle()
                vm.refresh()
                advanceUntilIdle()
                assertEquals(List(2) { settings.toSearchFilter() }, repo.filters)
                assertTrue(repo.bounds.isEmpty())
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `재시도 중 다시 누르면 ViewModel이 중복 요청을 막는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val retryResult = CompletableDeferred<EmotionResult<EmotionPage>>()
                var calls = 0
                val repo =
                    FakeRepository().apply {
                        first = {
                            calls++
                            if (calls == 1) {
                                EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
                            } else {
                                retryResult.await()
                            }
                        }
                    }
                val vm = viewModel(repo)
                vm.open()
                advanceUntilIdle()
                assertTrue(vm.state.value.error != null)

                vm.retryError()
                runCurrent()
                assertTrue(vm.state.value.loading)
                assertEquals(2, calls)

                vm.retryError()
                runCurrent()
                assertEquals(2, calls)

                retryResult.complete(successPage(2))
                advanceUntilIdle()
                assertFalse(vm.state.value.loading)
                assertEquals(
                    listOf(2L),
                    vm.state.value.items
                        .map { it.id },
                )
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `취소를 무시하고 늦게 도착한 이전 페이지는 새 목록을 덮지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val gate = CompletableDeferred<EmotionResult<EmotionPage>>()
                val repo = FakeRepository()
                var calls = 0
                repo.first = { if (calls++ == 0) withContext(NonCancellable) { gate.await() } else successPage(2) }
                val vm = viewModel(repo)
                vm.open()
                runCurrent()
                vm.refresh()
                runCurrent()
                gate.complete(successPage(1))
                advanceUntilIdle()
                assertEquals(
                    listOf(2L),
                    vm.state.value.items
                        .map { it.id },
                )
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `반응 쓰기 성공 뒤 상세 재조회 실패는 성공한 선택을 취소하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repo = FakeRepository()
                val vm = viewModel(repo)
                vm.open()
                advanceUntilIdle()
                vm.react(1, EmotionReactionType.HEART)
                advanceUntilIdle()
                assertTrue(
                    vm.state.value.items
                        .single()
                        .reactions
                        .first()
                        .selected,
                )
                assertEquals(
                    1L,
                    vm.state.value.items
                        .single()
                        .reactions
                        .first()
                        .count,
                )
                assertTrue(
                    vm.state.value.pendingIds
                        .isEmpty(),
                )
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `차단 성공 뒤 늦게 도착한 페이지에도 차단한 글은 다시 나타나지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val gate = CompletableDeferred<EmotionResult<EmotionPage>>()
                val repo = FakeRepository()
                repo.first = { EmotionResult.Success(EmotionPage(listOf(emotion(1)), "next")) }
                repo.next = { gate.await() }
                val vm = viewModel(repo)
                vm.open()
                advanceUntilIdle()
                vm.loadMore()
                runCurrent()
                vm.requestBlock(1)
                vm.confirmBlock()
                runCurrent()
                gate.complete(EmotionResult.Success(EmotionPage(listOf(emotion(1), emotion(2)), null)))
                advanceUntilIdle()
                assertEquals(
                    listOf(2L),
                    vm.state.value.items
                        .map { it.id },
                )
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `내 글 신고 차단 메뉴는 열리지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repo = FakeRepository()
                repo.first = { EmotionResult.Success(EmotionPage(listOf(emotion(1).copy(isMine = true)), null)) }
                val vm = viewModel(repo)
                vm.open()
                advanceUntilIdle()
                vm.requestBlock(1)
                assertEquals(null, vm.state.value.blockId)
                assertFalse(vm.state.value.loading)
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(repo: FakeRepository) = NearbyEmotionViewModel(repo, repo)

    private class FakeRepository :
        EmotionRepository,
        BlockRepository {
        val bounds = mutableListOf<EmotionBounds>()
        val filters = mutableListOf<EmotionSearchFilter>()
        var first: suspend () -> EmotionResult<EmotionPage> = { successPage(1) }
        var next: suspend () -> EmotionResult<EmotionPage> = { successPage(2) }

        override suspend fun firstPage(bounds: EmotionBounds): EmotionResult<EmotionPage> {
            this.bounds += bounds
            return first()
        }

        override suspend fun nextPage(cursor: String) = next()

        override suspend fun filteredFirstPage(filter: EmotionSearchFilter): EmotionResult<EmotionPage> {
            filters += filter
            return first()
        }

        override suspend fun detail(id: Long): EmotionResult<Emotion> =
            EmotionResult.Failure(EmotionFailure.UNAVAILABLE)

        override suspend fun react(
            id: Long,
            type: EmotionReactionType,
            selected: Boolean,
        ) = EmotionResult.Success(Unit)

        override suspend fun blockEmotion(emotionId: Long) = EmotionModerationResult.Success

        override suspend fun blockUser(emotionId: Long) = EmotionModerationResult.Success

        override suspend fun delete(emotionId: Long) = EmotionModerationResult.Success
    }

    private companion object {
        val BOUNDS = EmotionBounds(126.0, 37.0, 127.0, 38.0)

        fun successPage(id: Long) = EmotionResult.Success(EmotionPage(listOf(emotion(id)), null))

        fun emotion(id: Long) =
            Emotion(
                id,
                EmotionState.FRUSTRATED,
                "닉네임",
                Instant.parse("2026-09-27T00:00:00Z"),
                false,
                EmotionContentType.MEMO,
                "메모",
                EmotionReactionType.entries.map { ReactionCount(it, 0, false) },
                null,
                null,
            )
    }
}
