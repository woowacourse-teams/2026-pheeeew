package com.pheeeew.feature.emotion.component

/** Button positions in the 354dp reference width used by [EmotionPad]. */
internal enum class EmotionPadArrangement(
    val buttonOrigins: List<EmotionButtonOrigin>,
) {
    ThreeTwo(
        listOf(
            EmotionButtonOrigin(0f, 0f),
            EmotionButtonOrigin(124.5f, 0f),
            EmotionButtonOrigin(249f, 0f),
            EmotionButtonOrigin(62.25f, 177f),
            EmotionButtonOrigin(186.75f, 177f),
        ),
    ),
    TwoThree(
        listOf(
            EmotionButtonOrigin(62.25f, 0f),
            EmotionButtonOrigin(186.75f, 0f),
            EmotionButtonOrigin(0f, 177f),
            EmotionButtonOrigin(124.5f, 177f),
            EmotionButtonOrigin(249f, 177f),
        ),
    ),
}

internal data class EmotionButtonOrigin(
    val x: Float,
    val y: Float,
)
