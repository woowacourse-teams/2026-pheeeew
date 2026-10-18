package com.pheeeew.domain.model.emotion

enum class EmotionRegionLevel { SIDO, SIGUNGU }

/** Identifies a complete national summary independently of the viewport used to request it. */
data class EmotionRegionQueryKey(
    val level: EmotionRegionLevel,
    val startDate: String?,
    val endDate: String?,
    val contentType: MapFilterContent,
    val scope: MapFilterScope,
    val groupId: String?,
) {
    companion object {
        fun create(
            level: EmotionRegionLevel,
            groupId: String?,
            filter: EmotionSearchFilter?,
        ): EmotionRegionQueryKey {
            val effectiveGroupId = if (filter == null) groupId else filter.groupId
            return EmotionRegionQueryKey(
                level = level,
                startDate = filter?.startDate,
                endDate = filter?.endDate,
                contentType = filter?.content ?: MapFilterContent.ALL,
                scope = filter?.scope ?: if (effectiveGroupId == null) MapFilterScope.ALL else MapFilterScope.GROUP,
                groupId = effectiveGroupId,
            )
        }
    }
}

data class EmotionMapViewport(
    val bounds: EmotionMapBounds,
    val zoom: Double,
    val renderBounds: EmotionMapBounds = bounds,
) {
    fun isValid(): Boolean = bounds.isValid() && renderBounds.isValid() && zoom.isFinite() && zoom >= 0.0
}

data class EmotionRegion(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val count: Long,
    val representativeState: EmotionState?,
)

sealed interface EmotionRegionResult {
    data class Success(
        val regions: List<EmotionRegion>,
    ) : EmotionRegionResult

    data object Failure : EmotionRegionResult
}
