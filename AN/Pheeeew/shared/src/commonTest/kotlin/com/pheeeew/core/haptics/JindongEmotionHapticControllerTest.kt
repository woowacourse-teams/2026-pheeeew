package com.pheeeew.core.haptics

import io.github.compose.jindong.core.executor.HapticExecutor
import io.github.compose.jindong.core.executor.HapticHandle
import io.github.compose.jindong.core.model.HapticPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JindongEmotionHapticControllerTest {
    @Test
    fun rapidTapsReplacePlaybackWithoutKeepingAQueue() {
        val controller = JindongEmotionHapticController()
        val executor = FakeExecutor()
        val owner = Any()
        controller.activate(owner) { executor }
        repeat(100) { controller.play(owner) }
        assertEquals(100, executor.handles.size)
        assertEquals(99, executor.cancellations)
        controller.deactivate(owner)
        controller.play(owner)
        assertEquals(100, executor.handles.size)
        assertEquals(100, executor.cancellations)
        assertEquals(1, executor.releases)
    }

    @Test
    fun overlappingOwnersShareExecutorUntilLastOwnerLeaves() {
        val controller = JindongEmotionHapticController()
        val executor = FakeExecutor()
        val first = Any()
        val second = Any()
        var creations = 0
        val create = {
            creations++
            executor
        }
        controller.activate(first, create)
        controller.activate(first, create)
        controller.activate(second, create)
        controller.play(first)
        controller.deactivate(first)
        controller.play(first)
        controller.play(second)
        assertEquals(1, creations)
        assertEquals(2, executor.handles.size)
        assertEquals(0, executor.releases)
        controller.deactivate(second)
        controller.deactivate(second)
        assertEquals(1, executor.releases)
    }

    @Test
    fun resumeCreatesFreshExecutorAndNeverReplaysOldInput() {
        val controller = JindongEmotionHapticController()
        val old = FakeExecutor()
        val fresh = FakeExecutor()
        val owner = Any()
        controller.activate(owner) { old }
        controller.play(owner)
        controller.deactivate(owner)
        controller.activate(owner) { fresh }
        assertEquals(0, fresh.handles.size)
        controller.play(owner)
        assertEquals(1, fresh.handles.size)
        assertEquals(1, old.handles.size)
    }

    @Test
    fun unsupportedDeviceDoesNotAttemptPlayback() {
        val controller = JindongEmotionHapticController()
        val executor = FakeExecutor(supported = false)
        val owner = Any()
        controller.activate(owner) { executor }
        controller.play(owner)
        controller.deactivate(owner)
        assertEquals(0, executor.handles.size)
        assertEquals(1, executor.releases)
    }

    @Test
    fun cancellationFailureStillReleasesResourcesAndAllowsResume() {
        val controller = JindongEmotionHapticController()
        val executor = FakeExecutor(failCancellation = true)
        val owner = Any()
        controller.activate(owner) { executor }
        controller.play(owner)
        controller.deactivate(owner)
        assertEquals(1, executor.releases)
        val fresh = FakeExecutor()
        controller.activate(owner) { fresh }
        controller.play(owner)
        assertEquals(1, fresh.handles.size)
    }

    private class FakeExecutor(
        private val supported: Boolean = true,
        private val failCancellation: Boolean = false,
    ) : HapticExecutor {
        val handles = mutableListOf<HapticHandle>()
        var cancellations = 0
        var releases = 0

        override val isSupported: Boolean get() = supported

        override suspend fun execute(pattern: HapticPattern) = error("Use cancellable handles")

        override fun executeAsync(pattern: HapticPattern): HapticHandle {
            assertEquals(JindongEmotionHapticController.TAP_PATTERN, pattern)
            return object : HapticHandle {
                override var isActive = true

                override fun cancel() {
                    if (!isActive) return
                    isActive = false
                    cancellations++
                    check(!failCancellation)
                    assertFalse(isActive)
                }
            }.also { handles.add(it) }
        }

        override fun release() {
            releases++
        }
    }
}
