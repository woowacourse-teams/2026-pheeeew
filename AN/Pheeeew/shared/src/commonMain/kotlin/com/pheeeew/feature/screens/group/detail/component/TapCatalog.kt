package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.ui.graphics.Color
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import org.jetbrains.compose.resources.DrawableResource

internal enum class TapReactionKind {
    Face,
    Text,
    Emoji,
    Plus,
}

internal data class TapReaction(
    val kind: TapReactionKind,
    val value: String,
)

internal data class TapEmotion(
    val label: String,
    val color: Color,
    val face: DrawableResource,
    val texts: List<String>,
    val emojis: List<String>,
)

internal object TapCatalog {
    fun emotion(kind: EmotionKind): TapEmotion =
        when (kind) {
            EmotionKind.Blocked -> {
                TapEmotion(
                    label = "답답",
                    color = Color(0xFFF8D3C0),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts = listOf("아오!!", "으아아", "꽉 막혔어", "후우…"),
                    emojis = listOf("😮‍💨", "😤"),
                )
            }

            EmotionKind.Annoyed -> {
                TapEmotion(
                    label = "짜증",
                    color = Color(0xFFF3C7D6),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts = listOf("아 진짜!", "으으…", "또?!", "그만 좀!"),
                    emojis = listOf("💢", "🙄", "😑"),
                )
            }

            EmotionKind.Tired -> {
                TapEmotion(
                    label = "지침",
                    color = Color(0xFFE1D9F0),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts = listOf("ㅠㅠ", "방전…", "기력 0", "눕고 싶다"),
                    emojis = listOf("🫠", "🥱", "🪫"),
                )
            }

            EmotionKind.Defeated -> {
                TapEmotion(
                    label = "좌절",
                    color = Color(0xFFCCE5F2),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts = listOf("ㅠㅠ", "털썩…", "안 돼…", "와르르"),
                    emojis = listOf("😭", "🥲", "💧"),
                )
            }

            EmotionKind.Angry -> {
                TapEmotion(
                    label = "분노",
                    color = Color(0xFFF5BEB3),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts = listOf("으아악!!", "부글부글", "폭발 직전", "!!!"),
                    emojis = listOf("😡", "🤬", "🔥"),
                )
            }
        }

    fun pick(
        kind: EmotionKind,
        random: TapRandom,
    ): TapReaction {
        val emotion = emotion(kind)
        return when (random.int(200)) {
            in 0..79 -> TapReaction(TapReactionKind.Face, kind.name.lowercase())
            in 80..154 -> TapReaction(TapReactionKind.Text, emotion.texts[random.int(emotion.texts.size)])
            in 155..184 -> TapReaction(TapReactionKind.Emoji, emotion.emojis[random.int(emotion.emojis.size)])
            else -> TapReaction(TapReactionKind.Plus, "+1")
        }
    }
}
