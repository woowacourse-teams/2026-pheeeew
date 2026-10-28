package com.pheeeew.feature.emotion.component

import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.DrawableResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_angry_stage_0
import pheeeew.shared.generated.resources.press_angry_stage_1
import pheeeew.shared.generated.resources.press_angry_stage_2
import pheeeew.shared.generated.resources.press_angry_stage_3
import pheeeew.shared.generated.resources.press_angry_stage_4
import pheeeew.shared.generated.resources.press_angry_stage_5
import pheeeew.shared.generated.resources.press_annoyed_stage_0
import pheeeew.shared.generated.resources.press_annoyed_stage_1
import pheeeew.shared.generated.resources.press_annoyed_stage_2
import pheeeew.shared.generated.resources.press_annoyed_stage_3
import pheeeew.shared.generated.resources.press_annoyed_stage_4
import pheeeew.shared.generated.resources.press_annoyed_stage_5
import pheeeew.shared.generated.resources.press_blocked_stage_0
import pheeeew.shared.generated.resources.press_blocked_stage_1
import pheeeew.shared.generated.resources.press_blocked_stage_2
import pheeeew.shared.generated.resources.press_blocked_stage_3
import pheeeew.shared.generated.resources.press_blocked_stage_4
import pheeeew.shared.generated.resources.press_blocked_stage_5
import pheeeew.shared.generated.resources.press_defeated_stage_0
import pheeeew.shared.generated.resources.press_defeated_stage_1
import pheeeew.shared.generated.resources.press_defeated_stage_2
import pheeeew.shared.generated.resources.press_defeated_stage_3
import pheeeew.shared.generated.resources.press_defeated_stage_4
import pheeeew.shared.generated.resources.press_defeated_stage_5
import pheeeew.shared.generated.resources.press_tired_stage_0
import pheeeew.shared.generated.resources.press_tired_stage_1
import pheeeew.shared.generated.resources.press_tired_stage_2
import pheeeew.shared.generated.resources.press_tired_stage_3
import pheeeew.shared.generated.resources.press_tired_stage_4
import pheeeew.shared.generated.resources.press_tired_stage_5

/** Press-only artwork; existing registration and sticker face resources stay unchanged. */
internal object PressFaceCatalog {
    // Common transparent canvas; the face stays at its original scale while fragments have room.
    const val CANVAS_SIZE = 180

    private val artwork =
        mapOf(
            EmotionKind.Angry to
                mapOf(
                    EmotionFaceStage.Intact to Res.drawable.press_angry_stage_0,
                    EmotionFaceStage.Hairline to Res.drawable.press_angry_stage_1,
                    EmotionFaceStage.Cracked to Res.drawable.press_angry_stage_2,
                    EmotionFaceStage.Fractured to Res.drawable.press_angry_stage_3,
                    EmotionFaceStage.Shattered to Res.drawable.press_angry_stage_4,
                    EmotionFaceStage.Burst to Res.drawable.press_angry_stage_5,
                ),
            EmotionKind.Blocked to
                mapOf(
                    EmotionFaceStage.Intact to Res.drawable.press_blocked_stage_0,
                    EmotionFaceStage.Hairline to Res.drawable.press_blocked_stage_1,
                    EmotionFaceStage.Cracked to Res.drawable.press_blocked_stage_2,
                    EmotionFaceStage.Fractured to Res.drawable.press_blocked_stage_3,
                    EmotionFaceStage.Shattered to Res.drawable.press_blocked_stage_4,
                    EmotionFaceStage.Burst to Res.drawable.press_blocked_stage_5,
                ),
            EmotionKind.Tired to
                mapOf(
                    EmotionFaceStage.Intact to Res.drawable.press_tired_stage_0,
                    EmotionFaceStage.Hairline to Res.drawable.press_tired_stage_1,
                    EmotionFaceStage.Cracked to Res.drawable.press_tired_stage_2,
                    EmotionFaceStage.Fractured to Res.drawable.press_tired_stage_3,
                    EmotionFaceStage.Shattered to Res.drawable.press_tired_stage_4,
                    EmotionFaceStage.Burst to Res.drawable.press_tired_stage_5,
                ),
            EmotionKind.Annoyed to
                mapOf(
                    EmotionFaceStage.Intact to Res.drawable.press_annoyed_stage_0,
                    EmotionFaceStage.Hairline to Res.drawable.press_annoyed_stage_1,
                    EmotionFaceStage.Cracked to Res.drawable.press_annoyed_stage_2,
                    EmotionFaceStage.Fractured to Res.drawable.press_annoyed_stage_3,
                    EmotionFaceStage.Shattered to Res.drawable.press_annoyed_stage_4,
                    EmotionFaceStage.Burst to Res.drawable.press_annoyed_stage_5,
                ),
            EmotionKind.Defeated to
                mapOf(
                    EmotionFaceStage.Intact to Res.drawable.press_defeated_stage_0,
                    EmotionFaceStage.Hairline to Res.drawable.press_defeated_stage_1,
                    EmotionFaceStage.Cracked to Res.drawable.press_defeated_stage_2,
                    EmotionFaceStage.Fractured to Res.drawable.press_defeated_stage_3,
                    EmotionFaceStage.Shattered to Res.drawable.press_defeated_stage_4,
                    EmotionFaceStage.Burst to Res.drawable.press_defeated_stage_5,
                ),
        )

    fun resource(
        kind: EmotionKind,
        stage: EmotionFaceStage,
    ): DrawableResource = artwork.getValue(kind).getValue(stage)
}

/** Main cap radius and baked shadow spread in the original 180-unit PNG canvas. */
internal data class PressFaceCapGeometry(
    val radius: Float = 46f,
    val shadowSpread: Float = 0f,
)

private val standardCapGeometry = PressFaceCapGeometry()
private val tiredCapGeometry = PressFaceCapGeometry(radius = 47.5f, shadowSpread = 8.5f)

internal fun pressFaceCapGeometry(
    kind: EmotionKind,
    stage: EmotionFaceStage,
): PressFaceCapGeometry =
    if (kind == EmotionKind.Tired) {
        when (stage) {
            EmotionFaceStage.Intact, EmotionFaceStage.Hairline,
            EmotionFaceStage.Cracked, EmotionFaceStage.Fractured,
            -> tiredCapGeometry

            else -> standardCapGeometry
        }
    } else {
        standardCapGeometry
    }
