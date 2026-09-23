package com.pheeeew.legacy.core.location

import com.pheeeew.legacy.domain.model.location.CurrentLocation

/** 위치가 '현재 위치'로 사용할 만큼 신선한지 판정합니다. */
class LocationFreshnessPolicy(
    val maximumAgeMillis: Long = DEFAULT_MAXIMUM_AGE_MILLIS,
) {
    init {
        require(maximumAgeMillis >= 0L) { "maximumAgeMillis must not be negative" }
    }

    fun isFresh(
        location: CurrentLocation,
        currentTimeMillis: Long,
    ): Boolean {
        if (location.capturedAtMillis > currentTimeMillis) return false

        val ageMillis = currentTimeMillis - location.capturedAtMillis
        return ageMillis >= 0L && ageMillis <= maximumAgeMillis
    }

    companion object {
        const val DEFAULT_MAXIMUM_AGE_MILLIS: Long = 60_000L
    }
}
