package com.pheeeew.feature.screens.press

import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.data.InMemoryPressDataSource
import com.pheeeew.feature.screens.press.data.PressFixtureData
import com.pheeeew.feature.screens.press.model.PressPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PressViewModelTest {
    @Test
    fun `starts on today with fixture snapshot`() {
        val initial = PressFixtureData.initialSnapshots()
        val viewModel = PressViewModel(InMemoryPressDataSource(initial))

        assertEquals(PressPeriod.Today, viewModel.uiState.value.period)
        assertEquals(initial.today, viewModel.uiState.value.selectedSnapshot)
    }

    @Test
    fun `period selection changes only the selected snapshot`() {
        val initial = PressFixtureData.initialSnapshots()
        val viewModel = PressViewModel(InMemoryPressDataSource(initial))

        viewModel.onPeriodSelected(PressPeriod.ThisWeek)

        assertEquals(PressPeriod.ThisWeek, viewModel.uiState.value.period)
        assertEquals(initial.thisWeek, viewModel.uiState.value.selectedSnapshot)
        assertEquals(initial.today.emotionCounts, viewModel.uiState.value.todayEmotionCounts)
        assertEquals(initial, viewModel.uiState.value.snapshots)
    }

    @Test
    fun `one press increments selected emotion and personal and overall totals in both periods`() {
        val viewModel = PressViewModel(InMemoryPressDataSource(PressFixtureData.initialSnapshots()))
        val before = viewModel.uiState.value.snapshots

        assertTrue(viewModel.onEmotionTap(EmotionKind.Annoyed))

        val after = viewModel.uiState.value.snapshots
        assertNotSame(before, after)
        assertEquals(before.today.totalCount + 1, after.today.totalCount)
        assertEquals(before.today.myTotalCount + 1, after.today.myTotalCount)
        assertEquals(before.today.emotionCounts[1].count + 1, after.today.emotionCounts[1].count)
        assertEquals(before.today.myEmotionCounts[1].count + 1, after.today.myEmotionCounts[1].count)
        assertEquals(before.thisWeek.totalCount + 1, after.thisWeek.totalCount)
        assertEquals(before.thisWeek.myTotalCount + 1, after.thisWeek.myTotalCount)
        assertEquals(before.thisWeek.emotionCounts[1].count + 1, after.thisWeek.emotionCounts[1].count)
        assertEquals(before.thisWeek.myEmotionCounts[1].count + 1, after.thisWeek.myEmotionCounts[1].count)
    }

    @Test
    fun `screen view model recreation reads the retained app session counts`() {
        val session = PressFixtureSessionViewModel()
        val firstScreenViewModel = PressViewModel(session.dataSource)

        firstScreenViewModel.onEmotionTap(EmotionKind.Annoyed)
        val secondScreenViewModel = PressViewModel(session.dataSource)

        assertEquals(firstScreenViewModel.uiState.value.snapshots, secondScreenViewModel.uiState.value.snapshots)
    }
}
