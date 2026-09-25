package com.pheeeew.feature.component.stamp

/** 그룹 스탬프를 화면에 표시하기 위한 값 객체입니다. 색상은 0xAARRGGBB 형식입니다. */
data class StampAppearanceUiModel(
    val label: String,
    val shape: StampShapeId,
    val fillArgb: Long,
    val textArgb: Long,
) {
    init {
        require(fillArgb in ARGB_RANGE) { "fillArgb는 32비트 ARGB 값이어야 합니다." }
        require(textArgb in ARGB_RANGE) { "textArgb는 32비트 ARGB 값이어야 합니다." }
    }

    private companion object {
        val ARGB_RANGE = 0L..0xFFFF_FFFFL
    }
}
