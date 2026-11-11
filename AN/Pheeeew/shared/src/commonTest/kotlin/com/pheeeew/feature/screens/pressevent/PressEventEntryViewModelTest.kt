package com.pheeeew.feature.screens.pressevent

import androidx.lifecycle.ViewModelStore
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.data.config.PressEventConfigSource
import com.pheeeew.data.repository.PressEventConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PressEventEntryViewModelTest {
    @Test
    fun `진입 후 OFF를 확인하면 종료 상태를 표시한다`() = verify(false, true, PressEventEntryStatus.Disabled)

    @Test
    fun `설정 확인 실패와 오프라인이면 네트워크 안내 상태를 표시한다`() = verify(null, false, PressEventEntryStatus.Offline)

    @Test
    fun `연결 중 설정 확인 실패이면 일반 오류 상태를 표시한다`() = verify(null, true, PressEventEntryStatus.Failed)

    @Test
    fun `진입 확인을 통과하면 이후 OFF 갱신으로 이미 진입한 상태를 취소하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val source = FakeSource()
                val repository = PressEventConfigRepository("dev", true)
                repository.onForeground(source)
                val viewModel = PressEventEntryViewModel(repository, Connected(true))
                store.put("entry", viewModel)
                runCurrent()
                source.finish(true)
                runCurrent()
                assertEquals(PressEventEntryStatus.Available, viewModel.status.value)
                source.active = false
                repository.onForeground(source)
                runCurrent()
                assertEquals(PressEventEntryStatus.Available, viewModel.status.value)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `화면을 나간 뒤 늦게 받은 결과는 화면 상태에 반영하지 않는다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val source = FakeSource()
                val repository = PressEventConfigRepository("dev", true)
                repository.onForeground(source)
                val viewModel = PressEventEntryViewModel(repository, Connected(true))
                store.put("entry", viewModel)
                runCurrent()
                store.clear()
                source.finish(false)
                runCurrent()
                assertEquals(PressEventEntryStatus.Loading, viewModel.status.value)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    private fun verify(
        enabled: Boolean?,
        connected: Boolean,
        expected: PressEventEntryStatus,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val source = FakeSource()
            val repository = PressEventConfigRepository("dev", true)
            repository.onForeground(source)
            val viewModel = PressEventEntryViewModel(repository, Connected(connected))
            store.put("entry", viewModel)
            runCurrent()
            assertEquals(PressEventEntryStatus.Loading, viewModel.status.value)
            source.finish(enabled)
            runCurrent()
            assertEquals(expected, viewModel.status.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class Connected(
        connected: Boolean,
    ) : ConnectivityObserver {
        override val isConnected: Flow<Boolean> = flowOf(connected)
    }

    private class FakeSource : PressEventConfigSource {
        var active = true
        private var completion: ((Boolean) -> Unit)? = null

        override fun initialize(
            parameterKey: String,
            minimumFetchIntervalSeconds: Long,
            completion: (Boolean) -> Unit,
        ) = completion(true)

        override fun activatedBoolean(parameterKey: String) = active

        override fun fetchAndActivate(
            forceRefresh: Boolean,
            completion: (Boolean) -> Unit,
        ) {
            if (forceRefresh) this.completion = completion else completion(true)
        }

        fun finish(enabled: Boolean?) {
            if (enabled != null) active = enabled
            checkNotNull(completion)(enabled != null)
            completion = null
        }
    }
}
