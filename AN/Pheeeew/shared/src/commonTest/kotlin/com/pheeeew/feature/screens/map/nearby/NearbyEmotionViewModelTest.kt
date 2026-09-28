package com.pheeeew.feature.screens.map.nearby

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.GroupStampItem
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionRepository
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
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
                vm.onViewportChanged(BOUNDS)
                vm.open()
                advanceUntilIdle()
                val loaded = vm.state.value
                vm.onViewportChanged(EmotionBounds(1.0, 1.0, 2.0, 2.0))
                vm.select(1)
                vm.openOnMap(1) { id, target ->
                    assertEquals(1L, id)
                    assertEquals(coordinate, target)
                    true
                }
                assertEquals(loaded, vm.state.value)
                assertEquals(listOf(BOUNDS), repo.bounds)
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `시트 열린 뒤 지도 콜백 변경은 캡처한 목록 범위를 바꾸지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repo = FakeRepository()
                val vm = viewModel(repo)
                vm.onViewportChanged(BOUNDS)
                vm.open()
                advanceUntilIdle()
                vm.onViewportChanged(EmotionBounds(1.0, 1.0, 2.0, 2.0))
                vm.refresh()
                advanceUntilIdle()
                assertEquals(listOf(BOUNDS, BOUNDS), repo.bounds)
                assertEquals(listOf<String?>(null, null), repo.groupIds)
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
                vm.onViewportChanged(BOUNDS)
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
                vm.onViewportChanged(BOUNDS)
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
                vm.onViewportChanged(BOUNDS)
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
    fun `내 글 신고 차단 메뉴는 열리지 않고 가입 그룹 실패에도 전체 조회는 가능하다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repo = FakeRepository()
                repo.first = { EmotionResult.Success(EmotionPage(listOf(emotion(1).copy(isMine = true)), null)) }
                val vm = viewModel(repo)
                vm.onViewportChanged(BOUNDS)
                vm.open()
                advanceUntilIdle()
                vm.requestBlock(1)
                assertEquals(null, vm.state.value.blockId)
                assertEquals(
                    ALL_GROUPS,
                    vm.state.value.groups
                        .single()
                        .id,
                )
                assertFalse(vm.state.value.loading)
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `membership refresh replaces stale groups and ignores an older response`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val gate = CompletableDeferred<Unit>()
                var calls = 0
                val vm =
                    NearbyEmotionViewModel(
                        FakeRepository(),
                        GroupStampListRepository {
                            if (calls++ == 0) {
                                withContext(NonCancellable) { gate.await() }
                                GroupStampListLoadResult.Loaded(emptyList())
                            } else {
                                GroupStampListLoadResult.Loaded(listOf(group()))
                            }
                        },
                    )
                vm.openGroups()
                runCurrent()
                vm.onMembershipChanged()
                runCurrent()
                assertEquals(
                    listOf(ALL_GROUPS, group().id.value),
                    vm.state.value.groups
                        .map { it.id },
                )
                gate.complete(Unit)
                advanceUntilIdle()
                assertEquals(
                    listOf(ALL_GROUPS, group().id.value),
                    vm.state.value.groups
                        .map { it.id },
                )
                assertFalse(vm.state.value.groupsLoading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `removed selected group resets filter pending selection and dial and reloads all emotions`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var availableGroups = listOf(group())
                val repo = FakeRepository()
                val vm =
                    NearbyEmotionViewModel(
                        repo,
                        GroupStampListRepository {
                            GroupStampListLoadResult.Loaded(availableGroups)
                        },
                    )
                vm.onViewportChanged(BOUNDS)
                vm.open()
                advanceUntilIdle()
                vm.openGroups()
                advanceUntilIdle()
                vm.completeGroup(
                    vm.state.value.groups
                        .last(),
                )
                advanceUntilIdle()
                vm.openGroups()
                advanceUntilIdle()
                assertEquals(1f, vm.state.value.dialProgress)
                availableGroups = emptyList()
                vm.loadGroups()
                advanceUntilIdle()
                assertEquals(ALL_GROUPS, vm.state.value.groupId)
                assertEquals(ALL_GROUPS, vm.state.value.pendingGroupId)
                assertEquals(0f, vm.state.value.dialProgress)
                assertEquals(listOf(null, group().id.value, null), repo.groupIds)
                vm.dismiss()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed membership refresh can be retried without restoring stale options`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var fail = false
                val vm =
                    NearbyEmotionViewModel(
                        FakeRepository(),
                        GroupStampListRepository {
                            if (fail) error("unavailable")
                            GroupStampListLoadResult.Loaded(listOf(group()))
                        },
                    )
                vm.openGroups()
                advanceUntilIdle()
                fail = true
                vm.onMembershipChanged()
                advanceUntilIdle()
                assertEquals(
                    listOf(ALL_GROUPS),
                    vm.state.value.groups
                        .map { it.id },
                )
                assertTrue(vm.state.value.groupsError)
                assertFalse(vm.state.value.groupsLoading)
                fail = false
                vm.loadGroups()
                advanceUntilIdle()
                assertEquals(
                    listOf(ALL_GROUPS, group().id.value),
                    vm.state.value.groups
                        .map { it.id },
                )
                assertFalse(vm.state.value.groupsError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun group() =
        GroupStampItem(
            requireNotNull(GroupId.parse("10000000-0000-0000-0000-000000000001")),
            "새 그룹",
            GroupStamp(
                "모임",
                requireNotNull(StampColor.parseServerValue("#112233")),
                requireNotNull(StampColor.parseServerValue("#FFFFFF")),
                GroupStampFrame.CIRCLE,
            ),
        )

    private fun viewModel(repo: FakeRepository) =
        NearbyEmotionViewModel(
            repo,
            GroupStampListRepository {
                GroupStampListLoadResult.Unavailable
            },
        )

    private class FakeRepository : EmotionRepository {
        val bounds = mutableListOf<EmotionBounds>()
        val groupIds = mutableListOf<String?>()
        var first: suspend () -> EmotionResult<EmotionPage> = { successPage(1) }
        var next: suspend () -> EmotionResult<EmotionPage> = { successPage(2) }

        override suspend fun firstPage(
            bounds: EmotionBounds,
            groupId: String?,
        ): EmotionResult<EmotionPage> {
            this.bounds += bounds
            groupIds += groupId
            return first()
        }

        override suspend fun nextPage(cursor: String) = next()

        override suspend fun detail(id: Long): EmotionResult<Emotion> =
            EmotionResult.Failure(EmotionFailure.UNAVAILABLE)

        override suspend fun react(
            id: Long,
            type: EmotionReactionType,
            selected: Boolean,
        ) = EmotionResult.Success(Unit)

        override suspend fun block(id: Long) = EmotionResult.Success(Unit)
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
