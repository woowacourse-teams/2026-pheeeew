package com.pheeeew.feature.screens.settings

import com.pheeeew.core.update.AppUpdateDriver
import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.core.update.DefaultAppUpdateController
import com.pheeeew.core.update.UpdateAvailability
import com.pheeeew.domain.model.version.AppVersionDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OptionalAppUpdatePromptTest {
    private val suggestion = AppVersionDecision.UpdateSuggested("https://example.test/app", "2.6.0")

    @Test
    fun `deferred suggestion shows installation progress and failure until user dismisses the attempt`() =
        runTest {
            val installResult = CompletableDeferred<Unit>()
            val driver =
                object : AppUpdateDriver {
                    override suspend fun check() = UpdateAvailability(true, true, true, downloaded = true)

                    override fun launch(required: Boolean) = false

                    override suspend fun install() {
                        installResult.await()
                    }
                }
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            var attempted = false
            var deferred = false

            fun visible() =
                shouldShowOptionalAppUpdatePrompt(suggestion, true, controller.state.value, deferred, attempted)

            assertFalse(visible())
            controller.resume()
            runCurrent()
            assertTrue(visible())

            attempted = true
            controller.complete()
            runCurrent()
            assertEquals(AppUpdateState.INSTALLING, controller.state.value)
            assertTrue(visible())

            installResult.completeExceptionally(IllegalStateException("installation failed"))
            runCurrent()
            assertEquals(AppUpdateState.FAILED, controller.state.value)
            assertTrue(visible())

            attempted = false
            deferred = true
            assertFalse(visible())
            deferred = false // A later foreground event must not revive this failed attempt.
            assertFalse(visible())
        }

    @Test
    fun `an unrelated failure cannot reopen a dismissed recommendation`() {
        assertFalse(shouldShowOptionalAppUpdatePrompt(suggestion, true, AppUpdateState.FAILED, false, false))
        assertFalse(shouldShowOptionalAppUpdatePrompt(suggestion, true, AppUpdateState.DOWNLOADED, true, false))
    }

    @Test
    fun `installation success stops displaying a previously dismissed recommendation`() {
        assertFalse(shouldShowOptionalAppUpdatePrompt(suggestion, true, AppUpdateState.IDLE, false, true))
        assertTrue(shouldShowOptionalAppUpdatePrompt(suggestion, false, AppUpdateState.IDLE, false, false))
    }
}
