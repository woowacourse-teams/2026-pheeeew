package com.pheeeew.domain.model.emotion

import com.pheeeew.domain.model.group.GroupStamp

enum class EmotionState {
    FRUSTRATED,
    IRRITATED,
    EXHAUSTED,
    DISCOURAGED,
    ANGRY,
}

data class EmotionMapPin(
    val id: Long,
    val longitude: Double,
    val latitude: Double,
    val createdAt: String,
    val state: EmotionState,
    val rotationDegrees: Double,
    val groupStamp: GroupStamp?,
)

data class EmotionMapBounds(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
) {
    fun isValid(): Boolean =
        minLongitude.isFinite() && maxLongitude.isFinite() &&
            minLatitude.isFinite() && maxLatitude.isFinite() &&
            minLongitude in -180.0..180.0 && maxLongitude in -180.0..180.0 &&
            minLatitude in -90.0..90.0 && maxLatitude in -90.0..90.0 &&
            minLatitude < maxLatitude && minLongitude != maxLongitude
}

data class EmotionMapPage(
    val pins: List<EmotionMapPin>,
    val hasNext: Boolean,
    val nextCursor: String?,
    val invalidItemCount: Int,
)

data class EmotionMapSnapshot(
    val pins: List<EmotionMapPin>,
)

sealed interface EmotionMapFailure {
    data object Unavailable : EmotionMapFailure

    data object InvalidResponse : EmotionMapFailure
}

sealed interface EmotionMapPageResult {
    data class Success(
        val page: EmotionMapPage,
        val fromCache: Boolean = false,
    ) : EmotionMapPageResult

    data class Failure(
        val reason: EmotionMapFailure,
    ) : EmotionMapPageResult
}
