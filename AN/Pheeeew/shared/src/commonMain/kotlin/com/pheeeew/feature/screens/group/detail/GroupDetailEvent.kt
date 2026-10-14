package com.pheeeew.feature.screens.group.detail

sealed interface GroupDetailEvent {
    data class PlayMoodAudio(
        val emotionId: Long,
        val url: String,
    ) : GroupDetailEvent

    data object StopMoodAudio : GroupDetailEvent
}
