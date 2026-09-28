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
    val groupLabel: String = "개인",
    val isGroupSelectorVisible: Boolean = false,
    val selectedGroupId: String = "personal",
    val pendingGroupId: String = "personal",
    val groupDialProgress: Float = 0f,
    val origin: GeoCoordinate? = null,
    val selectedCoordinate: GeoCoordinate? = null,
    val isSelectedCoordinateInRange: Boolean = false,
    val locationMessage: String? = null,
    val submissionMessage: String? = null,
    val isSubmitting: Boolean = false,
)
