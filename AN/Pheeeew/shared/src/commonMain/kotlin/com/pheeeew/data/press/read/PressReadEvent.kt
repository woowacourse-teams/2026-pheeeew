package com.pheeeew.data.press.read

import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot

/** 조회가 관찰한 쓰기 상태이며 응답 정산에 사용할 시작 시점의 값을 보존한다. */
internal data class PressReadContext(
    val writeOutcomeUnknown: Boolean = false,
    val writeInFlight: Boolean = false,
    val acceptedPressCoverage: Long = 0L,
    val personalPressDate: String? = null,
)

/** 조회 결과와 진행 상태를 세션에 동기적으로 전달한다. */
internal sealed interface PressReadEvent {
    data object PairStarted : PressReadEvent

    data class PairCompleted(
        val personal: MyDailyPressSnapshot?,
        val aggregate: AllDailyPressSnapshot?,
        val context: PressReadContext,
    ) : PressReadEvent

    data object PairIdle : PressReadEvent

    data object AggregateStarted : PressReadEvent

    data class AggregateCompleted(
        val snapshot: AllDailyPressSnapshot?,
        val context: PressReadContext,
    ) : PressReadEvent

    data object AggregateDiscarded : PressReadEvent

    data object PairRefreshRequired : PressReadEvent
}
