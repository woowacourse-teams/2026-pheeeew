package com.pheeeew.data.remote.press

import com.pheeeew.core.network.ApiResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.repository.press.PressReadResult
import com.pheeeew.domain.repository.press.PressStatisticsDataSource
import kotlinx.coroutines.CancellationException

internal class PressApiStatisticsDataSource(
    private val api: EmotionPressApi,
) : PressStatisticsDataSource {
    override suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot> =
        when (val result = api.findMyToday()) {
            is ApiResult.Success -> {
                safelyRead {
                    val counts = result.value.counts.toDomainCounts()
                    MyDailyPressSnapshot(
                        pressDate = result.value.pressDate,
                        counts = counts,
                        total = result.value.total,
                    ).also { require(it.total >= 0L && it.counts.values.sum() == it.total) }
                }
            }

            is ApiResult.Failure -> {
                PressReadResult.Unavailable
            }
        }

    override suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot> =
        when (val result = api.findAllToday()) {
            is ApiResult.Success -> {
                safelyRead {
                    require(result.value.total >= 0L)
                    AllDailyPressSnapshot(result.value.pressDate, result.value.total)
                }
            }

            is ApiResult.Failure -> {
                PressReadResult.Unavailable
            }
        }

    private suspend fun <T> safelyRead(block: () -> T): PressReadResult<T> =
        try {
            PressReadResult.Loaded(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PressReadResult.Unavailable
        }

    private fun Map<String, Long>.toDomainCounts(): Map<EmotionState, Long> =
        EmotionState.entries
            .associateWith { emotion ->
                val value = this[emotion.name] ?: error("누락된 감정 집계입니다.")
                require(value >= 0L)
                value
            }.also { require(keys == EmotionState.entries.map { it.name }.toSet()) }
}
