package com.pheeeew.domain.model.sigh

import com.pheeeew.domain.model.geo.Coordinate

data class CreateSighCommand(
    val requestId: String,
    val coordinate: Coordinate,
    val memo: String?,
) {
    init {
        require(requestId.isNotBlank()) { "요청 식별자는 비어 있을 수 없습니다." }
    }
}
