package com.pheeeew.legacy.domain.model.location

/** 현재 위치 조회 상태입니다. */
sealed interface LocationState {
    /** 위치를 조회 중입니다. */
    data object Loading : LocationState

    /** 신선한 현재 위치를 획득했습니다. */
    data class Available(
        val location: CurrentLocation,
    ) : LocationState

    /** 현재 위치를 사용할 수 없습니다. */
    data class Unavailable(
        val reason: LocationError,
    ) : LocationState
}
