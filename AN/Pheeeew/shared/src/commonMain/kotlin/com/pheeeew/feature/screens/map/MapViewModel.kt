package com.pheeeew.feature.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapViewport
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.monitoring.product.MonitoredLocationPermission
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.screens.map.monitoring.ContentLoad
import com.pheeeew.feature.screens.map.monitoring.ContentMonitoring
import com.pheeeew.feature.screens.map.nearby.NEARBY_COLLAPSED_MAP_HEIGHT_FRACTION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

class MapViewModel(
    private val refreshLocation: RefreshLocationUseCase,
    private val findEmotionMapPage: FindEmotionMapPageUseCase,
    private val findEmotionMapSnapshot: FindEmotionMapSnapshotUseCase,
    monitoring: Monitoring = NoOpMonitoring,
    private val findRegions: FindEmotionRegionsUseCase? = null,
    private val findRegionSnapshot: FindEmotionRegionSnapshotUseCase? = null,
) : ViewModel() {
    private val _uiModel = MutableStateFlow(MapUiModel())
    val uiModel: StateFlow<MapUiModel> = _uiModel.asStateFlow()

    private val telemetry = ProductMonitoring(monitoring, "map")
    private var mapLoad: com.pheeeew.feature.monitoring.product.ProductOperation? = null
    val exploration = ContentMonitoring(monitoring, viewModelScope, "map")

    fun contentVisibility(visible: Boolean) {
        if (exploration.visibility(visible) && _uiModel.value.emotionContentLoad != null) {
            val load =
                exploration
                    .load(
                        "resume",
                        "cache",
                        _uiModel.value.emotionContentLoad?.loadId,
                    ).also { it.ready() }
            _uiModel.value = _uiModel.value.copy(emotionContentLoad = load)
        }
    }

    override fun onCleared() {
        exploration.close()
        super.onCleared()
    }

    fun contentPresented(
        loadId: String,
        entryIds: List<String>,
    ) {
        _uiModel.value.emotionContentLoad?.takeIf { it.loadId == loadId }?.let {
            val ids = entryIds.mapNotNull { id -> id.toLongOrNull() }.distinct()
            exploration.presented(it, ids.size)
            ids.forEach { id -> exploration.itemVisible(id) }
        }
    }

    private var hasStarted = false
    private var locationRequestJob: Job? = null
    private var nextCameraCommandId = 0L
    private var viewportJob: Job? = null
    private var regionJob: Job? = null
    private var latestViewport: EmotionMapViewport? = null
    private var activeRegionQuery: Pair<EmotionMapBounds, EmotionRegionLevel>? = null
    private var emotionMapJob: Job? = null
    private var queryGeneration = 0L
    private var activeEmotionMapRequestId = 0L
    private var pendingEmotionMapRequest: EmotionMapRequest? = null
    private var needsFreshEmotionPins = false
    private var requestedBounds: EmotionMapBounds? = null
    private var displayedBounds: EmotionMapBounds? = null
    private var retryCursor: String? = null
    private var displayedPins = linkedMapOf<Long, EmotionMapPin>()
    private var partialPageBounds: EmotionMapBounds? = null
    private var partialPagePins = linkedMapOf<Long, EmotionMapPin>()
    private val locallyRegisteredPins = linkedMapOf<Long, EmotionPinUiModel>()

    fun start() {
        if (hasStarted) return
        hasStarted = true
        requestCurrentLocation(moveCamera = false, requestPermission = true)
    }

    fun dismissLocationPermissionDialog() {
        _uiModel.value = _uiModel.value.copy(showLocationPermissionDialog = false)
    }

    fun onEmotionRegistered(pin: EmotionPinUiModel) {
        locallyRegisteredPins[pin.id] = pin
        onRecordLocationPickingChanged(false)
        _uiModel.value =
            _uiModel.value.copy(
                emotionPins =
                    (uiModel.value.emotionPins.filterNot { it.id == pin.id } + pin)
                        .sortedWith(compareBy<EmotionPinUiModel> { Instant.parse(it.createdAt) }.thenBy { it.id }),
            )
        refreshEmotionPins()
    }

    fun onEmotionHidden(id: Long) {
        locallyRegisteredPins.remove(id)
        _uiModel.value =
            _uiModel.value.copy(
                hiddenEmotionIds = _uiModel.value.hiddenEmotionIds + id,
                focusedEmotionId = _uiModel.value.focusedEmotionId.takeUnless { it == id },
            )
    }

    fun onMyLocationClick(fromUser: Boolean = true) {
        if (fromUser) telemetry.emit("map_location_requested")
        requestCurrentLocation(moveCamera = true, requestPermission = true)
    }

    fun focusOnEmotion(
        id: Long,
        coordinate: GeoCoordinate? = null,
    ): Boolean {
        if (id in _uiModel.value.hiddenEmotionIds) return false
        val target =
            coordinate ?: _uiModel.value.emotionPins.firstOrNull { it.id == id }?.let {
                GeoCoordinate(it.latitude, it.longitude)
            } ?: return false
        // Place the pin slightly below the visible map's midpoint.
        focusOnCoordinate(target, verticalPosition = NEARBY_COLLAPSED_MAP_HEIGHT_FRACTION.toDouble() * 0.6)
        _uiModel.value = _uiModel.value.copy(focusedEmotionId = id)
        return true
    }

    fun focusOnRegionCluster(id: String) {
        val region = _uiModel.value.regionClusters.firstOrNull { it.id == id } ?: return
        val displayedLevel = _uiModel.value.displayedRegionLevel ?: return
        val zoom = MapZoomPolicy.focusZoomForRegion(displayedLevel)
        sendCameraCommand(
            action = MapCameraActionUiModel.MoveToCoordinate,
            latitude = region.latitude,
            longitude = region.longitude,
            value = zoom,
        )
    }

    fun clearFocusedEmotion() {
        _uiModel.value = _uiModel.value.copy(focusedEmotionId = null)
    }

    fun focusOnCoordinate(
        coordinate: GeoCoordinate,
        verticalPosition: Double = 0.5,
    ) {
        sendCameraCommand(
            action = MapCameraActionUiModel.MoveToCoordinate,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            value = LOCATION_FOCUS_ZOOM,
            verticalPosition = verticalPosition,
        )
    }

    fun onEmotionSelectorOpen() {
        _uiModel.value = _uiModel.value.copy(isEmotionSelectorExpanded = true)
    }

    fun onEmotionSelectorToggle() {
        _uiModel.value =
            _uiModel.value.copy(isEmotionSelectorExpanded = !_uiModel.value.isEmotionSelectorExpanded)
    }

    fun onEmotionBubbleSelected() {
        _uiModel.value = _uiModel.value.copy(isEmotionSelectorExpanded = false)
    }

    fun onRecordLocationPickingChanged(isPicking: Boolean) {
        _uiModel.value = _uiModel.value.copy(isRecordLocationPicking = isPicking)
        if (isPicking) {
            regionJob?.cancel()
            if (activeRegionQuery != null) {
                ++queryGeneration
                activeRegionQuery = null
            }
            _uiModel.value = _uiModel.value.copy(isLoadingRegionClusters = false)
        }
        // The renderer fits the 500m circle and uses that zoom as the zoom-out limit.
    }

    fun onViewportChanged(bounds: EmotionMapBounds) {
        if (!bounds.isValid()) return
        if (bounds == requestedBounds && (viewportJob?.isActive == true || emotionMapJob?.isActive == true)) return
        if (bounds == displayedBounds && emotionMapJob?.isActive != true) return
        requestedBounds = bounds
        retryCursor = null
        partialPageBounds = null
        partialPagePins.clear()
        exploration.supersede()
        publishSnapshot(bounds)
        val generation = ++queryGeneration
        viewportJob?.cancel()
        pendingEmotionMapRequest = null
        viewportJob =
            viewModelScope.launch {
                delay(VIEWPORT_DEBOUNCE_MILLIS)
                if (generation == queryGeneration) {
                    enqueueEmotionMapRequest(EmotionMapRequest(bounds, generation, bypassCache = needsFreshEmotionPins))
                }
            }
    }

    /** Region summaries occupy wide zooms; the existing paged pin flow owns close zooms. */
    fun onViewportChanged(viewport: EmotionMapViewport) {
        updateViewport(viewport, forceRegionRefresh = false)
    }

    private fun updateViewport(viewport: EmotionMapViewport, forceRegionRefresh: Boolean) {
        if (!viewport.isValid() || _uiModel.value.isRecordLocationPicking) return
        latestViewport = viewport
        val level = MapZoomPolicy.regionLevelForZoom(viewport.zoom)
        if (level == null || findRegions == null) {
            regionJob?.cancel()
            if (activeRegionQuery != null) {
                ++queryGeneration
                displayedBounds = null
            }
            activeRegionQuery = null
            _uiModel.value = _uiModel.value.copy(regionClusters = emptyList(), displayedRegionLevel = null, regionClustersError = null, isLoadingRegionClusters = false)
            onViewportChanged(viewport.bounds)
            return
        }
        val query = viewport.bounds to level
        if (!forceRegionRefresh && query == activeRegionQuery && (regionJob?.isActive == true || _uiModel.value.regionClustersError == null)) return
        activeRegionQuery = query
        val generation = ++queryGeneration
        viewportJob?.cancel()
        emotionMapJob?.cancel()
        regionJob?.cancel()
        pendingEmotionMapRequest = null
        requestedBounds = null
        val cached = if (forceRegionRefresh) null else findRegionSnapshot?.invoke(viewport.bounds, level)
        _uiModel.value = _uiModel.value.copy(
            emotionPins = emptyList(),
            regionClusters = cached?.toClusterUiModels() ?: _uiModel.value.regionClusters,
            displayedRegionLevel = if (cached != null) level else _uiModel.value.displayedRegionLevel,
            isLoadingEmotionPins = false,
            isLoadingMoreEmotionPins = false,
            isLoadingRegionClusters = false,
            emotionPinsError = null,
            regionClustersError = null,
        )
        if (cached != null) return
        regionJob = viewModelScope.launch {
            delay(VIEWPORT_DEBOUNCE_MILLIS)
            if (generation != queryGeneration) return@launch
            _uiModel.value = _uiModel.value.copy(isLoadingRegionClusters = true)
            val result = findRegions(viewport.bounds, level, forceRefresh = forceRegionRefresh)
            if (generation != queryGeneration || activeRegionQuery != query) return@launch
            _uiModel.value = when (result) {
                is EmotionRegionResult.Success -> _uiModel.value.copy(
                    regionClusters = result.regions.toClusterUiModels(),
                    displayedRegionLevel = level,
                    isLoadingRegionClusters = false,
                    regionClustersError = null,
                )
                EmotionRegionResult.Failure -> _uiModel.value.copy(
                    isLoadingRegionClusters = false,
                    regionClustersError = "지역별 감정을 불러오지 못했어요",
                )
            }
        }
    }

    fun retryRegionClusters() {
        val viewport = latestViewport ?: return
        updateViewport(viewport, forceRegionRefresh = true)
    }

    private fun List<EmotionRegion>.toClusterUiModels() = map {
        RegionClusterUiModel(it.id, it.name, it.longitude, it.latitude, it.count, it.representativeState?.toRegionUiEmotion())
    }

    fun refreshEmotionPins() {
        if (latestViewport?.zoom?.let(MapZoomPolicy::regionLevelForZoom) != null && findRegions != null) {
            retryRegionClusters()
            return
        }
        // A camera move may replace this request before it starts or finishes.
        needsFreshEmotionPins = true
        val bounds = requestedBounds ?: displayedBounds ?: return
        val generation = ++queryGeneration
        viewportJob?.cancel()
        pendingEmotionMapRequest = null
        enqueueEmotionMapRequest(EmotionMapRequest(bounds, generation, bypassCache = true))
    }

    fun retryEmotionPins() {
        val cursor = retryCursor
        if (cursor == null) {
            refreshEmotionPins()
            return
        }
        val bounds = displayedBounds ?: requestedBounds ?: return
        val generation = ++queryGeneration
        viewportJob?.cancel()
        pendingEmotionMapRequest = null
        enqueueEmotionMapRequest(
            EmotionMapRequest(
                bounds = bounds,
                generation = generation,
                startCursor = cursor,
                existingPins = LinkedHashMap(partialPagePins),
                bypassCache = true,
            ),
        )
    }

    private fun enqueueEmotionMapRequest(request: EmotionMapRequest) {
        if (request.generation != queryGeneration) return
        if (emotionMapJob?.isActive == true) {
            pendingEmotionMapRequest = request
        } else {
            pendingEmotionMapRequest = null
            loadEmotionPins(request)
        }
    }

    private fun loadEmotionPins(request: EmotionMapRequest) {
        val requestId = ++activeEmotionMapRequestId
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                _uiModel.value =
                    _uiModel.value.copy(
                        isLoadingEmotionPins = true,
                        isLoadingMoreEmotionPins = false,
                        hasPartialEmotionPins = false,
                        emotionPinsError = null,
                    )
                val pins = request.existingPins
                if (request.startCursor == null || partialPageBounds != request.bounds) {
                    partialPageBounds = request.bounds
                    partialPagePins = linkedMapOf()
                }
                val usedCursors = mutableSetOf<String>()
                var cursor: String? = request.startCursor
                cursor?.let(usedCursors::add)
                var firstPage = request.startCursor == null
                var invalidCount = if (firstPage) 0 else _uiModel.value.invalidEmotionPinCount
                var observation: ContentLoad? = null
                try {
                    while (true) {
                        if (request.generation != queryGeneration) return@launch
                        observation =
                            exploration.load(
                                if (cursor != null) {
                                    "pagination"
                                } else if (request.bypassCache) {
                                    "refresh"
                                } else {
                                    "viewport"
                                },
                            )
                        when (
                            val result =
                                findEmotionMapPage(
                                    request.bounds,
                                    cursor = cursor,
                                    forceRefresh = request.bypassCache && cursor == null,
                                )
                        ) {
                            is EmotionMapPageResult.Failure -> {
                                observation.failed()
                                if (request.generation == queryGeneration) {
                                    retryCursor = cursor
                                    partialPageBounds = request.bounds
                                    partialPagePins = LinkedHashMap(pins)
                                    _uiModel.value =
                                        _uiModel.value.copy(
                                            isLoadingEmotionPins = false,
                                            isLoadingMoreEmotionPins = false,
                                            hasPartialEmotionPins = !firstPage,
                                            emotionPinsError = "감정 핀을 불러오지 못했어요",
                                        )
                                }
                                return@launch
                            }

                            is EmotionMapPageResult.Success -> {
                                val page = result.page
                                val origin = if (result.fromCache) "cache" else "network"
                                invalidCount += page.invalidItemCount
                                if (request.generation != queryGeneration) {
                                    observation.hidden()
                                    observation.ready(origin)
                                    if (!_uiModel.value.isRecordLocationPicking) {
                                        requestedBounds?.let(::publishSnapshot)
                                    }
                                    return@launch
                                }
                                page.pins.forEach { pins[it.id] = it }
                                partialPageBounds = request.bounds
                                partialPagePins = LinkedHashMap(pins)
                                _uiModel.value.emotionContentLoad?.hidden()
                                val displayLoad = exploration.display(observation, origin)
                                displayedBounds = request.bounds
                                page.pins.forEach { locallyRegisteredPins.remove(it.id) }
                                val isComplete = !page.hasNext
                                val pinsForDisplay =
                                    if (isComplete) {
                                        pins.filterValues { request.bounds.contains(it) }
                                    } else {
                                        LinkedHashMap(displayedPins).apply { putAll(pins) }
                                    }
                                displayedPins = LinkedHashMap(pinsForDisplay)
                                val ordered = pinsForDisplay.values.toOrderedUiPins(request.bounds)
                                _uiModel.value =
                                    _uiModel.value.copy(
                                        emotionPins = ordered,
                                        emotionContentLoad = displayLoad,
                                        isLoadingEmotionPins = false,
                                        isLoadingMoreEmotionPins = page.hasNext,
                                        hasPartialEmotionPins = false,
                                        invalidEmotionPinCount = invalidCount,
                                        emotionPinsError = null,
                                    )
                                firstPage = false
                                if (isComplete) {
                                    if (request.bypassCache) needsFreshEmotionPins = false
                                    retryCursor = null
                                    partialPageBounds = null
                                    partialPagePins.clear()
                                    break
                                }
                                val nextCursor = page.nextCursor
                                if (nextCursor.isNullOrBlank() || !usedCursors.add(nextCursor)) {
                                    retryCursor = null
                                    _uiModel.value =
                                        _uiModel.value.copy(
                                            isLoadingMoreEmotionPins = false,
                                            hasPartialEmotionPins = true,
                                            emotionPinsError = "감정 핀 목록을 완전히 불러오지 못했어요",
                                        )
                                    return@launch
                                }
                                cursor = nextCursor
                                retryCursor = cursor
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    observation?.cancelled()
                    throw cancellation
                } catch (_: Exception) {
                    observation?.failed()
                    if (request.generation == queryGeneration) {
                        retryCursor = cursor
                        partialPageBounds = request.bounds
                        partialPagePins = LinkedHashMap(pins)
                        _uiModel.value =
                            _uiModel.value.copy(
                                isLoadingEmotionPins = false,
                                isLoadingMoreEmotionPins = false,
                                hasPartialEmotionPins = !firstPage,
                                emotionPinsError = "감정 핀을 불러오지 못했어요",
                            )
                    }
                } finally {
                    if (request.generation == queryGeneration && !_uiModel.value.hasPartialEmotionPins) {
                        retryCursor = null
                    }
                    if (request.generation == queryGeneration && _uiModel.value.isLoadingEmotionPins) {
                        _uiModel.value = _uiModel.value.copy(isLoadingEmotionPins = false)
                    }
                    if (requestId == activeEmotionMapRequestId) {
                        emotionMapJob = null
                        val pendingRequest = pendingEmotionMapRequest
                        if (pendingRequest != null) {
                            pendingEmotionMapRequest = null
                            enqueueEmotionMapRequest(pendingRequest)
                        }
                    }
                }
            }
        emotionMapJob = job
        job.start()
    }

    private fun publishSnapshot(bounds: EmotionMapBounds) {
        val snapshotPins = findEmotionMapSnapshot(bounds)?.pins ?: return
        displayedPins =
            LinkedHashMap(displayedPins).apply {
                snapshotPins.forEach { put(it.id, it) }
            }
        _uiModel.value.emotionContentLoad?.hidden()
        val load = exploration.load("cache", "cache").also { it.ready() }
        _uiModel.value =
            _uiModel.value.copy(
                emotionPins = displayedPins.values.toOrderedUiPins(bounds),
                emotionContentLoad = load,
            )
    }

    private fun Collection<EmotionMapPin>.toOrderedUiPins(bounds: EmotionMapBounds): List<EmotionPinUiModel> =
        (map { it.toUiModel() } + locallyRegisteredPins.values.filter { bounds.contains(it) })
            .distinctBy { it.id }
            .filterNot { it.id in _uiModel.value.hiddenEmotionIds }
            .sortedWith(compareBy<EmotionPinUiModel> { Instant.parse(it.createdAt) }.thenBy { it.id })

    private fun EmotionMapBounds.contains(pin: EmotionPinUiModel): Boolean =
        pin.latitude in minLatitude..maxLatitude &&
            if (minLongitude <= maxLongitude) {
                pin.longitude in minLongitude..maxLongitude
            } else {
                pin.longitude >= minLongitude || pin.longitude <= maxLongitude
            }

    private fun EmotionMapBounds.contains(pin: EmotionMapPin): Boolean =
        pin.latitude in minLatitude..maxLatitude &&
            if (minLongitude <= maxLongitude) {
                pin.longitude in minLongitude..maxLongitude
            } else {
                pin.longitude >= minLongitude || pin.longitude <= maxLongitude
            }

    fun onConnectivityChanged(connected: Boolean) {
        val wasOffline = _uiModel.value.isOffline
        _uiModel.value = _uiModel.value.copy(isOffline = !connected)
        if (connected && wasOffline) {
            if (_uiModel.value.mapError != null) retryMap()
            refreshEmotionPins()
        }
    }

    fun onMapRendererAttached() {
        mapLoad?.finish("cancelled")
        mapLoad = telemetry.operation("map_load_finished")
    }

    fun onMapError(error: MapErrorUiModel) {
        if (_uiModel.value.mapError == error) return
        mapLoad?.finish("failed")
        _uiModel.value = _uiModel.value.copy(mapError = error)
    }

    fun onMapRecovered() {
        mapLoad?.finish("success")
        _uiModel.value = _uiModel.value.copy(mapError = null)
    }

    fun retryMap() {
        mapLoad?.finish("cancelled")
        mapLoad = telemetry.operation("map_load_finished")
        _uiModel.value =
            _uiModel.value.copy(
                mapError = null,
                mapRevision = _uiModel.value.mapRevision + 1,
            )
    }

    private fun requestCurrentLocation(
        moveCamera: Boolean,
        requestPermission: Boolean,
    ) {
        if (locationRequestJob?.isActive == true) return
        locationRequestJob =
            viewModelScope.launch {
                _uiModel.value = _uiModel.value.copy(isRequestingLocation = true, locationError = null)
                try {
                    val locationState =
                        telemetry.operation("location_acquire_finished").observe({
                            if (it is LocationState.Available) "success" else "unavailable"
                        }) { refreshLocation(requestPermission) }
                    _uiModel.value =
                        _uiModel.value.copy(
                            locationState = locationState,
                            locationError = (locationState as? LocationState.Unavailable)?.reason.takeIf { moveCamera },
                            showLocationPermissionDialog =
                                requestPermission &&
                                    locationState == LocationState.Unavailable(LocationError.PermissionDenied),
                        )
                    val location = (locationState as? LocationState.Available)?.location
                    if (moveCamera && location != null) {
                        sendCameraCommand(
                            action = MapCameraActionUiModel.MoveToCoordinate,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            value = LOCATION_FOCUS_ZOOM,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiModel.value =
                        _uiModel.value.copy(
                            locationState = LocationState.Unavailable(LocationError.GpsUnavailable),
                            locationError = LocationError.GpsUnavailable.takeIf { moveCamera },
                        )
                } finally {
                    _uiModel.value = _uiModel.value.copy(isRequestingLocation = false)
                }
            }
    }

    private fun sendCameraCommand(
        action: MapCameraActionUiModel,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        value: Double,
        verticalPosition: Double = 0.5,
    ) {
        _uiModel.value =
            _uiModel.value.copy(
                cameraCommand =
                    MapCameraCommandUiModel(
                        id = ++nextCameraCommandId,
                        action = action,
                        latitude = latitude,
                        longitude = longitude,
                        value = value,
                        verticalPosition = verticalPosition,
                    ),
            )
    }

    companion object {
        private const val LOCATION_FOCUS_ZOOM = 15.5

        fun create(
            locationDependencies: LocationDependencies,
            findEmotionMapPage: FindEmotionMapPageUseCase,
            findEmotionMapSnapshot: FindEmotionMapSnapshotUseCase,
            monitoring: Monitoring = NoOpMonitoring,
            findRegions: FindEmotionRegionsUseCase? = null,
            findRegionSnapshot: FindEmotionRegionSnapshotUseCase? = null,
        ): MapViewModel =
            MapViewModel(
                refreshLocation =
                    RefreshLocationUseCase(
                        permissionController =
                            MonitoredLocationPermission(
                                locationDependencies.permissionController,
                                monitoring,
                            ),
                        repository = locationDependencies.repository,
                    ),
                findEmotionMapPage = findEmotionMapPage,
                findEmotionMapSnapshot = findEmotionMapSnapshot,
                monitoring = monitoring,
                findRegions = findRegions,
                findRegionSnapshot = findRegionSnapshot,
            )

        private const val VIEWPORT_DEBOUNCE_MILLIS = 700L
    }
}

private data class EmotionMapRequest(
    val bounds: EmotionMapBounds,
    val generation: Long,
    val startCursor: String? = null,
    val existingPins: LinkedHashMap<Long, EmotionMapPin> = linkedMapOf(),
    val bypassCache: Boolean = false,
)
