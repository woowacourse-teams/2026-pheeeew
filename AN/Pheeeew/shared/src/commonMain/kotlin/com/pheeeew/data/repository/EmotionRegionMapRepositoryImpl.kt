package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.cache.EmotionRegionMapCache
import com.pheeeew.data.cache.EmotionRegionMapCache.Generation
import com.pheeeew.data.remote.api.EmotionRegionMapApi
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionQueryKey
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.expandedForRegionQuery
import com.pheeeew.domain.repository.EmotionRegionMapRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

@Inject
class EmotionRegionMapRepositoryImpl(
    private val api: EmotionRegionMapApi,
) : EmotionRegionMapRepository {
    private val cache = EmotionRegionMapCache()

    private data class RequestKey(
        val query: EmotionRegionQueryKey,
        val generation: Generation,
    )

    private class InFlightRequest(
        val deferred: Deferred<EmotionRegionResult>,
        var waiterCount: Int,
    )

    private val requestsScope = MutableStateFlow(newRequestsScope())
    private val requestsMutex = Mutex()
    private val inFlight = mutableMapOf<RequestKey, InFlightRequest>()

    fun invalidateSession() {
        cache.clear()
        rotateRequestsScope()
    }

    fun invalidateAll() {
        cache.invalidateAll()
        rotateRequestsScope()
    }

    override fun findSnapshot(
        level: EmotionRegionLevel,
        filter: EmotionSearchFilter?,
    ): List<EmotionRegion>? = cache.snapshot(EmotionRegionQueryKey.create(level, filter))

    override suspend fun findRegions(
        bounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        forceRefresh: Boolean,
        filter: EmotionSearchFilter?,
    ): EmotionRegionResult {
        val key = EmotionRegionQueryKey.create(level, filter)
        val (requestKey, request, shouldStart) =
            requestsMutex.withLock {
                if (forceRefresh) {
                    cache.invalidateAll()
                    inFlight.values.forEach { it.deferred.cancel() }
                    inFlight.clear()
                } else {
                    cache.snapshot(key)?.let { return EmotionRegionResult.Success(it) }
                }
                val generation = cache.generation()
                val activeRequestKey = RequestKey(key, generation)
                val existing = inFlight[activeRequestKey]?.takeIf { it.deferred.isActive }
                if (existing != null) {
                    existing.waiterCount++
                    Triple(activeRequestKey, existing, false)
                } else {
                    inFlight.remove(activeRequestKey)
                    val queryBounds = bounds.expandedForRegionQuery(level)
                    val created =
                        InFlightRequest(
                            deferred =
                                requestsScope.value.async(start = CoroutineStart.LAZY) {
                                    fetchAndCache(key, queryBounds, level, filter, generation)
                                },
                            waiterCount = 1,
                        )
                    inFlight[activeRequestKey] = created
                    Triple(activeRequestKey, created, true)
                }
            }
        if (shouldStart) request.deferred.start()
        return try {
            request.deferred.await()
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            EmotionRegionResult.Failure
        } catch (_: Exception) {
            EmotionRegionResult.Failure
        } finally {
            releaseWaiter(requestKey, request)
        }
    }

    private suspend fun releaseWaiter(
        key: RequestKey,
        request: InFlightRequest,
    ) {
        withContext(NonCancellable) {
            requestsMutex.withLock {
                if (inFlight[key] !== request) return@withLock
                request.waiterCount--
                if (request.waiterCount == 0) {
                    inFlight.remove(key)
                    request.deferred.cancel()
                }
            }
        }
    }

    private suspend fun fetchAndCache(
        key: EmotionRegionQueryKey,
        queryBounds: EmotionMapBounds,
        level: EmotionRegionLevel,
        filter: EmotionSearchFilter?,
        generation: Generation,
    ): EmotionRegionResult =
        try {
            when (val result = api.findRegions(queryBounds, level, filter)) {
                is ApiResult.Failure -> {
                    EmotionRegionResult.Failure
                }

                is ApiResult.Success -> {
                    val fetchedAt = Clock.System.now().toEpochMilliseconds()
                    val regions = result.value.map(::parseRegion)
                    if (regions.any { it == null }) {
                        EmotionRegionResult.Failure
                    } else {
                        val completeRegions = regions.filterNotNull()
                        requestsMutex.withLock {
                            cache.put(key, completeRegions, generation, fetchedAt)
                        }
                        EmotionRegionResult.Success(completeRegions)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            EmotionRegionResult.Failure
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

    private fun newRequestsScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun rotateRequestsScope() {
        val replacement = newRequestsScope()
        while (true) {
            val previous = requestsScope.value
            if (requestsScope.compareAndSet(previous, replacement)) {
                previous.coroutineContext[Job]?.cancel()
                return
            }
        }
    }
}
