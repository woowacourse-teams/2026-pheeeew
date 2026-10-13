package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.GroupStampItem
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembershipRefreshTest {
    @Test
    fun `automatic membership refresh keeps the closed selector closed and explicit opening still works`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val groups = MutableGroupStampListRepository()
                val record =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        registrationRepository =
                            object : EmotionRegistrationRepository {
                                override suspend fun register(registration: EmotionRegistration) =
                                    EmotionRegistrationResult.Unavailable
                            },
                        groupStampListRepository = groups,
                        lastRecordedGroupRepository = InMemoryLastRecordedGroupRepository(),
                    )
                record.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                assertFalse(record.uiModel.value.isGroupSelectorVisible)

                groups.currentGroups = listOf(group())
                groups.invalidate()
                advanceUntilIdle()

                assertEquals(listOf("none", GROUP_ID), record.groupOptions.value.map { it.id })
                assertFalse(record.uiModel.value.isGroupSelectorVisible)

                record.onGroupSelectorOpen()
                assertFalse(record.uiModel.value.isGroupSelectorVisible)
                advanceUntilIdle()
                assertTrue(record.uiModel.value.isGroupSelectorVisible)

                groups.currentGroups = emptyList()
                groups.invalidate()
                advanceUntilIdle()
                assertTrue(record.uiModel.value.isGroupSelectorVisible)
                assertEquals(listOf("none"), record.groupOptions.value.map { it.id })
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `record selector updates membership and clears a removed selection`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val groups = MutableGroupStampListRepository()
                val record =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        registrationRepository =
                            object : EmotionRegistrationRepository {
                                override suspend fun register(registration: EmotionRegistration) =
                                    EmotionRegistrationResult.Unavailable
                            },
                        groupStampListRepository = groups,
                        lastRecordedGroupRepository = InMemoryLastRecordedGroupRepository(),
                    )
                record.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                assertEquals(listOf("none"), record.groupOptions.value.map { it.id })

                groups.currentGroups = listOf(group())
                groups.invalidate()
                advanceUntilIdle()
                assertEquals(listOf("none", GROUP_ID), record.groupOptions.value.map { it.id })

                record.onGroupSelectionComplete(record.groupOptions.value.last())
                groups.currentGroups = emptyList()
                groups.invalidate()
                advanceUntilIdle()
                assertEquals("none", record.uiModel.value.selectedGroupId)
                assertEquals(listOf("none"), record.groupOptions.value.map { it.id })
                assertEquals(RecordFlowStepUiModel.Input, record.uiModel.value.step)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `late record group lookup cannot overwrite the list loaded for a newer membership revision`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val staleResponse = CompletableDeferred<GroupStampListLoadResult>()
                val groups = StaleFirstLoadGroupStampListRepository(staleResponse)
                val record =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        registrationRepository =
                            object : EmotionRegistrationRepository {
                                override suspend fun register(registration: EmotionRegistration) =
                                    EmotionRegistrationResult.Unavailable
                            },
                        groupStampListRepository = groups,
                        lastRecordedGroupRepository = InMemoryLastRecordedGroupRepository(),
                    )

                record.open(EmotionTypeUiModel.FRUSTRATED)
                runCurrent()
                groups.currentGroups = listOf(group())
                groups.invalidate()
                advanceUntilIdle()
                assertEquals(listOf("none", GROUP_ID), record.groupOptions.value.map { it.id })

                staleResponse.complete(GroupStampListLoadResult.Loaded(emptyList()))
                advanceUntilIdle()
                assertEquals(listOf("none", GROUP_ID), record.groupOptions.value.map { it.id })
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class MutableGroupStampListRepository : GroupStampListRepository {
        private val revision = MutableStateFlow(0L)
        override val membershipChanges = revision.asStateFlow()
        var currentGroups: List<GroupStampItem> = emptyList()

        override suspend fun findMyStamps(): GroupStampListLoadResult = GroupStampListLoadResult.Loaded(currentGroups)

        override fun invalidate() {
            revision.value += 1
        }
    }

    private class StaleFirstLoadGroupStampListRepository(
        private val staleResponse: CompletableDeferred<GroupStampListLoadResult>,
    ) : GroupStampListRepository {
        private val revision = MutableStateFlow(0L)
        override val membershipChanges = revision.asStateFlow()
        var currentGroups: List<GroupStampItem> = emptyList()
        private var loadCount = 0

        override suspend fun findMyStamps(): GroupStampListLoadResult {
            loadCount += 1
            return if (loadCount == 1) {
                withContext(NonCancellable) { staleResponse.await() }
            } else {
                GroupStampListLoadResult.Loaded(currentGroups)
            }
        }

        override fun invalidate() {
            revision.value += 1
        }
    }

    private fun group() =
        GroupStampItem(
            id = requireNotNull(GroupId.parse(GROUP_ID)),
            name = "새 그룹",
            stamp =
                GroupStamp(
                    text = "모임",
                    textColor = requireNotNull(StampColor.parseServerValue("#112233")),
                    backgroundColor = requireNotNull(StampColor.parseServerValue("#FFE164")),
                    frame = GroupStampFrame.CIRCLE,
                ),
        )

    private companion object {
        const val GROUP_ID = "10000000-0000-0000-0000-000000000001"
    }
}
