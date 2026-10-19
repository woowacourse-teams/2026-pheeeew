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
