package com.pheeeew.feature.screens.map.detail

internal fun EmotionDetailContentUiModel.Audio.playbackProgress(): Float {
    val duration = durationMillis ?: return 0f
    if (duration <= 0) return 0f
    return (positionMillis.toDouble() / duration).toFloat().coerceIn(0f, 1f)
}

internal fun EmotionDetailContentUiModel.Audio.timelineLabel(): String? {
    val duration = durationMillis ?: return null
    val millis = if (isPlaying) positionMillis.coerceIn(0, duration.coerceAtLeast(0)) else duration
    val seconds = millis.coerceAtLeast(0) / 1000
    return "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}
