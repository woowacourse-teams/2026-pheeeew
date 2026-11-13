package com.pheeeew.feature.emotion.model

/** One emotion and its displayed aggregate count. */
data class EmotionCountUiModel(
    val kind: EmotionKind,
    val count: Long,
) {
    init {
        require(count >= 0L) { "감정 횟수는 음수일 수 없습니다." }
    }
}
