package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.tap_reaction_angry_1
import pheeeew.shared.generated.resources.tap_reaction_angry_2
import pheeeew.shared.generated.resources.tap_reaction_angry_3
import pheeeew.shared.generated.resources.tap_reaction_angry_4
import pheeeew.shared.generated.resources.tap_reaction_annoyed_1
import pheeeew.shared.generated.resources.tap_reaction_annoyed_2
import pheeeew.shared.generated.resources.tap_reaction_annoyed_3
import pheeeew.shared.generated.resources.tap_reaction_annoyed_4
import pheeeew.shared.generated.resources.tap_reaction_blocked_1
import pheeeew.shared.generated.resources.tap_reaction_blocked_2
import pheeeew.shared.generated.resources.tap_reaction_blocked_3
import pheeeew.shared.generated.resources.tap_reaction_blocked_4
import pheeeew.shared.generated.resources.tap_reaction_defeated_1
import pheeeew.shared.generated.resources.tap_reaction_defeated_2
import pheeeew.shared.generated.resources.tap_reaction_defeated_3
import pheeeew.shared.generated.resources.tap_reaction_defeated_4
import pheeeew.shared.generated.resources.tap_reaction_tired_1
import pheeeew.shared.generated.resources.tap_reaction_tired_2
import pheeeew.shared.generated.resources.tap_reaction_tired_3
import pheeeew.shared.generated.resources.tap_reaction_tired_4

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
    val color: Color,
    val face: DrawableResource,
    val texts: List<StringResource>,
    val emojis: List<String>,
)

internal object TapCatalog {
    @Composable
    fun localizedTexts(): Map<StringResource, String> =
        mapOf(
            Res.string.tap_reaction_blocked_1 to stringResource(Res.string.tap_reaction_blocked_1),
            Res.string.tap_reaction_blocked_2 to stringResource(Res.string.tap_reaction_blocked_2),
            Res.string.tap_reaction_blocked_3 to stringResource(Res.string.tap_reaction_blocked_3),
            Res.string.tap_reaction_blocked_4 to stringResource(Res.string.tap_reaction_blocked_4),
            Res.string.tap_reaction_annoyed_1 to stringResource(Res.string.tap_reaction_annoyed_1),
            Res.string.tap_reaction_annoyed_2 to stringResource(Res.string.tap_reaction_annoyed_2),
            Res.string.tap_reaction_annoyed_3 to stringResource(Res.string.tap_reaction_annoyed_3),
            Res.string.tap_reaction_annoyed_4 to stringResource(Res.string.tap_reaction_annoyed_4),
            Res.string.tap_reaction_tired_1 to stringResource(Res.string.tap_reaction_tired_1),
            Res.string.tap_reaction_tired_2 to stringResource(Res.string.tap_reaction_tired_2),
            Res.string.tap_reaction_tired_3 to stringResource(Res.string.tap_reaction_tired_3),
            Res.string.tap_reaction_tired_4 to stringResource(Res.string.tap_reaction_tired_4),
            Res.string.tap_reaction_defeated_1 to stringResource(Res.string.tap_reaction_defeated_1),
            Res.string.tap_reaction_defeated_2 to stringResource(Res.string.tap_reaction_defeated_2),
            Res.string.tap_reaction_defeated_3 to stringResource(Res.string.tap_reaction_defeated_3),
            Res.string.tap_reaction_defeated_4 to stringResource(Res.string.tap_reaction_defeated_4),
            Res.string.tap_reaction_angry_1 to stringResource(Res.string.tap_reaction_angry_1),
            Res.string.tap_reaction_angry_2 to stringResource(Res.string.tap_reaction_angry_2),
            Res.string.tap_reaction_angry_3 to stringResource(Res.string.tap_reaction_angry_3),
            Res.string.tap_reaction_angry_4 to stringResource(Res.string.tap_reaction_angry_4),
        )

    fun emotion(kind: EmotionKind): TapEmotion =
        when (kind) {
            EmotionKind.Blocked -> {
                TapEmotion(
                    color = Color(0xFFF8D3C0),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts =
                        listOf(
                            Res.string.tap_reaction_blocked_1,
                            Res.string.tap_reaction_blocked_2,
                            Res.string.tap_reaction_blocked_3,
                            Res.string.tap_reaction_blocked_4,
                        ),
                    emojis = listOf("😮‍💨", "😤"),
                )
            }

            EmotionKind.Annoyed -> {
                TapEmotion(
                    color = Color(0xFFF3C7D6),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts =
                        listOf(
                            Res.string.tap_reaction_annoyed_1,
                            Res.string.tap_reaction_annoyed_2,
                            Res.string.tap_reaction_annoyed_3,
                            Res.string.tap_reaction_annoyed_4,
                        ),
                    emojis = listOf("💢", "🙄", "😑"),
                )
            }

            EmotionKind.Tired -> {
                TapEmotion(
                    color = Color(0xFFE1D9F0),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts =
                        listOf(
                            Res.string.tap_reaction_tired_1,
                            Res.string.tap_reaction_tired_2,
                            Res.string.tap_reaction_tired_3,
                            Res.string.tap_reaction_tired_4,
                        ),
                    emojis = listOf("🫠", "🥱", "🪫"),
                )
            }

            EmotionKind.Defeated -> {
                TapEmotion(
                    color = Color(0xFFCCE5F2),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts =
                        listOf(
                            Res.string.tap_reaction_defeated_1,
                            Res.string.tap_reaction_defeated_2,
                            Res.string.tap_reaction_defeated_3,
                            Res.string.tap_reaction_defeated_4,
                        ),
                    emojis = listOf("😭", "🥲", "💧"),
                )
            }

            EmotionKind.Angry -> {
                TapEmotion(
                    color = Color(0xFFF5BEB3),
                    face = EmotionFeedbackCatalog.face(kind),
                    texts =
                        listOf(
                            Res.string.tap_reaction_angry_1,
                            Res.string.tap_reaction_angry_2,
                            Res.string.tap_reaction_angry_3,
                            Res.string.tap_reaction_angry_4,
                        ),
                    emojis = listOf("😡", "🤬", "🔥"),
                )
            }
        }

    fun pick(
        kind: EmotionKind,
        random: TapRandom,
        localizedTexts: Map<StringResource, String>,
        plusOne: String,
    ): TapReaction {
        val emotion = emotion(kind)
        return when (random.int(200)) {
            in 0..79 -> {
                TapReaction(TapReactionKind.Face, kind.name.lowercase())
            }

            in 80..154 -> {
                TapReaction(
                    TapReactionKind.Text,
                    localizedTexts.getValue(emotion.texts[random.int(emotion.texts.size)]),
                )
            }

            in 155..184 -> {
                TapReaction(TapReactionKind.Emoji, emotion.emojis[random.int(emotion.emojis.size)])
            }

            else -> {
                TapReaction(TapReactionKind.Plus, plusOne)
            }
        }
    }
}
