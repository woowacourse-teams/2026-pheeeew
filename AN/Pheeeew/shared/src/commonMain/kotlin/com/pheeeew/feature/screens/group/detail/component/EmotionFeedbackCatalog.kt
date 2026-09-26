package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.ui.graphics.Color
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_face_angry
import pheeeew.shared.generated.resources.emotion_face_discouraged
import pheeeew.shared.generated.resources.emotion_face_exhausted
import pheeeew.shared.generated.resources.emotion_face_frustrated
import pheeeew.shared.generated.resources.emotion_face_irritated
import pheeeew.shared.generated.resources.group_detail_emoji_angry_1
import pheeeew.shared.generated.resources.group_detail_emoji_angry_2
import pheeeew.shared.generated.resources.group_detail_emoji_angry_3
import pheeeew.shared.generated.resources.group_detail_emoji_annoyed_1
import pheeeew.shared.generated.resources.group_detail_emoji_annoyed_2
import pheeeew.shared.generated.resources.group_detail_emoji_annoyed_3
import pheeeew.shared.generated.resources.group_detail_emoji_blocked_1
import pheeeew.shared.generated.resources.group_detail_emoji_blocked_2
import pheeeew.shared.generated.resources.group_detail_emoji_blocked_3
import pheeeew.shared.generated.resources.group_detail_emoji_defeated_1
import pheeeew.shared.generated.resources.group_detail_emoji_defeated_2
import pheeeew.shared.generated.resources.group_detail_emoji_defeated_3
import pheeeew.shared.generated.resources.group_detail_emoji_tired_1
import pheeeew.shared.generated.resources.group_detail_emoji_tired_2
import pheeeew.shared.generated.resources.group_detail_emoji_tired_3
import pheeeew.shared.generated.resources.group_detail_emotion_angry
import pheeeew.shared.generated.resources.group_detail_emotion_annoyed
import pheeeew.shared.generated.resources.group_detail_emotion_blocked
import pheeeew.shared.generated.resources.group_detail_emotion_defeated
import pheeeew.shared.generated.resources.group_detail_emotion_tired
import pheeeew.shared.generated.resources.group_detail_sticker_angry_1
import pheeeew.shared.generated.resources.group_detail_sticker_angry_2
import pheeeew.shared.generated.resources.group_detail_sticker_angry_3
import pheeeew.shared.generated.resources.group_detail_sticker_annoyed_1
import pheeeew.shared.generated.resources.group_detail_sticker_annoyed_2
import pheeeew.shared.generated.resources.group_detail_sticker_annoyed_3
import pheeeew.shared.generated.resources.group_detail_sticker_blocked_1
import pheeeew.shared.generated.resources.group_detail_sticker_blocked_2
import pheeeew.shared.generated.resources.group_detail_sticker_blocked_3
import pheeeew.shared.generated.resources.group_detail_sticker_defeated_1
import pheeeew.shared.generated.resources.group_detail_sticker_defeated_2
import pheeeew.shared.generated.resources.group_detail_sticker_defeated_3
import pheeeew.shared.generated.resources.group_detail_sticker_tired_1
import pheeeew.shared.generated.resources.group_detail_sticker_tired_2
import pheeeew.shared.generated.resources.group_detail_sticker_tired_3
import kotlin.random.Random

internal enum class EmotionStickerKind {
    PlusOne,
    Text,
    Emoji,
    Face,
}

internal data class EmotionBurst(
    val id: Long,
    val emotion: EmotionKind,
    val stickerKind: EmotionStickerKind,
    val stickerVariant: Int,
    val horizontalDrift: Int,
    val verticalLaunchOffset: Int,
    val rotationDegrees: Float,
)

internal object EmotionFeedbackCatalog {
    fun create(
        id: Long,
        emotion: EmotionKind,
    ): EmotionBurst {
        val stickerKind = EmotionStickerKind.entries.random()
        val variant = Random.Default.nextInt(STICKER_VARIANT_COUNT)
        val lane = (id % BURST_LANE_COUNT).toInt()
        val horizontalDrift = listOf(-42, -26, 22, 38, 0)[lane]
        val verticalLaunchOffset = listOf(-8, -24, -40, -56, -72)[lane]
        val rotation = listOf(-7f, 5f, -3f, 7f, 2f)[lane]
        return EmotionBurst(id, emotion, stickerKind, variant, horizontalDrift, verticalLaunchOffset, rotation)
    }

