package com.pheeeew.domain.repository.press

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot

interface PressRepository {
    suspend fun findMyToday(): PressReadResult<MyDailyPressSnapshot>

    suspend fun findAllToday(): PressReadResult<AllDailyPressSnapshot>

    suspend fun submit(
        location: CurrentLocation,
        counts: Map<EmotionState, Int>,
    ): PressSubmitResult
}

sealed interface PressReadResult<out T> {
    data class Loaded<T>(
        val value: T,
    ) : PressReadResult<T>

    data object Unavailable : PressReadResult<Nothing>
}

sealed interface PressSubmitResult {
    data object Submitted : PressSubmitResult

    data object Rejected : PressSubmitResult

    data object OutcomeUnknown : PressSubmitResult

    data object Unavailable : PressSubmitResult
}
