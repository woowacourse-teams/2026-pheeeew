package com.pheeeew.feature.screens.group.create.model

/** 불투명 스탬프 색을 편집하는 HSV 값입니다. */
data class StampColorSelection(
    val hueDegrees: Float,
    val saturation: Float,
    val value: Float,
) {
    init {
        require(hueDegrees >= 0f && hueDegrees < 360f)
        require(saturation in 0f..1f)
        require(value in 0f..1f)
    }
}

sealed interface StampColorSheetState {
    data object Closed : StampColorSheetState

    data class Editing(val selection: StampColorSelection) : StampColorSheetState
}
