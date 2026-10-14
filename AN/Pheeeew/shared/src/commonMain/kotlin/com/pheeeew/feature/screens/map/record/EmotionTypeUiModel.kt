package com.pheeeew.feature.screens.map.record

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_name_angry
import pheeeew.shared.generated.resources.emotion_name_annoyed
import pheeeew.shared.generated.resources.emotion_name_blocked
import pheeeew.shared.generated.resources.emotion_name_defeated
import pheeeew.shared.generated.resources.emotion_name_tired
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated
import pheeeew.shared.generated.resources.record_emotion_phrase_angry
import pheeeew.shared.generated.resources.record_emotion_phrase_annoyed
import pheeeew.shared.generated.resources.record_emotion_phrase_blocked
import pheeeew.shared.generated.resources.record_emotion_phrase_defeated
import pheeeew.shared.generated.resources.record_emotion_phrase_tired
import pheeeew.shared.generated.resources.record_emotion_prompt_angry
import pheeeew.shared.generated.resources.record_emotion_prompt_annoyed
import pheeeew.shared.generated.resources.record_emotion_prompt_blocked
import pheeeew.shared.generated.resources.record_emotion_prompt_defeated
import pheeeew.shared.generated.resources.record_emotion_prompt_tired

enum class EmotionTypeUiModel(
    val label: StringResource,
    val recordPhrase: StringResource,
    val icon: DrawableResource,
    val recordPrompt: StringResource,
) {
    FRUSTRATED(
        Res.string.emotion_name_blocked,
        Res.string.record_emotion_phrase_blocked,
        Res.drawable.ic_emotion_frustrated,
        Res.string.record_emotion_prompt_blocked,
    ),
    IRRITATED(
        Res.string.emotion_name_annoyed,
        Res.string.record_emotion_phrase_annoyed,
        Res.drawable.ic_emotion_irritated,
        Res.string.record_emotion_prompt_annoyed,
    ),
    EXHAUSTED(
        Res.string.emotion_name_tired,
        Res.string.record_emotion_phrase_tired,
        Res.drawable.ic_emotion_exhausted,
        Res.string.record_emotion_prompt_tired,
    ),
    DISCOURAGED(
        Res.string.emotion_name_defeated,
        Res.string.record_emotion_phrase_defeated,
        Res.drawable.ic_emotion_discouraged,
        Res.string.record_emotion_prompt_defeated,
    ),
    ANGRY(
        Res.string.emotion_name_angry,
        Res.string.record_emotion_phrase_angry,
        Res.drawable.ic_emotion_angry,
        Res.string.record_emotion_prompt_angry,
    ),
}
