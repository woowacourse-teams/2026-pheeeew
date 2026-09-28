package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.CurrentLocation
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
import com.pheeeew.feature.component.stamp.StampShapeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecordGroupSelectionTest {
    private val groupId = "10000000-0000-0000-0000-000000000001"
    private val sampleGroup =
        GroupStampItem(
            id = requireNotNull(GroupId.parse(groupId)),
            name = "서버 그룹",
            stamp =
                GroupStamp(
                    text = "모임",
                    textColor = requireNotNull(StampColor.parseServerValue("#112233")),
                    backgroundColor = requireNotNull(StampColor.parseServerValue("#44556680")),
                    frame = GroupStampFrame.STUB,
                ),
        )
    private val unusedRegistration =
        object : EmotionRegistrationRepository {
            override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult =
                EmotionRegistrationResult.Unavailable
        }

    @Test
    fun `등록 성공은 화면이 닫힌 뒤에도 서버 ID와 선택한 스탬프의 핀을 전달한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        object : EmotionRegistrationRepository {
                            override suspend fun register(registration: EmotionRegistration) =
                                EmotionRegistrationResult.Success(42)
                        },
                        GroupStampListRepository { GroupStampListLoadResult.Loaded(listOf(sampleGroup)) },
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.DISCOURAGED)
                advanceUntilIdle()
                val group = model.groupOptions.value.last()
                model.onGroupSelectionComplete(group)
                model.onSkip(CurrentLocation(37.52, 127.02, 5f, 0L))
                model.onConfirmLocation()
                advanceUntilIdle()
                assertNull(model.uiModel.value.selectedEmotion)
                val registered = model.registeredEmotions.first()
                assertEquals(42L, registered.id)
                assertEquals(37.52, registered.latitude)
                assertEquals(127.02, registered.longitude)
                assertEquals(EmotionTypeUiModel.DISCOURAGED, registered.emotion)
                assertEquals(group.stamp, registered.stamp)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `group button loads stamp options and preserves none as first choice`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val id = "10000000-0000-0000-0000-000000000001"
                val groups =
                    GroupStampListRepository {
                        GroupStampListLoadResult.Loaded(
                            listOf(
                                GroupStampItem(
                                    id = requireNotNull(GroupId.parse(id)),
                                    name = "서버 그룹",
                                    stamp =
                                        GroupStamp(
                                            text = "모임",
                                            textColor = requireNotNull(StampColor.parseServerValue("#112233")),
                                            backgroundColor = requireNotNull(StampColor.parseServerValue("#44556680")),
                                            frame = GroupStampFrame.STUB,
                                        ),
                                ),
                            ),
                        )
                    }
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        unusedRegistration,
                        groups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onGroupSelectorOpen()
                assertTrue(model.uiModel.value.isGroupSelectionLoading)
                assertTrue(!model.uiModel.value.isGroupSelectorVisible)
                advanceUntilIdle()

                val options = model.groupOptions.value
                assertTrue(model.uiModel.value.isGroupSelectorVisible)
                assertEquals(listOf("none", id), options.map { it.id })
                assertNull(options.first().stamp)
                assertEquals("모임", options.last().stamp?.label)
                assertEquals(StampShapeId.TICKET, options.last().stamp?.shape)
                assertEquals(0x80445566L, options.last().stamp?.fillArgb)
                model.onGroupSelectionComplete(options.last())
                assertEquals(id, model.uiModel.value.selectedGroupId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed lookup keeps none option and reports an error`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        unusedRegistration,
                        GroupStampListRepository { GroupStampListLoadResult.Unavailable },
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onGroupSelectorOpen()
                advanceUntilIdle()

                assertEquals(listOf("none"), model.groupOptions.value.map { it.id })
                assertTrue(!model.uiModel.value.isGroupSelectorVisible)
                assertTrue(model.notice.value?.isError == true)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `successful registration remembers group and restores it for the next record`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val storage = InMemoryLastRecordedGroupRepository()
                val registration =
                    object : EmotionRegistrationRepository {
                        override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult =
                            EmotionRegistrationResult.Success(42)
                    }
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        registration,
                        GroupStampListRepository { GroupStampListLoadResult.Loaded(listOf(sampleGroup)) },
                        storage,
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onGroupSelectionComplete(model.groupOptions.value.last())
                model.onSkip(CurrentLocation(37.5665, 126.9780, 1f, 0L))
                model.onConfirmLocation()
                advanceUntilIdle()
                assertEquals(groupId, storage.groupId)

                model.open(EmotionTypeUiModel.ANGRY)
                advanceUntilIdle()
                assertEquals(groupId, model.uiModel.value.selectedGroupId)
                assertEquals(
                    "모임",
                    model.groupOptions.value
                        .last()
                        .stamp
                        ?.label,
                )

                model.onGroupSelectionComplete(model.groupOptions.value.first())
                model.onSkip(CurrentLocation(37.5665, 126.9780, 1f, 0L))
                model.onConfirmLocation()
                advanceUntilIdle()
                assertNull(storage.groupId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `stored group missing from my groups resets the selection and local value`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val storage = InMemoryLastRecordedGroupRepository(groupId)
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        unusedRegistration,
                        GroupStampListRepository { GroupStampListLoadResult.Loaded(emptyList()) },
                        storage,
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()

                assertEquals("none", model.uiModel.value.selectedGroupId)
                assertNull(storage.groupId)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
