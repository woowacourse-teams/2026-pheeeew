package com.pheeeew.feature.screens.collection

import androidx.lifecycle.ViewModelStore
import com.pheeeew.domain.model.collection.CollectionActivityProgress
import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionCardReference
import com.pheeeew.domain.model.collection.CollectionCounts
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    private class FakeCollectionRepository(
        private val firstHistory: CompletableDeferred<CollectionResult<CollectionHistory>>? = null,
        private val secondHistory: CompletableDeferred<CollectionResult<CollectionHistory>>? = null,
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
                                        0
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
