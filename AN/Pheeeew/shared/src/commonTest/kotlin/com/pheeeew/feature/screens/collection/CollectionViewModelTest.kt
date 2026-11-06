package com.pheeeew.feature.screens.collection

import androidx.lifecycle.ViewModelStore
import com.pheeeew.domain.model.collection.CollectionActivityProgress
import com.pheeeew.domain.model.collection.CollectionCalendar
import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionCardReference
import com.pheeeew.domain.model.collection.CollectionCounts
import com.pheeeew.domain.model.collection.CollectionEmotion
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.domain.model.collection.CollectionEmotionPage
import com.pheeeew.domain.model.collection.CollectionFailure
import com.pheeeew.domain.model.collection.CollectionHistory
import com.pheeeew.domain.model.collection.CollectionHistoryQuery
import com.pheeeew.domain.model.collection.CollectionImages
import com.pheeeew.domain.model.collection.CollectionResult
import com.pheeeew.domain.model.collection.CollectionToday
import com.pheeeew.domain.model.collection.CollectionTodayItem
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.CollectionRepository
import com.pheeeew.feature.emotion.model.EmotionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
    @Test
    fun `claim starts a fresh history read while an older refresh is active`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val oldHistory = CompletableDeferred<CollectionResult<CollectionHistory>>()
                val repository = FakeCollectionRepository(oldHistory)
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "오늘도히유")
                runCurrent()
                assertTrue(viewModel.uiState.value.isRefreshRequestActive)
                val scrollRequestId = viewModel.uiState.value.scrollToTopRequestId

                viewModel.claim(EmotionType.EXHAUSTED)
                runCurrent()

                assertEquals(scrollRequestId + 1, viewModel.uiState.value.scrollToTopRequestId)
                assertEquals(1, repository.claimRequests)
                assertEquals(2, repository.historyRequests)
                assertEquals(
                    listOf("claimed-card"),
                    viewModel.uiState.value.cards
                        .map { it.id },
                )
                assertFalse(viewModel.uiState.value.isRefreshRequestActive)

                oldHistory.complete(CollectionResult.Success(repository.history("stale-card")))
                runCurrent()
                assertEquals(
                    listOf("claimed-card"),
                    viewModel.uiState.value.cards
                        .map { it.id },
                )
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `resume refresh keeps the current scroll request unchanged`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val repository = FakeCollectionRepository()
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "오늘도히유")
                runCurrent()
                val scrollRequestId = viewModel.uiState.value.scrollToTopRequestId

                viewModel.onScreenResumed()
                runCurrent()

                assertEquals(scrollRequestId, viewModel.uiState.value.scrollToTopRequestId)
                assertEquals(2, repository.historyRequests)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `claim resets scroll only after the new history is applied`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val newHistory = CompletableDeferred<CollectionResult<CollectionHistory>>()
                val repository = FakeCollectionRepository(secondHistory = newHistory)
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "오늘도히유")
                runCurrent()
                val scrollRequestId = viewModel.uiState.value.scrollToTopRequestId

                viewModel.claim(EmotionType.EXHAUSTED)
                runCurrent()
                assertEquals(scrollRequestId, viewModel.uiState.value.scrollToTopRequestId)

                newHistory.complete(CollectionResult.Success(repository.history("new-card")))
                runCurrent()
                assertEquals(
                    listOf("new-card"),
                    viewModel.uiState.value.cards
                        .map { it.id },
                )
                assertEquals(scrollRequestId + 1, viewModel.uiState.value.scrollToTopRequestId)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed claim history keeps scroll reset pending until a successful retry`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val failedHistory = CompletableDeferred<CollectionResult<CollectionHistory>>()
                val retryHistory = CompletableDeferred<CollectionResult<CollectionHistory>>()
                val repository = FakeCollectionRepository(secondHistory = failedHistory, thirdHistory = retryHistory)
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "오늘도히유")
                runCurrent()
                val scrollRequestId = viewModel.uiState.value.scrollToTopRequestId
                val previousCards = viewModel.uiState.value.cards

                viewModel.claim(EmotionType.EXHAUSTED)
                runCurrent()
                failedHistory.complete(CollectionResult.Failure(CollectionFailure(503, null, null)))
                runCurrent()
                assertEquals(previousCards, viewModel.uiState.value.cards)
                assertEquals(scrollRequestId, viewModel.uiState.value.scrollToTopRequestId)
                assertFalse(viewModel.uiState.value.isRefreshRequestActive)

                viewModel.retryCards()
                runCurrent()
                assertEquals(scrollRequestId, viewModel.uiState.value.scrollToTopRequestId)

                retryHistory.complete(CollectionResult.Success(repository.history("new-card")))
                runCurrent()
                assertEquals(
                    listOf("new-card"),
                    viewModel.uiState.value.cards
                        .map { it.id },
                )
                assertEquals(scrollRequestId + 1, viewModel.uiState.value.scrollToTopRequestId)

                viewModel.onScreenResumed()
                runCurrent()
                assertEquals(scrollRequestId + 1, viewModel.uiState.value.scrollToTopRequestId)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `claim excludes unregistered emotions and cards use generic count labels`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val repository = FakeCollectionRepository(unregisteredPressCount = 1000)
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "오늘도히유")
                runCurrent()

                viewModel.claim(EmotionType.FRUSTRATED)
                runCurrent()
                assertEquals(0, repository.claimRequests)

                viewModel.claim(EmotionType.EXHAUSTED)
                runCurrent()
                assertEquals(1, repository.claimRequests)
                val card =
                    viewModel.uiState.value.cards
                        .single()
                assertEquals("프레스", card.pressLabel)
                assertEquals("스탬프", card.stampLabel)
                assertEquals(100L, card.pressCount)
                assertEquals(1L, card.stampCount)

                viewModel.selectCard(card.id)
                runCurrent()
                assertEquals(card, viewModel.uiState.value.selectedCard)
            } finally {
                store.clear()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `returning to a date shows cached emotions before merging fresh results`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val first =
                    CollectionEmotion(
                        1L,
                        "2026-10-15T11:00:00Z",
                        EmotionState.EXHAUSTED,
                        CollectionEmotionContentType.NONE,
                        null,
                        null,
                    )
                val latest = first.copy(id = 2L, createdAt = "2026-10-15T12:00:00Z")
                val refresh = CompletableDeferred<CollectionResult<CollectionEmotionPage>>()
                val base = FakeCollectionRepository()
                var visits = 0
                val repository =
                    object : CollectionRepository by base {
                        override suspend fun findCalendar(month: String): CollectionResult<CollectionCalendar> =
                            CollectionResult.Success(
                                CollectionCalendar(base.history("first").cards + base.history("second").cards),
                            )

                        override suspend fun findEmotions(
                            cardId: String,
                            cursor: String?,
                        ): CollectionResult<CollectionEmotionPage> =
                            when {
                                cardId == "second" -> {
                                    CollectionResult.Success(
                                        CollectionEmotionPage(emptyList(), false, null),
                                    )
                                }

                                ++visits == 1 -> {
                                    CollectionResult.Success(CollectionEmotionPage(listOf(first), false, null))
                                }

                                else -> {
                                    refresh.await()
                                }
                            }
                    }
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "나")
                runCurrent()
                viewModel.selectCalendarCard("first")
                runCurrent()
                viewModel.selectCalendarCard("second")
                runCurrent()
                viewModel.selectCalendarCard("first")
                runCurrent()
                assertEquals(listOf(first), viewModel.uiState.value.day.calendarEmotions)

                refresh.complete(CollectionResult.Success(CollectionEmotionPage(listOf(latest, first), false, null)))
                runCurrent()
                assertEquals(listOf(latest, first), viewModel.uiState.value.day.calendarEmotions)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `append retries its cursor and an invalid cursor restarts once from page one`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            try {
                val base = FakeCollectionRepository()
                val cursors = mutableListOf<String?>()
                val emotion =
                    CollectionEmotion(
                        1L,
                        "2026-10-15T11:00:00Z",
                        EmotionState.EXHAUSTED,
                        CollectionEmotionContentType.NONE,
                        null,
                        null,
                    )
                val repository =
                    object : CollectionRepository by base {
                        override suspend fun findCalendar(month: String): CollectionResult<CollectionCalendar> =
                            CollectionResult.Success(CollectionCalendar(base.history("card").cards))

                        override suspend fun findEmotions(
                            cardId: String,
                            cursor: String?,
                        ): CollectionResult<CollectionEmotionPage> {
                            cursors += cursor
                            return when (cursors.size) {
                                1 -> {
                                    CollectionResult.Success(CollectionEmotionPage(listOf(emotion), true, "old"))
                                }

                                2 -> {
                                    CollectionResult.Failure(CollectionFailure(null, "NETWORK", null))
                                }

                                3 -> {
                                    CollectionResult.Failure(CollectionFailure(400, "COLLECTION-007", null))
                                }

                                else -> {
                                    CollectionResult.Success(
                                        CollectionEmotionPage(listOf(emotion.copy(id = 2)), false, null),
                                    )
                                }
                            }
                        }
                    }
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                viewModel.onSessionChanged(1L, "나")
                viewModel.openCalendarDay(viewModel.uiState.value.calendarMonth, "card")
                runCurrent()
                viewModel.loadNextCalendarEmotions()
                runCurrent()
                assertEquals(listOf(emotion), viewModel.uiState.value.day.calendarEmotions)

                viewModel.retryNextCalendarEmotions()
                runCurrent()
                assertEquals(listOf(null, "old", "old", null), cursors)
                assertEquals(
                    listOf(2L),
                    viewModel.uiState.value.day.calendarEmotions
                        .map { it.id },
                )

                viewModel.closeCalendarDay()
                assertEquals(null, viewModel.uiState.value.day.selectedCalendarCardId)
                assertTrue(
                    viewModel.uiState.value.calendarCards
                        .isNotEmpty(),
                )
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `late emotions from an earlier session cannot replace the same card in the new session`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            val oldPage = CompletableDeferred<CollectionResult<CollectionEmotionPage>>()
            try {
                val base = FakeCollectionRepository()
                var requests = 0
                val repository =
                    object : CollectionRepository by base {
                        override suspend fun findCalendar(month: String): CollectionResult<CollectionCalendar> =
                            CollectionResult.Success(CollectionCalendar(base.history("card").cards))

                        override suspend fun findEmotions(
                            cardId: String,
                            cursor: String?,
                        ): CollectionResult<CollectionEmotionPage> =
                            if (++requests == 1) {
                                withContext(NonCancellable) { oldPage.await() }
                            } else {
                                CollectionResult.Success(CollectionEmotionPage(emptyList(), false, null))
                            }
                    }
                val viewModel = CollectionViewModel(repository)
                store.put("collection", viewModel)
                for (generation in 1L..2L) {
                    viewModel.onSessionChanged(generation, "나")
                    viewModel.openCalendarDay(viewModel.uiState.value.calendarMonth, "card")
                    runCurrent()
                }
                val stale =
                    CollectionEmotion(
                        1L,
                        "2026-10-15T11:00:00Z",
                        EmotionState.EXHAUSTED,
                        CollectionEmotionContentType.NONE,
                        null,
                        null,
                    )
                oldPage.complete(CollectionResult.Success(CollectionEmotionPage(listOf(stale), false, null)))
                runCurrent()

                assertEquals(2, requests)
                assertTrue(viewModel.uiState.value.day.calendarEmotionsLoaded)
                assertTrue(
                    viewModel.uiState.value.day.calendarEmotions
                        .isEmpty(),
                )
            } finally {
                oldPage.cancel()
                store.clear()
                Dispatchers.resetMain()
            }
        }

    private class FakeCollectionRepository(
        private val firstHistory: CompletableDeferred<CollectionResult<CollectionHistory>>? = null,
        private val secondHistory: CompletableDeferred<CollectionResult<CollectionHistory>>? = null,
        private val thirdHistory: CompletableDeferred<CollectionResult<CollectionHistory>>? = null,
        private val unregisteredPressCount: Long = 0,
    ) : CollectionRepository {
        var historyRequests = 0
            private set
        var claimRequests = 0
            private set

        override suspend fun findToday(): CollectionResult<CollectionToday> = CollectionResult.Success(today())

        override suspend fun claim(
            date: String,
            state: EmotionState,
        ): CollectionResult<CollectionCardReference> {
            claimRequests++
            return CollectionResult.Success(reference("claimed-card", state))
        }

        override suspend fun findHistory(query: CollectionHistoryQuery): CollectionResult<CollectionHistory> {
            historyRequests++
            if (historyRequests == 1 && firstHistory != null) return firstHistory.await()
            if (historyRequests == 2 && secondHistory != null) return secondHistory.await()
            if (historyRequests == 3 && thirdHistory != null) return thirdHistory.await()
            return CollectionResult.Success(history("claimed-card"))
        }

        override suspend fun findCard(cardId: String): CollectionResult<CollectionCard> =
            CollectionResult.Success(
                CollectionCard(
                    reference = reference(cardId, EmotionState.EXHAUSTED),
                    stampCount = 1,
                    pressCount = 100,
                ),
            )

        override suspend fun findCalendar(month: String): CollectionResult<CollectionCalendar> =
            CollectionResult.Success(CollectionCalendar(emptyList()))

        override suspend fun findEmotions(
            cardId: String,
            cursor: String?,
        ): CollectionResult<CollectionEmotionPage> =
            CollectionResult.Success(CollectionEmotionPage(emptyList(), hasNext = false, nextCursor = null))

        override suspend fun findDailyEmotions(
            date: String,
            cursor: String?,
        ): CollectionResult<CollectionEmotionPage> = findEmotions(date, cursor)

        fun history(cardId: String) =
            CollectionHistory(
                nickname = "오늘도히유",
                cards =
                    listOf(
                        CollectionCard(
                            reference = reference(cardId, EmotionState.EXHAUSTED),
                            stampCount = 1,
                            pressCount = 100,
                        ),
                    ),
                counts = CollectionCounts(total = 1, byEmotion = mapOf(EmotionState.EXHAUSTED to 1)),
                hasNext = false,
                nextCursor = null,
            )

        private fun today(): CollectionToday =
            CollectionToday(
                date = "2026-10-27",
                items =
                    EmotionState.entries.map { emotion ->
                        CollectionTodayItem(
                            state = emotion,
                            stamps = CollectionActivityProgress(if (emotion == EmotionState.EXHAUSTED) 1 else 0, 1, 0),
                            presses =
                                CollectionActivityProgress(
                                    if (emotion ==
                                        EmotionState.EXHAUSTED
                                    ) {
                                        100
                                    } else {
                                        unregisteredPressCount
                                    },
                                    100,
                                    0,
                                ),
                            card = null,
                        )
                    },
            )

        private fun reference(
            id: String,
            emotion: EmotionState,
        ) = CollectionCardReference(
            cardId = id,
            date = "2026-10-27",
            state = emotion,
            images = CollectionImages("character", "round", "straight"),
        )
    }
}
