package com.pheeeew.feature.screens.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.domain.model.version.AppVersionPolicy
import com.pheeeew.domain.repository.AppVersionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupViewModelTest {
    @Test
    fun `duplicate resume checks share the running request`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            val response = CompletableDeferred<AppVersionPolicy>()
            var calls = 0
            try {
                val vm =
                    owner.create(
                        AppVersionRepository {
                            calls++
                            response.await()
                        },
                    )
                vm.checkVersion()
                vm.checkVersion()
                runCurrent()
                assertEquals(1, calls)
                assertFalse(vm.state.value.checked)
                response.complete(AppVersionPolicy("1.0.0", "1.0.0", "https://example.test/store"))
                runCurrent()
                assertEquals(AppVersionDecision.Current, vm.state.value.decision)
                assertTrue(vm.state.value.checked)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed recheck preserves an already required update`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            var fail = false
            try {
                val vm =
                    owner.create(
                        AppVersionRepository {
                            if (fail) error("Offline")
                            AppVersionPolicy("2.0.0", "2.0.0", "https://example.test/store")
                        },
                    )
                vm.checkVersion()
                runCurrent()
                assertIs<AppVersionDecision.UpdateRequired>(vm.state.value.decision)
                fail = true
                vm.checkVersion()
                runCurrent()
                assertTrue(vm.state.value.checked)
                assertIs<AppVersionDecision.UpdateRequired>(vm.state.value.decision)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `public policy timeout allows startup`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            try {
                val vm = owner.create(AppVersionRepository { awaitCancellation() })
                vm.checkVersion()
                runCurrent()
                advanceTimeBy(10_000L)
                runCurrent()
                assertTrue(vm.state.value.checked)
                assertEquals(null, vm.state.value.decision)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `owner cancellation is not a successful check`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            try {
                val vm = owner.create(AppVersionRepository { awaitCancellation() })
                vm.checkVersion()
                runCurrent()
                owner.viewModelStore.clear()
                runCurrent()
                assertFalse(vm.state.value.checked)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `ready startup keeps two seconds and never reopens after dismissal`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            try {
                val vm = owner.create(currentPolicy())
                vm.startSplash(testScheduler.timeSource)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.READY, true)
                vm.updateMapPreparation(true)
                runCurrent()
                advanceTimeBy(1_999)
                runCurrent()
                assertFalse(vm.state.value.splashDismissed)
                advanceTimeBy(1)
                runCurrent()
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("ready", vm.state.value.splashExitReason)
                vm.updateEntryPreparation(true, AppEntryPhase.LOADING, false)
                vm.updateMapPreparation(false)
                vm.startSplash(testScheduler.timeSource)
                advanceTimeBy(4_000)
                runCurrent()
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("ready", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `maximum deadline closes splash without completing or cancelling version check`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            val response = CompletableDeferred<AppVersionPolicy>()
            var calls = 0
            try {
                val vm =
                    owner.create(
                        AppVersionRepository {
                            calls++
                            response.await()
                        },
                    )
                vm.startSplash(testScheduler.timeSource)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.LOADING, false)
                runCurrent()
                advanceTimeBy(3_999)
                runCurrent()
                assertFalse(vm.state.value.splashDismissed)
                // A new composition obtains the same retained model and cannot reset its deadline.
                assertTrue(vm === owner.create(currentPolicy()))
                vm.startSplash(testScheduler.timeSource)
                advanceTimeBy(1)
                runCurrent()
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("deadline", vm.state.value.splashExitReason)
                assertFalse(vm.state.value.checked)
                assertEquals(1, calls)
                assertTrue(response.isActive)
                response.complete(AppVersionPolicy("2.0.0", "2.0.0", "https://example.test/store"))
                runCurrent()
                assertIs<AppVersionDecision.UpdateRequired>(vm.state.value.decision)
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("deadline", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `map completion after animation immediately dismisses splash`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            try {
                val vm = owner.create(currentPolicy())
                vm.startSplash(testScheduler.timeSource)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.READY, true)
                runCurrent()
                advanceTimeBy(3_000)
                runCurrent()
                assertFalse(vm.state.value.splashDismissed)
                vm.updateMapPreparation(true)
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("ready", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `unresolved map has an absolute four second deadline`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            try {
                val vm = owner.create(currentPolicy())
                vm.startSplash(testScheduler.timeSource)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.READY, true)
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()
                assertEquals("deadline", vm.state.value.splashExitReason)
                vm.updateMapPreparation(true)
                assertEquals("deadline", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `entry failures and nickname input stop waiting for map after minimum time`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (phase in listOf(
                    AppEntryPhase.FAILED,
                    AppEntryPhase.NICKNAME_REQUIRED,
                    AppEntryPhase.REGISTRATION_REQUIRED,
                )) {
                    val owner = Owner()
                    try {
                        val vm = owner.create(currentPolicy())
                        vm.startSplash(testScheduler.timeSource)
                        vm.checkVersion()
                        vm.updateEntryPreparation(true, phase, false)
                        runCurrent()
                        advanceTimeBy(2_000)
                        runCurrent()
                        assertEquals("entry_action", vm.state.value.splashExitReason)
                    } finally {
                        owner.viewModelStore.clear()
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `ready phase with mismatched session cannot dismiss splash early`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            try {
                val vm = owner.create(currentPolicy())
                vm.startSplash(testScheduler.timeSource)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.READY, false)
                vm.updateMapPreparation(true)
                runCurrent()
                advanceTimeBy(2_000)
                runCurrent()
                assertFalse(vm.state.value.splashDismissed)
                vm.updateEntryPreparation(true, AppEntryPhase.READY, true)
                assertEquals("ready", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `background elapsed time enforces deadline on resume without resetting timer`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            val clock = TestTimeSource()
            try {
                val vm = owner.create(currentPolicy())
                vm.startSplash(clock)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.LOADING, false)
                runCurrent()
                clock += 6.seconds
                vm.refreshSplashDeadline()
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("deadline", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `onboarding and required update do not wait for the map`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (required in listOf(false, true)) {
                    val owner = Owner()
                    try {
                        val vm =
                            owner.create(
                                AppVersionRepository {
                                    val minimum = if (required) "2.0.0" else "1.0.0"
                                    AppVersionPolicy(minimum, minimum, "https://example.test/store")
                                },
                            )
                        vm.startSplash(testScheduler.timeSource)
                        vm.checkVersion()
                        vm.updateEntryPreparation(false, AppEntryPhase.LOADING, false)
                        runCurrent()
                        advanceTimeBy(2_000)
                        runCurrent()
                        assertEquals(if (required) "required_update" else "onboarding", vm.state.value.splashExitReason)
                    } finally {
                        owner.viewModelStore.clear()
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `a late ready callback cannot bypass deadline while dispatcher was paused`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            val clock = TestTimeSource()
            try {
                val vm = owner.create(currentPolicy())
                vm.startSplash(clock)
                vm.checkVersion()
                vm.updateEntryPreparation(true, AppEntryPhase.READY, true)
                runCurrent()
                clock += 6.seconds
                vm.updateMapPreparation(true)
                assertTrue(vm.state.value.splashDismissed)
                assertEquals("deadline", vm.state.value.splashExitReason)
            } finally {
                owner.viewModelStore.clear()
                Dispatchers.resetMain()
            }
        }

    private fun currentPolicy() =
        AppVersionRepository {
            AppVersionPolicy("1.0.0", "1.0.0", "https://example.test/store")
        }

    private class Owner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()

        fun create(repository: AppVersionRepository): AppStartupViewModel =
            ViewModelProvider.create(
                this,
                viewModelFactory { initializer { AppStartupViewModel(repository, "1.0.0") } },
            )[
                AppStartupViewModel::class,
            ]
    }
}
