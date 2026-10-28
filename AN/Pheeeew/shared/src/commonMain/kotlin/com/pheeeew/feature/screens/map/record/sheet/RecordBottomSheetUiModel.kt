package com.pheeeew.feature.screens.map.record.sheet

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel

enum class RecordFlowStepUiModel {
    Closed,
    Input,
    LocationSelection,
}

enum class RecordInputModeUiModel {
    Memo,
    Recording,
}

data class RecordBottomSheetUiModel(
    val step: RecordFlowStepUiModel = RecordFlowStepUiModel.Closed,
    val selectedEmotion: EmotionTypeUiModel? = null,
    val inputMode: RecordInputModeUiModel = RecordInputModeUiModel.Memo,
    val memo: String = "",
    val isAnonymous: Boolean = false,
    val recordingFilePath: String? = null,
    val origin: GeoCoordinate? = null,
    val selectedCoordinate: GeoCoordinate? = null,
    val hasMovedStamp: Boolean = false,
    val isSelectedCoordinateInRange: Boolean = false,
    val isSubmitting: Boolean = false,
)
