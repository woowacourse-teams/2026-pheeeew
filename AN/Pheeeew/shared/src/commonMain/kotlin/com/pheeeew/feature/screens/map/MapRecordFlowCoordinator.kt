package com.pheeeew.feature.screens.map

import com.pheeeew.core.audio.VoiceRecorder
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel

/** Coordinates recording side effects with the map and record-flow state. */
internal class MapRecordFlowCoordinator(
    private val mapViewModel: MapViewModel,
    private val recordViewModel: MapRecordViewModel,
    private val voiceRecorder: VoiceRecorder,
) {
    fun onEmotionSelected(emotion: EmotionTypeUiModel) {
        mapViewModel.onEmotionBubbleSelected()
        voiceRecorder.clear()
        recordViewModel.open(emotion)
    }

    fun dismiss() {
        if (recordViewModel.uiModel.value.isSubmitting) return
        voiceRecorder.clear()
        recordViewModel.dismiss()
        mapViewModel.onRecordLocationPickingChanged(false)
    }

    fun changeInputMode(mode: RecordInputModeUiModel) {
        voiceRecorder.stop()
        voiceRecorder.pause()
        recordViewModel.onInputModeChange(mode)
    }

    fun next(currentLocation: CurrentLocation?) {
        if (recordViewModel.uiModel.value.isGroupSelectionLoading) return
        voiceRecorder.stop()
        voiceRecorder.pause()
        recordViewModel.onNext(currentLocation, voiceRecorder.state.value.filePath)
        mapViewModel.onRecordLocationPickingChanged(true)
    }

    fun skip(currentLocation: CurrentLocation?) {
        if (recordViewModel.uiModel.value.isGroupSelectionLoading) return
        voiceRecorder.clear()
        recordViewModel.onSkip(currentLocation)
        mapViewModel.onRecordLocationPickingChanged(true)
    }

    fun backToInput() {
        if (recordViewModel.uiModel.value.isSubmitting) return
        recordViewModel.onBackToInput()
        mapViewModel.onRecordLocationPickingChanged(false)
    }

    fun confirmLocation() {
        recordViewModel.onConfirmLocation()
        if (recordViewModel.uiModel.value.step == RecordFlowStepUiModel.Closed) {
            mapViewModel.onRecordLocationPickingChanged(false)
        }
    }
}
