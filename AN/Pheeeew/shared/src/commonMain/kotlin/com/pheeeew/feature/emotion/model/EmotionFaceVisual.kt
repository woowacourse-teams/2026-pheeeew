package com.pheeeew.feature.emotion.model

/** Visual state only; unrelated to accepted press counts or server scores. */
internal data class EmotionFaceVisual(
    val stage: EmotionFaceStage = EmotionFaceStage.Intact,
    val playbackId: Long = 0,
)

internal enum class EmotionFaceStage {
    Intact,
    Hairline,
    Cracked,
    Fractured,
    Shattered,
    Burst,
    ;

    fun next(): EmotionFaceStage = progression[(progression.indexOf(this) + 1) % progression.size]

    companion object {
        /** Explicit display order, independent of enum declaration and resource ordering. */
        val progression = listOf(Intact, Hairline, Cracked, Fractured, Shattered, Burst)
    }
}
