package com.pheeeew.domain.repository.press

import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot

interface PressStatisticsDataSource {
    suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot>

    suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot>
}

sealed interface PressReadResult<out T> {
    data class Loaded<T>(
        val value: T,
    ) : PressReadResult<T>

    data object Unavailable : PressReadResult<Nothing>
}
