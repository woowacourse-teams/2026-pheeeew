package com.pheeeew.feature.emotion.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PressButtonRestPositionTest {
    @Test
    fun buzzerMovesOnlyByCapDepthAndQuickTapStillShowsPress() {
        val motion = TapButtonMotion(buzzer = true)
        motion.press(0.0)
        assertEquals(TapTransform(y = 3.0), motion.sample(55.0))
        motion.release(60.0)
        assertEquals(TapTransform(y = 3.0), motion.sample(65.0))
        assertEquals(TapTransform(), motion.sample(250.0))
    }

    @Test
    fun rapidRetargetsAndCancelledPressReturnToOriginalRestPosition() {
        val motion = TapButtonMotion(buzzer = true)
        repeat(20) { index ->
            val now = index * 30.0
            motion.press(now)
            motion.release(now + 10)
        }
        assertEquals(TapTransform(), motion.sample(2000.0))
        assertFalse(motion.isRunning(2000.0))
        motion.press(2100.0)
        motion.sample(2140.0)
        motion.reset()
        assertEquals(TapTransform(), motion.sample(2140.0))
        assertFalse(motion.pressed)
        assertFalse(motion.isRunning(2140.0))
    }
}
