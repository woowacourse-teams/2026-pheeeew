package com.pheeeew.feature.screens.ranking

import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressRankingLoadResult
import com.pheeeew.feature.screens.ranking.press.PressRankingSource
import com.pheeeew.feature.screens.ranking.press.PressRankingStatus
import com.pheeeew.feature.screens.ranking.press.PressRankingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

/** Measures virtual source-to-state timing, not network or rendered-frame latency. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLoadingProbeTest {
    @Test
    fun compareLoadingPaths() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                for (latency in listOf(0L, 50L, 200L, 1500L)) {
                    repeat(3) { repetition ->
                        var calls = 0

                        fun record(
                            path: String,
                            action: () -> Unit,
                        ) {
                            val start = testScheduler.currentTime
                            val before = calls
                            action()
                            advanceUntilIdle()
                            val expected =
                                when (path) {
                                    "press_previous" -> latency + 150L
                                    else -> latency
                                }
                            assertEquals(expected, testScheduler.currentTime - start, path)
                            assertEquals(1, calls - before, path)
                            println(
                                "LOADING_PROBE,$path,$latency,$repetition,${testScheduler.currentTime - start},${calls - before}",
                            )
                        }
                        val pressSource =
                            object : PressRankingSource {
                                override suspend fun load(
                                    emotion: PressEmotion,
                                    weeksAgo: Int,
                                ): PressRankingLoadResult {
                                    calls++
                                    delay(latency)
                                    return PressRankingLoadResult.Loaded(
                                        "2026-10-19T00:00:00Z",
                                        "2026-10-26T00:00:00Z",
                                        true,
                                        emptyList(),
                                    )
                                }
                            }
                        lateinit var press: PressRankingViewModel
                        record("press_initial") { press = PressRankingViewModel(pressSource) }
                        assertEquals(PressRankingStatus.Ready, press.uiState.value.status)
                        record("press_refresh") { press.onRefresh() }
                        record("press_filter") { press.onEmotionSelected(PressEmotion.Angry) }
                        record("press_previous") { press.onPreviousWeek() }
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
