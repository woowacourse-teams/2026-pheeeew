package com.pheeeew.core.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateControllerTest {
    private class Driver : AppUpdateDriver {
        var info = UpdateAvailability(true, true, true)
        var wait: CompletableDeferred<Unit>? = null
        var checks = 0
        val launches = mutableListOf<Boolean>()
        var fail = false
        var launchesSuccessfully = true
        var installations = 0

        override suspend fun check(): UpdateAvailability {
            checks++
            wait?.await()
            if (fail) error("play offline")
            return info
        }

        override fun launch(required: Boolean): Boolean {
            launches += required
            return launchesSuccessfully
        }

        override suspend fun install() {
            installations++
            if (fail) error("install failed")
        }
    }

    @Test
    fun `flexible download complete and installation are distinct from acceptance`() =
        runTest {
            val driver = Driver()
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.request(false)
            runCurrent()
            assertEquals(listOf(false), driver.launches)
            controller.onResult(true, false)
            assertEquals(AppUpdateState.DOWNLOADING, controller.state.value)
            controller.complete()
            runCurrent()
            assertEquals(0, driver.installations)
            controller.onInstallState(AppUpdateState.DOWNLOADED)
            controller.complete()
            controller.complete()
            runCurrent()
            assertEquals(1, driver.installations)
            assertEquals(AppUpdateState.INSTALLING, controller.state.value)
        }

    @Test
    fun `immediate cancel failure and explicit retry obtain fresh info`() =
        runTest {
            val driver = Driver()
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.request(true)
            runCurrent()
            controller.onResult(false, true)
            assertEquals(AppUpdateState.CANCELED, controller.state.value)
            controller.request(true)
            runCurrent()
            controller.onResult(false, false)
            assertEquals(AppUpdateState.FAILED, controller.state.value)
            controller.request(true)
            runCurrent()
            assertEquals(3, driver.checks)
            assertEquals(listOf(true, true, true), driver.launches)
        }

    @Test
    fun `unavailable and forbidden mode never launch and expose store fallback`() =
        runTest {
            for (info in listOf(UpdateAvailability(false, true, true), UpdateAvailability(true, false, false))) {
                val driver = Driver().apply { this.info = info }
                val controller = DefaultAppUpdateController(driver, backgroundScope)
                controller.request(true)
                runCurrent()
                assertEquals(AppUpdateState.UNAVAILABLE, controller.state.value)
                assertTrue(driver.launches.isEmpty())
            }
        }

    @Test
    fun `pending launch and downloads do not start duplicate flows`() =
        runTest {
            val driver = Driver().apply { wait = CompletableDeferred() }
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.request(true)
            controller.request(true)
            runCurrent()
            assertEquals(1, driver.checks)
            driver.wait!!.complete(Unit)
            runCurrent()
            controller.request(true)
            runCurrent()
            assertEquals(1, driver.launches.size)
            controller.onResult(true, false)
            controller.request(true)
            runCurrent()
            assertEquals(1, driver.launches.size)
        }

    @Test
    fun `foreground recovers downloaded and interrupted immediate without auto prompting available updates`() =
        runTest {
            val driver = Driver()
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.resume()
            runCurrent()
            assertEquals(AppUpdateState.IDLE, controller.state.value)
            driver.info = driver.info.copy(immediateInProgress = true)
            controller.resume()
            runCurrent()
            assertEquals(listOf(true), driver.launches)
            driver.info = driver.info.copy(immediateInProgress = false, downloaded = true)
            controller.resume()
            runCurrent()
            assertEquals(AppUpdateState.DOWNLOADED, controller.state.value)
            driver.fail = true
            controller.resume()
            runCurrent()
            assertEquals(AppUpdateState.DOWNLOADED, controller.state.value)
            controller.complete()
            runCurrent()
            assertEquals(AppUpdateState.FAILED, controller.state.value)
        }

    @Test
    fun `a recreated controller recovers a flexible download without starting another flow`() =
        runTest {
            val driver = Driver().apply { info = info.copy(downloading = true) }
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.resume()
            runCurrent()
            assertEquals(AppUpdateState.DOWNLOADING, controller.state.value)
            controller.request(false)
            runCurrent()
            assertTrue(driver.launches.isEmpty())
        }

    @Test
    fun `a timed out availability query provides a retry instead of staying busy`() =
        runTest {
            val driver = Driver().apply { wait = CompletableDeferred() }
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.request(true)
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(AppUpdateState.FAILED, controller.state.value)
            driver.wait = null
            controller.request(true)
            runCurrent()
            assertEquals(AppUpdateState.LAUNCHING, controller.state.value)
        }

    @Test
    fun `query and launch failures expose retry without changing version policy`() =
        runTest {
            val driver = Driver().apply { fail = true }
            val controller = DefaultAppUpdateController(driver, backgroundScope)
            controller.request(true)
            runCurrent()
            assertEquals(AppUpdateState.FAILED, controller.state.value)
            driver.fail = false
            driver.launchesSuccessfully = false
            controller.request(true)
            runCurrent()
            assertEquals(AppUpdateState.FAILED, controller.state.value)
        }
}
