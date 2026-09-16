package com.pheeeew.domain.model.sigh

import com.pheeeew.domain.model.geo.Coordinate
import kotlin.time.Instant

data class SighPin(
    val id: Long,
    val coordinate: Coordinate,
    val createdAt: Instant? = null,
)
