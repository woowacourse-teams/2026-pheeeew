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
    /** 메모와 생성 시각을 보존하는 지도 핀 projection을 만듭니다. */
    fun toPin(): SighPin =
        SighPin(
            id = id,
            coordinate = coordinate,
            createdAt = createdAt,
        )
}
