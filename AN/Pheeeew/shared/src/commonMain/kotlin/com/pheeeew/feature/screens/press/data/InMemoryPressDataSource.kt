package com.pheeeew.feature.screens.press.data

import com.pheeeew.feature.emotion.model.EmotionCountUiModel
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.model.PressPeriodSnapshot
import com.pheeeew.feature.screens.press.model.PressPeriodSnapshots

/** Fixture-only state; values remain in memory and are never sent to the backend. */
internal class InMemoryPressDataSource(
    initial: PressPeriodSnapshots = PressFixtureData.initialSnapshots(),
) : PressDataSource {
    private var snapshots = initial

    override fun load(): PressPeriodSnapshots = snapshots

    override fun recordPress(emotion: EmotionKind): PressPeriodSnapshots {
        snapshots = snapshots.record(emotion)
        return snapshots
    }
}

internal object PressFixtureData {
    fun initialSnapshots(): PressPeriodSnapshots =
        PressPeriodSnapshots(
            today = snapshot(all = listOf(6, 4, 5, 3, 2), mine = listOf(1, 1, 1, 0, 0)),
            thisWeek = snapshot(all = listOf(22, 17, 19, 13, 11), mine = listOf(5, 4, 3, 2, 2)),
        )

    private fun snapshot(
        all: List<Int>,
        mine: List<Int>,
    ): PressPeriodSnapshot {
        val emotions = EmotionKind.entries
        val allCounts = emotions.zip(all).map { (kind, count) -> EmotionCountUiModel(kind, count.toLong()) }
        val myCounts = emotions.zip(mine).map { (kind, count) -> EmotionCountUiModel(kind, count.toLong()) }
        return PressPeriodSnapshot(
            emotionCounts = allCounts,
            myEmotionCounts = myCounts,
            myTotalCount = myCounts.sumOf { it.count },
            totalCount = allCounts.sumOf { it.count },
        )
    }
}
