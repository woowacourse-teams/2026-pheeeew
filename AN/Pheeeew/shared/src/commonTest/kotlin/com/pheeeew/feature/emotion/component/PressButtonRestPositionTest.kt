package com.pheeeew.feature.emotion.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PressButtonRestPositionTest {
    @Test
    fun rapidRetargetsAndCancelledPressReturnToOriginalRestPosition() {
        val motion = TapButtonMotion()
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
