package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModelStore
import com.pheeeew.data.repository.press.PressRepositoryImpl
import com.pheeeew.data.repository.press.PressRetryPolicy
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressTotals
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.model.press.PressSessionNotice
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressSender
import com.pheeeew.domain.repository.press.PressStatisticsDataSource
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressViewModelTest {
    @Test
    fun `accepts taps optimistically and reconciles successful fake send`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender = FakePressSender(statistics, acceptedMarker())
                val repository =
                    PressRepositoryImpl(statistics, sender, backgroundScope, nowMillis = {
                        testScheduler.currentTime
                    }, retryPolicy = PressRetryPolicy(jitter = { 1.0 }))
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                assertEquals(
                    9L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
                assertEquals(
                    82L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertTrue(viewModel.onEmotionTap(EmotionKind.Blocked))
                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                runCurrent()

                assertEquals(1L, viewModel.uiState.value.optimisticPressCounts[EmotionKind.Blocked])
                assertEquals(2L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(2L, viewModel.uiState.value.optimisticAllPressCount)
                val optimisticAllTotal =
                    viewModel.uiState.value.allToday
                        ?.total
                        ?.plus(viewModel.uiState.value.optimisticAllPressCount)
                assertEquals(84L, optimisticAllTotal)
                advanceTimeBy(500)
                runCurrent()

                advanceTimeBy(300)
                runCurrent()

                assertEquals(1, sender.attempts)
                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertEquals(
                    11L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
                assertEquals(
                    84L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertTrue(
                    viewModel.uiState.value.optimisticPressCounts
                        .isEmpty(),
                )
                assertEquals(0L, viewModel.uiState.value.optimisticAllPressCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `uses the accepted write snapshot without another personal aggregate read`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender = FakePressSender(statistics, acceptedMarker())
                val repository =
                    PressRepositoryImpl(statistics, sender, backgroundScope, nowMillis = {
                        testScheduler.currentTime
                    }, retryPolicy = PressRetryPolicy(jitter = { 1.0 }))
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                assertTrue(viewModel.onEmotionTap(EmotionKind.Blocked))
                runCurrent()
                advanceTimeBy(500)
                runCurrent()
                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertEquals(0L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(10L, requireNotNull(viewModel.uiState.value.myToday).total)
                assertEquals(1, statistics.myReads)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `loads a baseline and applies a tap accepted before the first refresh`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender = FakePressSender(statistics, acceptedMarker())
                val repository =
                    PressRepositoryImpl(statistics, sender, backgroundScope, nowMillis = {
                        testScheduler.currentTime
                    }, flushDelayMillis = 0)
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)

                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                runCurrent()

                assertEquals(1, sender.attempts)
                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertEquals(10L, requireNotNull(viewModel.uiState.value.myToday).total)
                assertEquals(0L, viewModel.uiState.value.optimisticMyTotalCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `rolls back only a batch explicitly rejected by sender`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val repository =
                    PressRepositoryImpl(
                        statistics,
                        FakePressSender(statistics, PressSendResult.Rejected),
                        backgroundScope,
                        nowMillis = { testScheduler.currentTime },
                    )
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                assertTrue(viewModel.onEmotionTap(EmotionKind.Tired))
                runCurrent()
                assertEquals(1L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(1L, viewModel.uiState.value.optimisticAllPressCount)
                advanceTimeBy(500)
                runCurrent()

                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertTrue(
                    viewModel.uiState.value.optimisticPressCounts
                        .isEmpty(),
                )
                assertEquals(PressSessionNotice.Rejected, repository.state.value.notice)
                assertEquals(0L, viewModel.uiState.value.optimisticAllPressCount)
                assertEquals(
                    82L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertEquals(
                    9L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `keeps not sent input and honors backoff before retry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender = FakePressSender(statistics, PressSendResult.NotSent, acceptedMarker())
                val repository =
                    PressRepositoryImpl(statistics, sender, backgroundScope, nowMillis = {
                        testScheduler.currentTime
                    }, retryPolicy = PressRetryPolicy(jitter = { 1.0 }))
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                assertTrue(viewModel.onEmotionTap(EmotionKind.Annoyed))
                runCurrent()
                advanceTimeBy(500)
                runCurrent()
                assertEquals(1, sender.attempts)
                assertEquals(1L, viewModel.uiState.value.pendingPressCount)
                assertEquals(1L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(PressSessionNotice.RetryRequired, repository.state.value.notice)

                viewModel.retryUnsent()
                advanceTimeBy(1_000)
                runCurrent()
                advanceTimeBy(300)
                runCurrent()
                assertEquals(2, sender.attempts)
                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertEquals(
                    10L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
                assertEquals(0L, viewModel.uiState.value.optimisticMyTotalCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `reconciles an applied outcome unknown write without replay`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender =
                    FakePressSender(
                        statistics,
                        PressSendResult.OutcomeUnknown,
                        applyBeforeUnknown = true,
                    )
                val repository =
                    PressRepositoryImpl(statistics, sender, backgroundScope, nowMillis = {
                        testScheduler.currentTime
                    }, flushDelayMillis = 0)
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                runCurrent()
                assertEquals(1, sender.attempts)
                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertEquals(0L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(
                    10L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
                assertFalse(repository.state.value.isOutcomeUnknown)

                viewModel.retryUnsent()
                runCurrent()
                assertEquals(1, sender.attempts)
                assertEquals(0L, viewModel.uiState.value.pendingPressCount)
                assertTrue(
                    viewModel.uiState.value.optimisticPressCounts
                        .isEmpty(),
                )
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `retains last successful aggregates when a refresh fails`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val repository =
                    PressRepositoryImpl(
                        statistics,
                        FakePressSender(statistics, PressSendResult.ContractPending),
                        backgroundScope,
                        nowMillis = { testScheduler.currentTime },
                        retryPolicy = PressRetryPolicy(jitter = { 1.0 }),
                    )
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()
                statistics.failReads = true

                repository.refreshToday(force = true)
                runCurrent()

                assertEquals(
                    9L,
                    viewModel.uiState.value.myToday
                        ?.total,
                )
                assertEquals(
                    82L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertTrue(repository.state.value.hasMyError)
                assertTrue(repository.state.value.hasAllError)

                statistics.failReads = false
                advanceTimeBy(1_000)
                runCurrent()
                assertFalse(repository.state.value.hasMyError)
                assertFalse(repository.state.value.hasAllError)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `keeps input when sender pauses for a contract issue`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender = FakePressSender(statistics, PressSendResult.ContractPending)
                val repository =
                    PressRepositoryImpl(
                        statistics,
                        sender,
                        backgroundScope,
                        nowMillis = { testScheduler.currentTime },
                        flushDelayMillis = 0,
                    )
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                assertTrue(viewModel.onEmotionTap(EmotionKind.Defeated))
                runCurrent()

                assertEquals(1L, viewModel.uiState.value.pendingPressCount)
                assertEquals(1L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(PressSessionNotice.WaitingForApiContract, repository.state.value.notice)
                assertEquals(1, sender.attempts)

                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                runCurrent()

                assertEquals(2L, viewModel.uiState.value.pendingPressCount)
                assertEquals(2L, viewModel.uiState.value.optimisticMyTotalCount)
                assertEquals(1, sender.attempts)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `keeps accepted local press in all total until total endpoint confirms it`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val repository =
                    PressRepositoryImpl(
                        statistics,
                        FakePressSender(statistics, acceptedMarker()),
                        backgroundScope,
                        flushDelayMillis = 0,
                        nowMillis = { testScheduler.currentTime },
                    )
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()
                statistics.failAllReads = true

                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                runCurrent()

                val optimisticAllTotal =
                    viewModel.uiState.value.allToday
                        ?.total
                        ?.plus(viewModel.uiState.value.optimisticAllPressCount)
                assertEquals(83L, optimisticAllTotal)
                assertEquals(1L, viewModel.uiState.value.optimisticAllPressCount)
                assertEquals(
                    82L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )

                statistics.failAllReads = false
                repository.refreshToday(force = true)
                runCurrent()

                assertEquals(
                    83L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertEquals(0L, viewModel.uiState.value.optimisticAllPressCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `total refresh absorbs acknowledged taps but preserves taps made during the request`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val statistics = FakePressStatistics()
                val sender = FakePressSender(statistics, acceptedMarker(), PressSendResult.ContractPending)
                val repository =
                    PressRepositoryImpl(
                        statistics,
                        sender,
                        backgroundScope,
                        flushDelayMillis = 0,
                        nowMillis = { testScheduler.currentTime },
                    )
                val viewModel = PressViewModel(repository)
                store.put("press", viewModel)
                viewModel.onScreenResumed()
                runCurrent()

                val totalReadGate = CompletableDeferred<Unit>()
                statistics.allReadGate = totalReadGate
                assertTrue(viewModel.onEmotionTap(EmotionKind.Angry))
                runCurrent()
                assertEquals(1L, viewModel.uiState.value.optimisticAllPressCount)

                assertTrue(viewModel.onEmotionTap(EmotionKind.Tired))
                runCurrent()
                assertEquals(2L, viewModel.uiState.value.optimisticAllPressCount)

                statistics.allReadGate = null
                totalReadGate.complete(Unit)
                runCurrent()

                assertEquals(
                    83L,
                    viewModel.uiState.value.allToday
                        ?.total,
                )
                assertEquals(1L, viewModel.uiState.value.optimisticAllPressCount)
                val displayedAllTotal =
                    viewModel.uiState.value.allToday
                        ?.total
                        ?.plus(viewModel.uiState.value.optimisticAllPressCount)
                assertEquals(84L, displayedAllTotal)
                assertEquals(1L, viewModel.uiState.value.pendingPressCount)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `screen view model recreation reads the same session repository state`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val appSessionScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            try {
                val statistics = FakePressStatistics()
                val appScopedRepository =
                    PressRepositoryImpl(
                        statistics,
                        FakePressSender(statistics, PressSendResult.ContractPending),
                        appSessionScope,
                        flushDelayMillis = 10_000,
                    )

                val firstScreenStore = ViewModelStore()
                val firstScreen = PressViewModel(appScopedRepository)
                firstScreenStore.put("press", firstScreen)
                repeat(10_000) { assertTrue(firstScreen.onEmotionTap(EmotionKind.Blocked)) }
                runCurrent()
                assertEquals(10_000L, firstScreen.uiState.value.optimisticMyTotalCount)
                assertEquals(10_000L, firstScreen.uiState.value.optimisticAllPressCount)
                firstScreenStore.clear()

                val recreatedScreenStore = ViewModelStore()
                val recreatedScreen = PressViewModel(appScopedRepository)
                recreatedScreenStore.put("press", recreatedScreen)
                assertEquals(appScopedRepository.state.value.myToday, recreatedScreen.uiState.value.myToday)
                assertEquals(10_000L, recreatedScreen.uiState.value.optimisticMyTotalCount)
                runCurrent()

                assertEquals(10_000L, recreatedScreen.uiState.value.pendingPressCount)
                assertEquals(10_000L, recreatedScreen.uiState.value.optimisticMyTotalCount)
                assertEquals(10_000L, recreatedScreen.uiState.value.optimisticAllPressCount)
                assertEquals(PressSessionNotice.WaitingForApiContract, appScopedRepository.state.value.notice)
                recreatedScreenStore.clear()
            } finally {
                appSessionScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    private class FakePressStatistics : PressStatisticsDataSource {
        private val counts =
            EmotionState.entries.associateWith { 0L }.toMutableMap().apply {
                this[EmotionState.FRUSTRATED] = 2L
                this[EmotionState.IRRITATED] = 3L
                this[EmotionState.EXHAUSTED] = 4L
            }
        var allTotal = 82L
        var failReads = false
        var failAllReads = false
        var allReadGate: CompletableDeferred<Unit>? = null
        var myReads = 0
        var allReads = 0

        override suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot> {
            myReads++
            if (failReads) return PressReadResult.Unavailable
            return PressReadResult.Loaded(MyDailyPressSnapshot("2026-10-08", counts.toMap(), counts.values.sum()))
        }

        override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> {
            allReads++
            allReadGate?.await()
            if (failReads || failAllReads) return PressReadResult.Unavailable
            return PressReadResult.Loaded(AllDailyPressSnapshot("2026-10-08", allTotal))
        }

        fun apply(batch: com.pheeeew.domain.model.press.PressBatch) {
            batch.counts.forEach { (emotion, count) -> counts[emotion] = counts.getValue(emotion) + count }
            allTotal += batch.totalCount
        }

        fun todayTotals() = MyDailyPressTotals(counts.toMap(), counts.values.sum())
    }

    private class FakePressSender(
        private val statistics: FakePressStatistics,
        vararg results: PressSendResult,
        private val applyBeforeUnknown: Boolean = false,
    ) : PressSender {
        private val scripted = ArrayDeque(results.toList())
        var attempts = 0

        override suspend fun send(batch: com.pheeeew.domain.model.press.PressBatch): PressSendResult {
            attempts++
            val result = if (scripted.isEmpty()) acceptedMarker() else scripted.removeFirst()
            if (result is PressSendResult.Accepted ||
                (result == PressSendResult.OutcomeUnknown && applyBeforeUnknown)
            ) {
                statistics.apply(batch)
            }
            return if (result is PressSendResult.Accepted) {
                PressSendResult.Accepted(statistics.todayTotals())
            } else {
                result
            }
        }
    }

    private companion object {
        fun acceptedMarker() =
            PressSendResult.Accepted(
                MyDailyPressTotals(
                    counts = EmotionState.entries.associateWith { 0L },
                    total = 0L,
                ),
            )
    }
}
