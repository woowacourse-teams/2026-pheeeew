package com.pheeeew.feature.screens.pressevent

import androidx.lifecycle.ViewModelStore
import com.pheeeew.domain.model.pressevent.PressEventAttempt
import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.domain.model.pressevent.PressEventMyRank
import com.pheeeew.domain.model.pressevent.PressEventRankingEntry
import com.pheeeew.domain.model.pressevent.PressEventRankingPage
import com.pheeeew.domain.repository.PressEventFailure
import com.pheeeew.domain.repository.PressEventRepository
import com.pheeeew.domain.repository.PressEventResult
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.PressFaceDemoProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressEventViewModelTest {
    @Test
    fun previousRankingStaysFixedWhileCurrentRankingRefreshes() =
        runTest {
            fixture { vm, repo ->
                val previous = PressEventMyRank(100, 5)
                repo.pages[null] = PressEventResult.Success(page().copy(me = previous))
                vm.onScreenResumed()
                runCurrent()
                vm.start()
                assertEquals(previous, vm.uiState.value.previousResult)
                assertTrue(vm.uiState.value.previousResultKnown)
                repo.pages[null] = PressEventResult.Success(page().copy(me = PressEventMyRank(200, 2)))
                vm.refreshRankings()
                runCurrent()
                assertEquals(previous, vm.uiState.value.previousResult)
                assertEquals(PressEventMyRank(200, 2), vm.uiState.value.me)
            }
        }

    @Test
    fun freezesSubmissionAndRetriesSameIdAndCountsBeforeAllowingNewAttempt() =
        runTest {
            fixture { vm, repo ->
                repo.submitResult = PressEventResult.Failure(PressEventFailure.Retryable)
                vm.start()
                advanceTimeBy(3_000)
                runCurrent()
                repeat(3) { assertTrue(vm.press(EmotionKind.Angry)) }
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(EventSubmissionStatus.Failed, vm.uiState.value.submission)
                val first = repo.attempts.single()
                assertEquals(5, first.counts.size)
                assertEquals(3L, first.total)
                vm.start()
                assertEquals(PressEventPhase.Finished, vm.uiState.value.game.phase)
                vm.retrySubmission()
                runCurrent()
                assertSame(first, repo.attempts.last())
                repo.submitResult = null
                vm.retrySubmission()
                vm.retrySubmission()
                runCurrent()
                assertEquals(3, repo.attempts.size)
                assertEquals(
                    7L,
                    vm.uiState.value.result
                        ?.rank,
                )
                assertTrue(vm.uiState.value.canStart)
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                assertNotEquals(first.requestId, repo.attempts.last().requestId)
            }
        }

    @Test
    fun duplicateStartsAndHiddenAttemptsDoNotSubmitAndFastTicksDoNotEmitScreenState() =
        runTest {
            fixture { vm, repo ->
                val emissions = mutableListOf<PressEventViewState>()
                backgroundScope.launch(
                    UnconfinedTestDispatcher(testScheduler),
                ) { vm.uiState.collect { emissions += it } }
                vm.start()
                advanceTimeBy(2_000)
                vm.start()
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(PressEventPhase.Playing, vm.uiState.value.game.phase)
                emissions.clear()
                advanceTimeBy(100)
                runCurrent()
                assertEquals(19_900L, vm.remainingMillis.value)
                assertTrue(emissions.isEmpty())
                vm.onScreenHidden()
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(PressEventPhase.Ready, vm.uiState.value.game.phase)
                assertTrue(repo.attempts.isEmpty())
            }
        }

    @Test
    fun paginatesDeduplicatesAndRetriesFailedCursorWithoutDroppingExistingRows() =
        runTest {
            fixture { vm, repo ->
                repo.pages[null] = PressEventResult.Success(page(listOf(entry("a", 1)), "next"))
                repo.pages["next"] = PressEventResult.Failure(PressEventFailure.Retryable)
                vm.onScreenResumed()
                runCurrent()
                vm.loadMore()
                vm.loadMore()
                runCurrent()
                assertTrue(vm.uiState.value.nextPageError)
                assertEquals(
                    listOf("a"),
                    vm.uiState.value.people
                        .map { it.publicId },
                )
                repo.pages["next"] = PressEventResult.Success(page(listOf(entry("a", 1), entry("b", 2))))
                vm.loadMore()
                runCurrent()
                assertEquals(
                    listOf("a", "b"),
                    vm.uiState.value.people
                        .map { it.publicId },
                )
                assertEquals(null, vm.uiState.value.nextCursor)
                assertEquals(listOf(null, "next", "next"), repo.cursors)
            }
        }

    @Test
    fun pendingSubmissionWaitsForNetworkFailureBeforeRetryingSameAttempt() =
        runTest {
            fixture { vm, repo ->
                val pending = CompletableDeferred<PressEventResult<PressEventAttemptResult>>()
                repo.delayedSubmit = pending
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                assertEquals(EventSubmissionStatus.Submitting, vm.uiState.value.submission)
                advanceTimeBy(10_000)
                runCurrent()
                try {
                    assertEquals(EventSubmissionStatus.Submitting, vm.uiState.value.submission)
                    assertFalse(vm.uiState.value.submission.canRetrySubmission)
                    vm.retrySubmission()
                    runCurrent()
                    assertEquals(1, repo.attempts.size)
                } finally {
                    pending.complete(PressEventResult.Failure(PressEventFailure.Retryable))
                }
                runCurrent()
                assertEquals(EventSubmissionStatus.Failed, vm.uiState.value.submission)
                assertTrue(vm.uiState.value.submission.canRetrySubmission)
                val retry = CompletableDeferred<PressEventResult<PressEventAttemptResult>>()
                repo.delayedSubmit = retry
                vm.retrySubmission()
                vm.retrySubmission()
                runCurrent()
                assertEquals(EventSubmissionStatus.Submitting, vm.uiState.value.submission)
                assertFalse(vm.uiState.value.submission.canRetrySubmission)
                assertEquals(2, repo.attempts.size)
                assertSame(repo.attempts[0], repo.attempts[1])
                retry.complete(PressEventResult.Success(result(repo.attempts.last())))
                runCurrent()
                assertEquals(EventSubmissionStatus.Submitted, vm.uiState.value.submission)
            }
        }

    @Test
    fun hidingAtDeadlineBeforeTimerResumesSubmitsOnce() =
        runTest {
            fixture { vm, repo ->
                vm.start()
                advanceTimeBy(3_000)
                runCurrent()
                assertTrue(vm.press(EmotionKind.Angry))
                advanceTimeBy(20_000)
                assertEquals(PressEventPhase.Playing, vm.uiState.value.game.phase)
                vm.onScreenHidden()
                vm.onScreenHidden()
                runCurrent()
                assertEquals(PressEventPhase.Finished, vm.uiState.value.game.phase)
                assertEquals(1L, repo.attempts.single().total)
                assertEquals(EventSubmissionStatus.Submitted, vm.uiState.value.submission)
            }
        }

    @Test
    fun hidingJustBeforeDeadlineCancelsWithoutSubmission() =
        runTest {
            fixture { vm, repo ->
                vm.start()
                advanceTimeBy(22_999)
                vm.onScreenHidden()
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(PressEventPhase.Ready, vm.uiState.value.game.phase)
                assertTrue(repo.attempts.isEmpty())
            }
        }

    @Test
    fun fastRankingResponseNeverShowsLoadingIndicator() =
        runTest {
            fixture { vm, repo ->
                repo.responseDelayMillis = 200L
                vm.onScreenResumed()
                runCurrent()
                advanceTimeBy(199L)
                assertFalse(vm.uiState.value.rankingLoading)
                advanceTimeBy(2L)
                runCurrent()
                assertFalse(vm.uiState.value.rankingLoading)
            }
        }

    @Test
    fun slowRankingResponseKeepsLoadingIndicatorForMinimumTime() =
        runTest {
            fixture { vm, repo ->
                repo.responseDelayMillis = 400L
                vm.onScreenResumed()
                runCurrent()
                advanceTimeBy(301L)
                assertTrue(vm.uiState.value.rankingLoading)
                advanceTimeBy(100L)
                assertTrue(vm.uiState.value.rankingLoading)
                advanceTimeBy(399L)
                assertTrue(vm.uiState.value.rankingLoading)
                advanceTimeBy(2L)
                runCurrent()
                assertFalse(vm.uiState.value.rankingLoading)
            }
        }

    @Test
    fun rejectsLatePreviousSessionResponseAndDoesNotSendOldAttemptUnderNewSession() =
        runTest {
            fixture { vm, repo ->
                val pending = CompletableDeferred<PressEventResult<PressEventAttemptResult>>()
                repo.delayedSubmit = pending
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                val attempt = repo.attempts.single()
                vm.onContextChanged("other", 2L)
                pending.complete(PressEventResult.Success(result(attempt)))
                runCurrent()
                assertEquals(null, vm.uiState.value.result)
                assertEquals(EventSubmissionStatus.None, vm.uiState.value.submission)
                vm.retrySubmission()
                runCurrent()
                assertEquals(1, repo.attempts.size)
                assertEquals(listOf(1L), repo.generations)
            }
        }

    @Test
    fun absentEventIdDoesNotStartOrCallServerAndConflictIsNotRetried() =
        runTest {
            fixture { vm, repo ->
                vm.onContextChanged(null, 1L)
                vm.start()
                vm.onScreenResumed()
                runCurrent()
                assertFalse(vm.uiState.value.configured)
                assertTrue(repo.cursors.isEmpty())
                assertEquals(PressEventPhase.Ready, vm.uiState.value.game.phase)
                vm.onContextChanged("event", 1L)
                repo.submitResult = PressEventResult.Failure(PressEventFailure.Conflict)
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                assertEquals(EventSubmissionStatus.Conflict, vm.uiState.value.submission)
                vm.retrySubmission()
                runCurrent()
                assertEquals(1, repo.attempts.size)
                assertTrue(vm.uiState.value.canStart)
            }
        }

    @Test
    fun finishedSubmissionContinuesWhileHiddenAndRankingsRefreshOnResume() =
        runTest {
            fixture { vm, repo ->
                val pending = CompletableDeferred<PressEventResult<PressEventAttemptResult>>()
                repo.delayedSubmit = pending
                vm.onScreenResumed()
                runCurrent()
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                vm.onScreenHidden()
                pending.complete(PressEventResult.Success(result(repo.attempts.single())))
                runCurrent()
                assertEquals(EventSubmissionStatus.Submitted, vm.uiState.value.submission)
                assertEquals(1, repo.cursors.size)
                vm.onScreenResumed()
                runCurrent()
                assertEquals(2, repo.cursors.size)
            }
        }

    @Test
    fun inputAtDeadlineCompletesAndSubmitsOnceEvenBeforeTimerResumes() =
        runTest {
            fixture { vm, repo ->
                vm.start()
                advanceTimeBy(3_000)
                runCurrent()
                assertTrue(vm.press(EmotionKind.Angry))
                advanceTimeBy(20_000)
                assertFalse(vm.press(EmotionKind.Angry))
                runCurrent()
                assertEquals(1, repo.attempts.size)
                assertEquals(1L, repo.attempts.single().total)
                assertEquals(EventSubmissionStatus.Submitted, vm.uiState.value.submission)
                vm.returnToRanking()
                runCurrent()
                assertEquals(1, repo.attempts.size)
            }
        }

    @Test
    fun rejectedSubmissionCanRetrySameAttemptOnlyInsideResultDialog() =
        runTest {
            fixture { vm, repo ->
                repo.submitResult = PressEventResult.Failure(PressEventFailure.Rejected)
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                val attempt = repo.attempts.single()
                assertEquals(EventSubmissionStatus.Rejected, vm.uiState.value.submission)
                assertFalse(vm.uiState.value.canStart)
                vm.retrySubmission()
                vm.retrySubmission()
                runCurrent()
                assertEquals(2, repo.attempts.size)
                assertSame(attempt, repo.attempts.last())
                vm.returnToRanking()
                assertEquals(EventSubmissionStatus.None, vm.uiState.value.submission)
                assertTrue(vm.uiState.value.canStart)
                vm.retrySubmission()
                runCurrent()
                assertEquals(2, repo.attempts.size)
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                assertNotEquals(attempt.requestId, repo.attempts.last().requestId)
            }
        }

    @Test
    fun returningToRankingDiscardsFailedAttemptAndIgnoresLateSubmissionResponse() =
        runTest {
            fixture { vm, repo ->
                repo.submitResult = PressEventResult.Failure(PressEventFailure.Retryable)
                vm.start()
                advanceTimeBy(23_000)
                runCurrent()
                val attempt = repo.attempts.single()
                val pending = CompletableDeferred<PressEventResult<PressEventAttemptResult>>()
                repo.delayedSubmit = pending
                vm.retrySubmission()
                runCurrent()
                vm.returnToRanking()
                pending.complete(PressEventResult.Success(result(attempt)))
                runCurrent()
                assertEquals(PressEventPhase.Ready, vm.uiState.value.game.phase)
                assertEquals(EventSubmissionStatus.None, vm.uiState.value.submission)
                assertEquals(null, vm.uiState.value.result)
                vm.retrySubmission()
                runCurrent()
                assertEquals(2, repo.attempts.size)
            }
        }

    @Test
    fun faceCycleMatchesExistingVisualPolicy() {
        var progress = PressFaceDemoProgress()
        for (count in 0L..100L) {
            assertEquals(progress.visual, eventFaceVisuals(mapOf(EmotionKind.Angry to count))[EmotionKind.Angry])
            progress = progress.afterInput(true)
        }
    }

    private suspend fun TestScope.fixture(block: suspend TestScope.(PressEventViewModel, FakeRepository) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeRepository()
        val vm = PressEventViewModel(repo, "event", 1L) { testScheduler.currentTime }
        val owner = ViewModelStore().apply { put("event", vm) }
        try {
            block(vm, repo)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository : PressEventRepository {
        val attempts = mutableListOf<PressEventAttempt>()
        val generations = mutableListOf<Long>()
        val cursors = mutableListOf<String?>()
        val pages = mutableMapOf<String?, PressEventResult<PressEventRankingPage>>()
        var submitResult: PressEventResult<PressEventAttemptResult>? = null
        var responseDelayMillis: Long? = null
        var delayedSubmit: CompletableDeferred<PressEventResult<PressEventAttemptResult>>? = null

        override suspend fun submit(
            eventId: String,
            attempt: PressEventAttempt,
            sessionGeneration: Long,
        ): PressEventResult<PressEventAttemptResult> {
            attempts += attempt
            generations += sessionGeneration
            return delayedSubmit?.let { withContext(NonCancellable) { it.await() } } ?: submitResult
                ?: PressEventResult.Success(result(attempt))
        }

        override suspend fun rankings(
            eventId: String,
            sessionGeneration: Long,
            limit: Int,
            cursor: String?,
        ): PressEventResult<PressEventRankingPage> {
            cursors += cursor
            responseDelayMillis?.let { delay(it) }
            return pages[cursor] ?: PressEventResult.Success(page())
        }
    }

    private companion object {
        fun result(attempt: PressEventAttempt) =
            PressEventAttemptResult(attempt.requestId, attempt.total, 156, 7, "2026-11-11T03:00:00Z", false)

        fun entry(
            id: String,
            rank: Long,
        ) = PressEventRankingEntry(rank, id, id, 100, false)

        fun page(
            items: List<PressEventRankingEntry> = emptyList(),
            cursor: String? = null,
        ) = PressEventRankingPage(
            null,
            items,
            cursor != null,
            cursor,
            null,
            "2026-11-11T03:00:00Z",
        )
    }
}
