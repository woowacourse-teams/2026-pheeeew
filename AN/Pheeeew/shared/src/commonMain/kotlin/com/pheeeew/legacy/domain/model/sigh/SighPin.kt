package com.pheeeew.legacy.domain.model.sigh

import com.pheeeew.legacy.domain.model.geo.Coordinate
import kotlin.time.Instant

data class SighPin(
    val id: Long,
    val coordinate: Coordinate,
    val createdAt: Instant? = null,
)
