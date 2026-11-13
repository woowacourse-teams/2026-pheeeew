package com.pheeeew.data.press.read

import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.repository.PressReadResult
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Fetches statistics without mutating session state. Cancellation always reaches the owner. */
internal class PressStatisticsReader(
    private val statistics: PressStatisticsDataSource,
) {
    suspend fun readPair(): PressStatisticsPair =
        coroutineScope {
            val personalRead = async { readSafely(statistics::findMyToday) }
            val aggregateRead = async { readAggregate() }
            PressStatisticsPair(personalRead.await(), aggregateRead.await())
        }

    suspend fun readAggregate(): PressReadResult<AllDailyPressSnapshot> = readSafely(statistics::findAllToday)

    private suspend fun <T> readSafely(read: suspend () -> PressReadResult<T>): PressReadResult<T> =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PressReadResult.Unavailable
        }
}

internal data class PressStatisticsPair(
    val personal: PressReadResult<MyDailyPressSnapshot>,
    val aggregate: PressReadResult<AllDailyPressSnapshot>,
) {
    fun hasDifferentDates(): Boolean {
        val personalPressDate = (personal as? PressReadResult.Loaded)?.value?.pressDate ?: return false
        val aggregatePressDate = (aggregate as? PressReadResult.Loaded)?.value?.pressDate ?: return false
        return personalPressDate != aggregatePressDate
    }
}
