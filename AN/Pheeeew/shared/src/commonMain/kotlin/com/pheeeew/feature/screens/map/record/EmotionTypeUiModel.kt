package com.pheeeew.feature.screens.map.record

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_emotion_angry
import pheeeew.shared.generated.resources.group_detail_emotion_annoyed
import pheeeew.shared.generated.resources.group_detail_emotion_blocked
import pheeeew.shared.generated.resources.group_detail_emotion_defeated
import pheeeew.shared.generated.resources.group_detail_emotion_tired
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
    FRUSTRATED(Res.string.group_detail_emotion_blocked, Res.string.record_emotion_phrase_blocked, Res.drawable.ic_emotion_frustrated, Res.string.record_emotion_prompt_blocked),
    IRRITATED(Res.string.group_detail_emotion_annoyed, Res.string.record_emotion_phrase_annoyed, Res.drawable.ic_emotion_irritated, Res.string.record_emotion_prompt_annoyed),
    EXHAUSTED(Res.string.group_detail_emotion_tired, Res.string.record_emotion_phrase_tired, Res.drawable.ic_emotion_exhausted, Res.string.record_emotion_prompt_tired),
    DISCOURAGED(Res.string.group_detail_emotion_defeated, Res.string.record_emotion_phrase_defeated, Res.drawable.ic_emotion_discouraged, Res.string.record_emotion_prompt_defeated),
    ANGRY(Res.string.group_detail_emotion_angry, Res.string.record_emotion_phrase_angry, Res.drawable.ic_emotion_angry, Res.string.record_emotion_prompt_angry),
}
