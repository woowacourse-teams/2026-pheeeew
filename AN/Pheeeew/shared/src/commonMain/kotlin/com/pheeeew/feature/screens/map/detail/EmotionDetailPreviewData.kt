package com.pheeeew.feature.screens.map.detail

import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel

internal object EmotionDetailPreviewData {
    val reactions =
        listOf(
            EmotionReactionUiModel("heart", "❤️", 12, false),
            EmotionReactionUiModel("laugh", "🤣", 8, true),
            EmotionReactionUiModel("cry", "😭", 6, false),
            EmotionReactionUiModel("dizzy", "😵‍💫", 3, false),
            EmotionReactionUiModel("rage", "🤬", 2, false),
            EmotionReactionUiModel("skull", "☠️", 4, false),
        )
    val audio =
        EmotionDetailContentUiModel.Audio(
            playbackUrl = "https://example.com/preview.m4a",
            expiresAt = "2026-09-28T12:00:00Z",
            durationMillis = 18_000,
            positionMillis = 0,
            waveform =
                listOf(
                    .3f,
                    .5f,
                    .7f,
                    .4f,
                    .9f,
                    .6f,
                    .35f,
                    .75f,
                    1f,
                    .55f,
                    .8f,
                    .4f,
                    .65f,
                    .9f,
                    .5f,
                    .3f,
                    .75f,
                    .45f,
                    .8f,
                    .6f,
                    .35f,
                    .55f,
                    .3f,
                    .2f,
                ),
            isPlaying = false,
            isPreparing = false,
            error = null,
        )

    fun model(content: EmotionDetailContentUiModel) =
        EmotionDetailUiModel(
            "우테코 8기 히유",
            "히유",
            null,
            "지친 감자",
            EmotionTypeUiModel.IRRITATED,
            "오늘 오후 2:32",
            content,
            true,
            reactions,
            null,
        )
}