    fun color(emotion: EmotionKind): Color =
        when (emotion) {
            EmotionKind.Blocked -> Color(0xFFF2B6A3)
            EmotionKind.Annoyed -> Color(0xFFE8B5C4)
            EmotionKind.Tired -> Color(0xFFCDC7E4)
            EmotionKind.Defeated -> Color(0xFFAFCBDD)
            EmotionKind.Angry -> Color(0xFFEA9E92)
        }

    fun face(emotion: EmotionKind): DrawableResource =
        when (emotion) {
            EmotionKind.Blocked -> Res.drawable.emotion_face_frustrated
            EmotionKind.Annoyed -> Res.drawable.emotion_face_irritated
            EmotionKind.Tired -> Res.drawable.emotion_face_exhausted
            EmotionKind.Defeated -> Res.drawable.emotion_face_discouraged
            EmotionKind.Angry -> Res.drawable.emotion_face_angry
        }

    fun name(emotion: EmotionKind): StringResource =
        when (emotion) {
            EmotionKind.Blocked -> Res.string.group_detail_emotion_blocked
            EmotionKind.Annoyed -> Res.string.group_detail_emotion_annoyed
            EmotionKind.Tired -> Res.string.group_detail_emotion_tired
            EmotionKind.Defeated -> Res.string.group_detail_emotion_defeated
            EmotionKind.Angry -> Res.string.group_detail_emotion_angry
        }

    fun stickerText(
        emotion: EmotionKind,
        variant: Int,
    ): StringResource =
        when (emotion) {
            EmotionKind.Blocked -> {
                when (variant) {
                    0 -> Res.string.group_detail_sticker_blocked_1
                    1 -> Res.string.group_detail_sticker_blocked_2
                    else -> Res.string.group_detail_sticker_blocked_3
                }
            }

            EmotionKind.Annoyed -> {
                when (variant) {
                    0 -> Res.string.group_detail_sticker_annoyed_1
                    1 -> Res.string.group_detail_sticker_annoyed_2
                    else -> Res.string.group_detail_sticker_annoyed_3
                }
            }

            EmotionKind.Tired -> {
                when (variant) {
                    0 -> Res.string.group_detail_sticker_tired_1
                    1 -> Res.string.group_detail_sticker_tired_2
                    else -> Res.string.group_detail_sticker_tired_3
                }
            }

            EmotionKind.Defeated -> {
                when (variant) {
                    0 -> Res.string.group_detail_sticker_defeated_1
                    1 -> Res.string.group_detail_sticker_defeated_2
                    else -> Res.string.group_detail_sticker_defeated_3
                }
            }

            EmotionKind.Angry -> {
                when (variant) {
                    0 -> Res.string.group_detail_sticker_angry_1
                    1 -> Res.string.group_detail_sticker_angry_2
                    else -> Res.string.group_detail_sticker_angry_3
                }
            }
        }

    fun emoji(
        emotion: EmotionKind,
        variant: Int,
    ): StringResource =
        when (emotion) {
            EmotionKind.Blocked -> {
                when (variant) {
                    0 -> Res.string.group_detail_emoji_blocked_1
                    1 -> Res.string.group_detail_emoji_blocked_2
                    else -> Res.string.group_detail_emoji_blocked_3
                }
            }

            EmotionKind.Annoyed -> {
                when (variant) {
                    0 -> Res.string.group_detail_emoji_annoyed_1
                    1 -> Res.string.group_detail_emoji_annoyed_2
                    else -> Res.string.group_detail_emoji_annoyed_3
                }
            }

            EmotionKind.Tired -> {
                when (variant) {
                    0 -> Res.string.group_detail_emoji_tired_1
                    1 -> Res.string.group_detail_emoji_tired_2
                    else -> Res.string.group_detail_emoji_tired_3
                }
            }

            EmotionKind.Defeated -> {
                when (variant) {
                    0 -> Res.string.group_detail_emoji_defeated_1
                    1 -> Res.string.group_detail_emoji_defeated_2
                    else -> Res.string.group_detail_emoji_defeated_3
                }
            }

            EmotionKind.Angry -> {
                when (variant) {
                    0 -> Res.string.group_detail_emoji_angry_1
                    1 -> Res.string.group_detail_emoji_angry_2
                    else -> Res.string.group_detail_emoji_angry_3
                }
            }
        }

    private const val BURST_LANE_COUNT = 5
    private const val STICKER_VARIANT_COUNT = 3
}
