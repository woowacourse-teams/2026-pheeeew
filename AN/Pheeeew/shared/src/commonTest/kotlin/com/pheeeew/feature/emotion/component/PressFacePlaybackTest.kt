package com.pheeeew.feature.emotion.component

import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressFacePlaybackTest {
    @Test
    fun returningFromBackgroundDoesNotReplayConsumedBurst() {
        val playback = PressFacePlayback()
        val burst = EmotionFaceVisual(EmotionFaceStage.Burst, 1)
        assertFalse(playback.shouldPlay(burst, enabled = false))
        assertFalse(playback.shouldPlay(burst, enabled = true))
        assertTrue(playback.shouldPlay(burst.copy(playbackId = 2), enabled = true))
    }

    @Test
    fun repeatedRecompositionsDoNotRestartButExplicitReplayDoes() {
        val playback = PressFacePlayback()
        val burst = EmotionFaceVisual(EmotionFaceStage.Burst, 1)
        assertTrue(playback.shouldPlay(burst, enabled = true))
        assertFalse(playback.shouldPlay(burst, enabled = true))
        assertFalse(playback.shouldPlay(burst, enabled = true))
        assertTrue(playback.shouldPlay(burst.copy(playbackId = 2), enabled = true))
    }

    @Test
    fun resetAndReducedMotionConsumeStateWithoutGeneratingExtraPlayback() {
        val playback = PressFacePlayback()
        assertFalse(playback.shouldPlay(EmotionFaceVisual(EmotionFaceStage.Intact), enabled = true))
        val burst = EmotionFaceVisual(EmotionFaceStage.Burst, 5)
        assertFalse(playback.shouldPlay(burst, enabled = false))
        assertFalse(playback.shouldPlay(burst, enabled = true))
        assertFalse(playback.shouldPlay(EmotionFaceVisual(EmotionFaceStage.Cracked, 6), enabled = true))
    }
}
