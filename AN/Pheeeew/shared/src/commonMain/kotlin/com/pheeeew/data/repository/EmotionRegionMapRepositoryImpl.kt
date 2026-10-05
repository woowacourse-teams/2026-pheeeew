package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.cache.EmotionRegionMapCache
import com.pheeeew.data.remote.emotion.EmotionRegionMapApi
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.EmotionRegionMapRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class EmotionRegionMapRepositoryImpl(
    private val api: EmotionRegionMapApi,
) : EmotionRegionMapRepository {
    private val cache = EmotionRegionMapCache()
    private var cacheGeneration = 0L

    override fun findSnapshot(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String?,
    ): List<EmotionRegion>? = cache.snapshot(bounds, level, groupId)

    override suspend fun findRegions(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        groupId: String?,
        forceRefresh: Boolean,
    ): EmotionRegionResult {
        if (forceRefresh) {
            cacheGeneration++
            cache.invalidate(level, groupId)
        } else {
            findSnapshot(bounds, level, groupId)?.let { return EmotionRegionResult.Success(it) }
        }
        val generation = cacheGeneration
        return try {
            when (val result = api.findRegions(bounds, level, groupId)) {
                is ApiResult.Failure -> {
                    EmotionRegionResult.Failure
                }

                is ApiResult.Success -> {
                    val regions = result.value.mapNotNull(::parseRegion)
                    if (result.value.isNotEmpty() && regions.isEmpty()) {
                        EmotionRegionResult.Failure
                    } else {
                        if (generation == cacheGeneration) cache.put(bounds, level, groupId, regions)
                        EmotionRegionResult.Success(regions)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            EmotionRegionResult.Failure
        }
    }

    private fun parseRegion(element: JsonElement): EmotionRegion? =
        runCatching {
            val feature = element as? JsonObject ?: return null
            if ((feature["type"] as? JsonPrimitive)?.contentOrNull != "Feature") return null
            val id = (feature["id"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
            val geometry = feature["geometry"] as? JsonObject ?: return null
            if ((geometry["type"] as? JsonPrimitive)?.contentOrNull != "Point") return null
            val coordinates = geometry["coordinates"] as? JsonArray ?: return null
            if (coordinates.size != 2) return null
            val longitude = (coordinates[0] as? JsonPrimitive)?.doubleOrNull ?: return null
            val latitude = (coordinates[1] as? JsonPrimitive)?.doubleOrNull ?: return null
            if (!longitude.isFinite() || longitude !in -180.0..180.0 || !latitude.isFinite() ||
                latitude !in -90.0..90.0
            ) {
                return null
            }
            val properties = feature["properties"] as? JsonObject ?: return null
            // The published Swagger schema currently points at the individual-pin Properties type.
            // Accept the region summary's descriptive aliases until its schema is corrected.
            val name =
                properties.text("name", "regionName", "label", "administrativeName")?.takeIf(String::isNotBlank)
                    ?: return null
            val count =
                properties.number("count", "emotionCount", "totalCount", "totalEmotionCount")?.takeIf { it > 0 }
                    ?: return null
            val stateName =
                properties.text(
                    "representativeState",
                    "dominantState",
                    "state",
                    "representativeEmotion",
                    "representativeEmotionState",
                )
            val state = stateName?.let { value -> EmotionState.entries.firstOrNull { it.name == value } }
            if (stateName != null && state == null) return null
            EmotionRegion(id, name, longitude, latitude, count, state)
        }.getOrNull()

    private fun JsonObject.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject.number(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.longOrNull }
}
