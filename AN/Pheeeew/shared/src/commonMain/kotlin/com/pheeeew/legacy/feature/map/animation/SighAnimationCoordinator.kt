package com.pheeeew.legacy.feature.map.animation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

@Immutable
data class SighFlight(
    val id: String,
    val origin: Offset,
    val destination: Offset,
)

/** 화면이 소유하는 일회성 비행 요청의 작은 모델입니다. */
class SighAnimationCoordinator {
    fun start(
        id: String,
        origin: Offset,
        destination: Offset,
    ): SighFlight = SighFlight(id = id, origin = origin, destination = destination)
}
