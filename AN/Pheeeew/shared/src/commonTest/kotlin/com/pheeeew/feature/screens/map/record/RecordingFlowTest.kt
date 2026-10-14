package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingFlowTest {
    private val unusedGroups = GroupStampListRepository { GroupStampListLoadResult.Loaded(emptyList()) }
    private val unusedRepository =
        object : EmotionRegistrationRepository {
            override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult =
                EmotionRegistrationResult.Unavailable
        }

    @Test
    fun successfulRegistrationExposesPinIdAndCoordinateOnce() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val selected = GeoCoordinate(37.567, 126.979)
                val repository =
                    object : EmotionRegistrationRepository {
                        override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult {
                            assertEquals(selected, registration.coordinate)
                            return EmotionRegistrationResult.Success(42)
                        }
                    }
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        repository,
                        unusedGroups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                // Registering at the same location again must produce another consumable result.
                repeat(2) {
                    model.open(EmotionTypeUiModel.FRUSTRATED)
                    advanceUntilIdle()
                    model.onMemoChange("기록")
                    model.onNext(CurrentLocation(37.5665, 126.978, 1f, 0L), null)
                    model.onLocationSelected(selected.latitude, selected.longitude)
                    model.onConfirmLocation()
                    assertNull(model.registeredEmotion.value)
                    advanceUntilIdle()
                    assertEquals(RecordFlowStepUiModel.Closed, model.uiModel.value.step)
                    assertEquals(RegisteredEmotionUiModel(42, selected), model.registeredEmotion.value)
                    assertEquals(RegisteredEmotionUiModel(42, selected), model.consumeRegisteredEmotion())
                    assertNull(model.consumeRegisteredEmotion())
                    model.dismissNotice()
                    assertNull(model.registeredEmotion.value)
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun failedOrCancelledRegistrationDoesNotRequestCameraMovement() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        unusedRepository,
                        unusedGroups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onMemoChange("기록")
                model.onNext(CurrentLocation(37.5665, 126.978, 1f, 0L), null)
                model.onConfirmLocation()
                advanceUntilIdle()
                assertEquals(true, model.notice.value?.isError)
                assertNull(model.consumeRegisteredEmotion())
                model.dismiss()
                assertNull(model.consumeRegisteredEmotion())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun recordingSurvivesLocationSelectionAndReturn() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        unusedRepository,
                        unusedGroups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onInputModeChange(RecordInputModeUiModel.Recording)
                model.onNext(null, "/tmp/voice.m4a")
                assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
                model.onBackToInput()
                assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun emptyInputDoesNotAdvanceAndDismissDiscardsRecordingReference() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        unusedRepository,
                        unusedGroups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onInputModeChange(RecordInputModeUiModel.Recording)
                model.onNext(null, null)
                assertEquals(RecordFlowStepUiModel.Input, model.uiModel.value.step)
                model.onNext(null, "/tmp/voice.m4a")
                assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
                model.dismiss()
                assertNull(model.uiModel.value.recordingFilePath)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
