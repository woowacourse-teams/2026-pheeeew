package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.cache.EmotionMapCache
import com.pheeeew.data.remote.emotion.EmotionGroupStampDto
import com.pheeeew.data.remote.emotion.EmotionMapApi
import com.pheeeew.data.remote.emotion.EmotionMapFeatureDto
import com.pheeeew.data.remote.emotion.EmotionMapPageDto
import com.pheeeew.data.remote.emotion.EmotionMapPropertiesDto
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapFailure
import com.pheeeew.domain.model.emotion.EmotionMapPage
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionMapSnapshot
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.EmotionMapRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

class EmotionMapRepositoryImpl(
    private val api: EmotionMapApi,
) : EmotionMapRepository {
    private val cache = EmotionMapCache()

    override fun findSnapshot(
        bounds: EmotionMapBounds,
        groupId: String?,
    ): EmotionMapSnapshot? = cache.snapshot(bounds, groupId)

    override suspend fun findPage(
        bounds: EmotionMapBounds?,
        groupId: String?,
        cursor: String?,
        forceRefresh: Boolean,
    ): EmotionMapPageResult {
        if (forceRefresh && cursor == null && bounds != null) {
            // Older overlapping regions must not replace newly registered pins after a refresh.
            cache.invalidate(bounds, groupId)
        }
        if (!forceRefresh && cursor == null && bounds != null) {
            cache.completePage(bounds, groupId)?.let { page ->
                return EmotionMapPageResult.Success(page, fromCache = true)
            }
        }
        return try {
            when (
                val result =
                    api.findPage(
                        bounds?.minLongitude,
                        bounds?.minLatitude,
                        bounds?.maxLongitude,
                        bounds?.maxLatitude,
                        if (cursor == null) groupId else null,
                        cursor,
                    )
            ) {
                is ApiResult.Failure -> {
                    EmotionMapPageResult.Failure(
                        if (result.reason is NetworkFailure.Contract) {
                            EmotionMapFailure.InvalidResponse
                        } else {
                            EmotionMapFailure.Unavailable
                        },
                    )
                }

                is ApiResult.Success -> {
                    result.value.toDomain().also { pageResult ->
                        if (pageResult is EmotionMapPageResult.Success && bounds != null) {
                            cache.appendPage(bounds, groupId, cursor, pageResult.page)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            EmotionMapPageResult.Failure(EmotionMapFailure.InvalidResponse)
        }
    }

    private fun EmotionMapPageDto.toDomain(): EmotionMapPageResult {
        if (hasNext && nextCursor.isNullOrBlank()) {
            return EmotionMapPageResult.Failure(EmotionMapFailure.InvalidResponse)
        }
        var invalidCount = 0
        val pins =
            items.mapNotNull { item ->
                item.toDomainOrNull().also { if (it == null) invalidCount++ }
            }
        return EmotionMapPageResult.Success(
            EmotionMapPage(pins, hasNext, nextCursor, invalidCount),
        )
    }

    private fun EmotionMapFeatureDto.toDomainOrNull(): EmotionMapPin? =
        runCatching {
            require(id > 0)
            require(geometry.type == null || geometry.type == "Point")
            require(geometry.coordinates.size == 2)
            val longitude = geometry.coordinates[0]
            val latitude = geometry.coordinates[1]
            require(longitude.isFinite() && longitude in -180.0..180.0)
            require(latitude.isFinite() && latitude in -90.0..90.0)
            require(properties.rotationDegreesValid(properties.rotationDegrees))
            Instant.parse(properties.createdAt)
            val state = EmotionState.entries.firstOrNull { it.name == properties.state } ?: return null
            EmotionMapPin(
                id = id,
                longitude = longitude,
                latitude = latitude,
                createdAt = properties.createdAt,
                state = state,
                rotationDegrees = properties.rotationDegrees,
                groupStamp = properties.groupStamp?.toDomain(),
            )
        }.getOrNull()

    private fun EmotionMapPropertiesDto.rotationDegreesValid(value: Double): Boolean =
        value.isFinite() && value >= 0.0 && value < 360.0

    private fun EmotionGroupStampDto.toDomain(): GroupStamp {
        require(text.isNotBlank())
        val resolvedFrame =
            GroupStampFrame.entries.firstOrNull { it.name == frame }
                ?: error("Unknown stamp frame")
        return GroupStamp(
            text = text,
            textColor = StampColor.parseServerValue(textColor) ?: error("Invalid stamp text color"),
            backgroundColor = StampColor.parseServerValue(backgroundColor) ?: error("Invalid stamp background color"),
            frame = resolvedFrame,
        )
    }
}
