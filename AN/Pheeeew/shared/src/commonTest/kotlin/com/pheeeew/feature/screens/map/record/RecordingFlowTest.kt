package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
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
                model.onNext(null, "/tmp/voice.m4a")
                assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
                model.onBackToInput()
                assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun skipAndDismissDiscardRecordingReference() =
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
                model.onNext(null, "/tmp/voice.m4a")
                model.onBackToInput()
                model.onSkip(null)
                assertNull(model.uiModel.value.recordingFilePath)
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onNext(null, "/tmp/voice.m4a")
                model.dismiss()
                assertNull(model.uiModel.value.recordingFilePath)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
