package com.pheeeew.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppUiSessionTest {
    @Test
    fun `같은 UI 소유자는 모델을 유지하고 UI 종료는 자신의 모델과 대기 작업만 정리한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val activity = AppUiSession()
            val ui = AppUiSession()
            val factory = viewModelFactory { initializer { WaitingModel() } }
            try {
                val activityModel = ViewModelProvider.create(activity, factory)[WaitingModel::class]
                val first = ViewModelProvider.create(ui, factory)[WaitingModel::class]
                assertSame(first, ViewModelProvider.create(ui, factory)[WaitingModel::class])
                runCurrent()
                assertTrue(first.job.isActive)
                ui.close()
                runCurrent()
                assertTrue(first.cleared)
                assertTrue(first.finished)
                assertTrue(activityModel.job.isActive)
                assertFalse(activityModel.cleared)
                val nextUi = AppUiSession()
                try {
                    val next = ViewModelProvider.create(nextUi, factory)[WaitingModel::class]
                    assertNotSame(first, next)
                    assertSame(activityModel, ViewModelProvider.create(activity, factory)[WaitingModel::class])
                    assertEquals(false, next.cleared)
                } finally {
                    nextUi.close()
                }
            } finally {
                ui.close()
                activity.close()
                Dispatchers.resetMain()
            }
        }

    private class WaitingModel : ViewModel() {
        var cleared = false
        var finished = false
        val job =
            viewModelScope.launch {
                try {
                    awaitCancellation()
                } finally {
                    finished = true
                }
            }

        override fun onCleared() {
            cleared = true
        }
    }
}
