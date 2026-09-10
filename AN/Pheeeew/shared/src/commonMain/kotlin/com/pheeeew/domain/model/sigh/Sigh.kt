package com.pheeeew.domain.model.sigh

import com.pheeeew.domain.model.geo.Coordinate
import kotlin.time.Instant

data class Sigh(
    val id: Long,
    val coordinate: Coordinate,
    val memo: String?,
    val nickname: String = "알 수 없음",
    val createdAt: Instant,
) {
    fun toPin(): SighPin =
        SighPin(
            id = id,
            coordinate = coordinate,
        )
}
