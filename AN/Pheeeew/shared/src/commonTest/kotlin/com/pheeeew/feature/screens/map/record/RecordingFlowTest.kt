package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingFlowTest {
    @Test
    fun recordingSurvivesLocationSelectionAndReturn() {
        val model = MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase())
        model.open(EmotionTypeUiModel.Stuck)
        model.onNext(null, "/tmp/voice.m4a")
        assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
        model.onBackToInput()
        assertEquals("/tmp/voice.m4a", model.uiModel.value.recordingFilePath)
    }

    @Test
    fun skipAndDismissDiscardRecordingReference() {
        val model = MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase())
        model.open(EmotionTypeUiModel.Stuck)
        model.onNext(null, "/tmp/voice.m4a")
        model.onBackToInput()
        model.onSkip(null)
        assertNull(model.uiModel.value.recordingFilePath)
        model.open(EmotionTypeUiModel.Stuck)
        model.onNext(null, "/tmp/voice.m4a")
        model.dismiss()
        assertNull(model.uiModel.value.recordingFilePath)
    }
}
