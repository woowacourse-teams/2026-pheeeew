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
    val recordingFilePath: String? = null,
    val isGroupSelectorVisible: Boolean = false,
    val selectedGroupId: String = "none",
    val pendingGroupId: String = "none",
    val isGroupSelectionLoading: Boolean = false,
    val groupDialProgress: Float = 0f,
    val origin: GeoCoordinate? = null,
    val selectedCoordinate: GeoCoordinate? = null,
    val isSelectedCoordinateInRange: Boolean = false,
    val isSubmitting: Boolean = false,
)
