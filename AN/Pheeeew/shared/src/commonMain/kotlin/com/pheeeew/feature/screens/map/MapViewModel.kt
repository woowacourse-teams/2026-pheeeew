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
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionMapViewport
import com.pheeeew.domain.model.emotion.EmotionRegion
import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.domain.model.emotion.EmotionRegionResult
import com.pheeeew.domain.model.emotion.EmotionSearchFilter
import com.pheeeew.domain.model.emotion.MapFilterContent
import com.pheeeew.domain.model.emotion.MapFilterPeriod
import com.pheeeew.domain.model.emotion.MapFilterScope
import com.pheeeew.domain.model.emotion.MapFilterSettings
import com.pheeeew.domain.model.emotion.SearchDate
import com.pheeeew.domain.model.emotion.coversRegionViewport
import com.pheeeew.domain.model.emotion.expandedForRegionQuery
import com.pheeeew.domain.repository.MapFilterSettingsRepository
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionSnapshotUseCase
import com.pheeeew.domain.usecase.FindEmotionRegionsUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import com.pheeeew.feature.monitoring.product.MonitoredLocationPermission
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.ProductOperation
import com.pheeeew.feature.screens.map.filter.FilterContent
import com.pheeeew.feature.screens.map.filter.FilterDate
import com.pheeeew.feature.screens.map.filter.FilterPage
import com.pheeeew.feature.screens.map.filter.FilterPeriod
import com.pheeeew.feature.screens.map.filter.FilterScope
import com.pheeeew.feature.screens.map.filter.MapFilterListRefresher
import com.pheeeew.feature.screens.map.filter.MapFilterUiModel
import com.pheeeew.feature.screens.map.monitoring.ContentLoad
import com.pheeeew.feature.screens.map.monitoring.ContentMonitoring
import com.pheeeew.feature.screens.map.nearby.NEARBY_COLLAPSED_MAP_HEIGHT_FRACTION
import com.pheeeew.feature.screens.map.record.group.toSelectorUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

class MapViewModel(
    private val refreshLocation: RefreshLocationUseCase,
    private val findEmotionMapPage: FindEmotionMapPageUseCase,
    private val findEmotionMapSnapshot: FindEmotionMapSnapshotUseCase,
    monitoring: Monitoring = NoOpMonitoring,
    private val findRegions: FindEmotionRegionsUseCase? = null,
    private val findRegionSnapshot: FindEmotionRegionSnapshotUseCase? = null,
    private val groupStampListRepository: GroupStampListRepository? = null,
    private val filterSettingsRepository: MapFilterSettingsRepository? = null,
    private val filterListRefresher: MapFilterListRefresher? = null,
    private val pinSnapshotIntervalMillis: Long = MapPinSnapshotPublisher.DEFAULT_INTERVAL_MILLIS,
    private val pinSnapshotDelay: suspend (Long) -> Unit = { delay(it) },
) : ViewModel() {
    private val _uiModel = MutableStateFlow(MapUiModel())
    val uiModel: StateFlow<MapUiModel> = _uiModel.asStateFlow()
    private val _filterUiModel = MutableStateFlow(MapFilterUiModel())
    val filterUiModel: StateFlow<MapFilterUiModel> = _filterUiModel.asStateFlow()
    private var appliedFilterSettings = MapFilterSettings.DEFAULT
    private var filterSettingsLoaded = filterSettingsRepository == null
    private var filterApplyJob: Job? = null

    init {
        filterSettingsRepository?.let { repository ->
            viewModelScope.launch {
                val settings =
                    try {
                        repository.read()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        MapFilterSettings.DEFAULT
                    }
                appliedFilterSettings = settings
                filterListRefresher?.setAppliedSettings(settings)
                _filterUiModel.value = settings.toUiModel(_filterUiModel.value)
                filterSettingsLoaded = true
                latestViewport?.let { updateViewport(it, forceRegionRefresh = false) }
                    ?: requestedBounds?.let(::onViewportChanged)
            }
        }
    }

    fun openFilter() {
        _filterUiModel.value = _filterUiModel.value.copy(visible = true, page = FilterPage.Filters)
        if (_filterUiModel.value.scope == FilterScope.Groups && _filterUiModel.value.groups.isEmpty()) {
            loadFilterGroups()
        }
    }

    fun dismissFilter() {
        val wasApplying = _filterUiModel.value.applying
        filterApplyJob?.cancel()
        filterApplyJob = null
        _filterUiModel.value =
            appliedFilterSettings.toUiModel(_filterUiModel.value).copy(
                visible = false,
                page = FilterPage.Filters,
                applying = false,
                applyError = null,
            )
        if (wasApplying) {
            latestViewport?.let { updateViewport(it, forceRegionRefresh = true) }
                ?: requestedBounds?.let(::onViewportChanged)
        }
    }

    fun resetFilter() {
        _filterUiModel.value = MapFilterUiModel(visible = true, groups = _filterUiModel.value.groups)
    }

    fun selectFilterPeriod(period: FilterPeriod) {
        _filterUiModel.value = _filterUiModel.value.copy(period = period, applyError = null)
    }

    fun selectFilterContent(content: FilterContent) {
        _filterUiModel.value = _filterUiModel.value.copy(content = content, applyError = null)
    }

    fun selectFilterScope(scope: FilterScope) {
        _filterUiModel.value = _filterUiModel.value.copy(scope = scope, applyError = null)
        if (scope == FilterScope.Groups) openFilterGroups()
    }

    fun openFilterDate(start: Boolean) {
        _filterUiModel.value =
            _filterUiModel.value.copy(
                page = if (start) FilterPage.StartDate else FilterPage.EndDate,
                applyError = null,
            )
    }

    fun confirmFilterDate(date: FilterDate) {
        val state = _filterUiModel.value
        _filterUiModel.value =
            if (state.page == FilterPage.StartDate) {
                state.copy(
                    startDate = date,
                    endDate = state.endDate?.takeIf { it >= date },
                    page = FilterPage.Filters,
                    applyError = null,
                )
            } else {
                state.copy(
                    endDate = date,
                    startDate = state.startDate?.takeIf { it <= date },
                    page = FilterPage.Filters,
                    applyError = null,
                )
            }
    }

    fun openFilterGroups() {
        _filterUiModel.value = _filterUiModel.value.copy(page = FilterPage.Groups)
        loadFilterGroups()
    }

    private fun loadFilterGroups() {
        val repository = groupStampListRepository ?: return
        _filterUiModel.value = _filterUiModel.value.copy(loadingGroups = true, groupLoadFailed = false)
        viewModelScope.launch {
            val result =
                try {
                    repository.findMyStamps()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GroupStampListLoadResult.Unavailable
                }
            val current = _filterUiModel.value
            _filterUiModel.value =
                when (result) {
                    is GroupStampListLoadResult.Loaded -> {
                        current.copy(
                            groups = result.groups.map { it.toSelectorUiModel() },
                            selectedGroupIds =
                                current.selectedGroupIds.intersect(
                                    result.groups.map { it.id.value }.toSet(),
                                ),
                            loadingGroups = false,
                        )
                    }

                    GroupStampListLoadResult.Unavailable -> {
                        current.copy(loadingGroups = false, groupLoadFailed = true)
                    }
                }
        }
    }

    fun toggleFilterGroup(id: String) {
        val state = _filterUiModel.value
        if (state.groups.none { it.id == id }) return
        _filterUiModel.value = state.copy(selectedGroupIds = setOf(id), applyError = null)
    }

    fun closeFilterPage() {
        _filterUiModel.value = _filterUiModel.value.copy(page = FilterPage.Filters)
    }

    fun applyFilter() {
        if (_filterUiModel.value.applying) return
        val candidate = _filterUiModel.value.toSettingsOrNull()
        if (candidate == null) {
            _filterUiModel.value =
                _filterUiModel.value.copy(
                    applyError = "기간과 조회 범위에 필요한 값을 모두 선택해 주세요",
                )
            return
        }
        val bounds =
            (latestViewport?.bounds ?: pinViewportBounds)?.expandedForPinQuery() ?: requestedBounds ?: displayedBounds
        if (bounds == null) {
            _filterUiModel.value =
                _filterUiModel.value.copy(applyError = "지도 영역을 확인하고 다시 시도해 주세요")
            return
        }
        filterApplyJob?.cancel()
        _filterUiModel.value = _filterUiModel.value.copy(applying = true, applyError = null)
        filterApplyJob =
            viewModelScope.launch {
                val previousGeneration = queryGeneration
                queryGeneration++
                pinSnapshotPublisher.begin(queryGeneration)
                viewportJob?.cancel()
                emotionMapJob?.cancel()
                regionJob?.cancel()
                pendingEmotionMapRequest = null
                activeRegionQuery = null
                val searchFilter = candidate.toSearchFilter()
                try {
                    val pins = loadFilteredPins(bounds, searchFilter)
                    if (pins == null) {
                        resumeAppliedMapAfterFilterFailure()
                        _filterUiModel.value =
                            _filterUiModel.value.copy(
                                applying = false,
                                applyError = "지도 핀을 불러오지 못했어요. 다시 시도해 주세요",
                            )
                        return@launch
                    }
                    currentCoroutineContext().ensureActive()
                    val nearby = filterListRefresher?.takeIf { it.isVisible() }
                    val listPage =
                        nearby?.loadFirstPage(searchFilter)?.let { result ->
                            when (result) {
                                is EmotionResult.Success -> {
                                    result.value
                                }

                                is EmotionResult.Failure -> {
                                    resumeAppliedMapAfterFilterFailure()
                                    _filterUiModel.value =
                                        _filterUiModel.value.copy(
                                            applying = false,
                                            applyError =
                                                "감정 목록을 불러오지 못했어요. 다시 시도해 주세요",
                                        )
                                    return@launch
                                }
                            }
                        }
                    currentCoroutineContext().ensureActive()
                    filterSettingsRepository?.save(candidate)
                    currentCoroutineContext().ensureActive()
                    appliedFilterSettings = candidate
                    needsFreshEmotionPins = false
                    displayedBounds = bounds
                    requestedBounds = bounds
                    pinViewportBounds = latestViewport?.bounds ?: pinViewportBounds ?: bounds
                    displayedPins = pins
                    partialPageBounds = null
                    partialPagePins.clear()
                    retryCursor = null
                    locallyRegisteredPins.clear()
                    _uiModel.value =
                        _uiModel.value.copy(
                            regionClusters = emptyList(),
                            displayedRegionLevel = null,
                            isLoadingEmotionPins = false,
                            isLoadingMoreEmotionPins = false,
                            isLoadingRegionClusters = false,
                            hasPartialEmotionPins = false,
                            invalidEmotionPinCount = 0,
                            emotionPinsError = null,
                        )
                    pinSnapshotPublisher.finish(
                        queryGeneration,
                        EmotionPinSnapshot(
                            generation = queryGeneration,
                            pins = pins.values.toList(),
                            kind = "refresh",
                            origin = "network",
                            observation = null,
                        ),
                    )
                    if (nearby != null && listPage != null) {
                        nearby.commitFirstPage(candidate, listPage)
                    } else {
                        filterListRefresher?.setAppliedSettings(candidate)
                    }
                    _filterUiModel.value =
                        candidate.toUiModel(_filterUiModel.value).copy(
                            visible = false,
                            applying = false,
                            page = FilterPage.Filters,
                        )
                    filterApplyJob = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    resumeAppliedMapAfterFilterFailure()
                    if (queryGeneration > previousGeneration && _filterUiModel.value.visible) {
                        _filterUiModel.value =
                            _filterUiModel.value.copy(
                                applying = false,
                                applyError = "필터를 적용하지 못했어요. 다시 시도해 주세요",
                            )
                    }
                } finally {
                    if (filterApplyJob === currentCoroutineContext()[Job] &&
                        _filterUiModel.value.visible && _filterUiModel.value.applying
                    ) {
                        _filterUiModel.value = _filterUiModel.value.copy(applying = false)
                    }
                }
            }
    }

    private fun resumeAppliedMapAfterFilterFailure() {
        val viewport = latestViewport
        if (viewport != null && !appliedFilterSettings.requiresFilteredPins() &&
            MapZoomPolicy.regionLevelForZoom(viewport.zoom) != null && findRegions != null
        ) {
            updateViewport(viewport, forceRegionRefresh = true)
            return
        }
        val bounds = requestedBounds ?: displayedBounds ?: viewport?.bounds ?: return
        needsFreshEmotionPins = true
        val generation = ++queryGeneration
        viewportJob?.cancel()
        pendingEmotionMapRequest = null
        enqueueEmotionMapRequest(EmotionMapRequest(bounds, generation, bypassCache = true))
    }

    private suspend fun loadFilteredPins(
        bounds: EmotionMapBounds,
        filter: EmotionSearchFilter,
    ): LinkedHashMap<Long, EmotionMapPin>? {
        val pins = linkedMapOf<Long, EmotionMapPin>()
        val usedCursors = mutableSetOf<String>()
        var cursor: String? = null
        while (true) {
            when (val result = findEmotionMapPage.withFilter(bounds, filter, cursor, forceRefresh = cursor == null)) {
                is EmotionMapPageResult.Failure -> {
                    return null
                }

                is EmotionMapPageResult.Success -> {
                    result.page.pins.forEach { pins[it.id] = it }
                    if (!result.page.hasNext) return pins
                    val next = result.page.nextCursor?.takeIf { it.isNotBlank() } ?: return null
                    if (!usedCursors.add(next)) return null
                    cursor = next
                }
            }
        }
    }

    private val telemetry = ProductMonitoring(monitoring, "map")
    private val pinUiModelCache = EmotionPinUiModelCache()
    private val pinSnapshotPublisher =
        MapPinSnapshotPublisher(
            scope = viewModelScope,
            intervalMillis = pinSnapshotIntervalMillis,
            wait = pinSnapshotDelay,
            sameSnapshot = { first, second -> first.pins == second.pins },
            publish = ::publishEmotionPinSnapshot,
            discard = { it.observation?.hidden() },
        )
    private var mapLoad: ProductOperation? = null
    val exploration = ContentMonitoring(monitoring, viewModelScope, "map")

    fun contentVisibility(visible: Boolean) {
        val visibilityChanged = exploration.visibility(visible)
        if (!visible) pinSnapshotPublisher.setActive(false)
        if (visibilityChanged && visible && _uiModel.value.emotionContentLoad != null) {
            val load =
                exploration
                    .load(
                        "resume",
                        "cache",
                        _uiModel.value.emotionContentLoad?.loadId,
                    ).also { it.ready() }
            _uiModel.value = _uiModel.value.copy(emotionContentLoad = load)
        }
        if (visible) pinSnapshotPublisher.setActive(true)
    }

    override fun onCleared() {
        pinSnapshotPublisher.cancel()
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
    private var pinViewportBounds: EmotionMapBounds? = null
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
                    pinUiModelCache.orderedModels(uiModel.value.emotionPins.filterNot { it.id == pin.id } + pin),
            )
        (pinViewportBounds ?: requestedBounds ?: displayedBounds)?.let(::trimRetainedPins)
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
        focusOnRegionCluster(region, displayedLevel)
    }

    /** A previous render snapshot remains clickable while replacement images are prepared. */
    fun focusOnRegionCluster(
        region: RegionClusterUiModel,
        level: EmotionRegionLevel,
    ) {
        val zoom = MapZoomPolicy.focusZoomForRegion(level)
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
        if (!filterSettingsLoaded) {
            requestedBounds = bounds
            return
        }
        pinViewportBounds = bounds
        trimRetainedPins(bounds)
        val queryBounds = bounds.expandedForPinQuery()
        if (queryBounds == requestedBounds && (viewportJob?.isActive == true || emotionMapJob?.isActive == true)) return
        if (queryBounds == displayedBounds && emotionMapJob?.isActive != true) return
        requestedBounds = queryBounds
        retryCursor = null
        partialPageBounds = null
        partialPagePins.clear()
        val generation = ++queryGeneration
        pinSnapshotPublisher.begin(generation)
        exploration.supersede()
        publishSnapshot(queryBounds, generation)
        viewportJob?.cancel()
        pendingEmotionMapRequest = null
        viewportJob =
            viewModelScope.launch {
                delay(VIEWPORT_DEBOUNCE_MILLIS)
                if (generation == queryGeneration) {
                    enqueueEmotionMapRequest(
                        EmotionMapRequest(queryBounds, generation, bypassCache = needsFreshEmotionPins),
                    )
                }
            }
    }

    /** Region summaries occupy wide zooms; the existing paged pin flow owns close zooms. */
    fun onViewportChanged(viewport: EmotionMapViewport) {
        updateViewport(viewport, forceRegionRefresh = false)
    }

    private fun updateViewport(
        viewport: EmotionMapViewport,
        forceRegionRefresh: Boolean,
    ) {
        if (!viewport.isValid() || _uiModel.value.isRecordLocationPicking) return
        latestViewport = viewport
        if (!filterSettingsLoaded) return
        val level = MapZoomPolicy.regionLevelForZoom(viewport.zoom)
        if (level == null || findRegions == null || appliedFilterSettings.requiresFilteredPins()) {
            regionJob?.cancel()
            if (activeRegionQuery != null) {
                ++queryGeneration
                pinSnapshotPublisher.begin(queryGeneration)
                displayedBounds = null
            }
            activeRegionQuery = null
            _uiModel.value =
                _uiModel.value.copy(
                    regionClustersError = null,
                    isLoadingRegionClusters = false,
                )
            onViewportChanged(viewport.bounds)
            return
        }
        val query = viewport.bounds.expandedForRegionQuery(level) to level
        val activeQuery = activeRegionQuery
        if (!forceRegionRefresh && regionJob?.isActive == true &&
            activeQuery != null && activeQuery.second == level &&
            activeQuery.first.coversRegionViewport(viewport.bounds)
        ) {
            return
        }
        activeRegionQuery = query
        val generation = ++queryGeneration
        pinSnapshotPublisher.begin(generation)
        viewportJob?.cancel()
        emotionMapJob?.cancel()
        regionJob?.cancel()
        exploration.supersede()
        pendingEmotionMapRequest = null
        requestedBounds = null
        val cached = if (forceRegionRefresh) null else findRegionSnapshot?.invoke(viewport.bounds, level)
        _uiModel.value =
            _uiModel.value.copy(
                emotionPins = if (cached != null) emptyList() else _uiModel.value.emotionPins,
                regionClusters = cached?.toClusterUiModels() ?: _uiModel.value.regionClusters,
                displayedRegionLevel = if (cached != null) level else _uiModel.value.displayedRegionLevel,
                isLoadingEmotionPins = false,
                isLoadingMoreEmotionPins = false,
                isLoadingRegionClusters = false,
                emotionPinsError = null,
                regionClustersError = null,
            )
        if (cached != null) return
        regionJob =
            viewModelScope.launch {
                delay(VIEWPORT_DEBOUNCE_MILLIS)
                if (generation != queryGeneration) return@launch
                _uiModel.value = _uiModel.value.copy(isLoadingRegionClusters = true)
                val result = findRegions(viewport.bounds, level, forceRefresh = forceRegionRefresh)
                if (generation != queryGeneration || activeRegionQuery != query) return@launch
                _uiModel.value =
                    when (result) {
                        is EmotionRegionResult.Success -> {
                            _uiModel.value.copy(
                                emotionPins = emptyList(),
                                regionClusters = result.regions.toClusterUiModels(),
                                displayedRegionLevel = level,
                                isLoadingRegionClusters = false,
                                regionClustersError = null,
                            )
                        }

                        EmotionRegionResult.Failure -> {
                            _uiModel.value.copy(
                                isLoadingRegionClusters = false,
                                regionClustersError = "지역별 감정을 불러오지 못했어요",
                            )
                        }
                    }
            }
    }

    fun retryRegionClusters() {
        val viewport = latestViewport ?: return
        updateViewport(viewport, forceRegionRefresh = true)
    }

    private fun List<EmotionRegion>.toClusterUiModels() =
        map {
            RegionClusterUiModel(
                it.id,
                it.name,
                it.longitude,
                it.latitude,
                it.count,
                it.representativeState?.toRegionUiEmotion(),
            )
        }

    fun refreshEmotionPins() {
        if (!appliedFilterSettings.requiresFilteredPins() &&
            latestViewport?.zoom?.let(MapZoomPolicy::regionLevelForZoom) != null && findRegions != null
        ) {
            retryRegionClusters()
            return
        }
        // A camera move may replace this request before it starts or finishes.
        needsFreshEmotionPins = true
        val bounds = requestedBounds ?: displayedBounds ?: return
        val generation = ++queryGeneration
        pinSnapshotPublisher.begin(generation)
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
        pinSnapshotPublisher.begin(generation)
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
        pinSnapshotPublisher.begin(request.generation)
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
                val searchFilter = appliedFilterSettings.toSearchFilter()
                var pageIndex = 0
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
                        pageIndex++
                        val requestMark = TimeSource.Monotonic.markNow()
                        when (
                            val result =
                                findEmotionMapPage.withFilter(
                                    request.bounds,
                                    searchFilter,
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
                                    pinSnapshotPublisher.flush(request.generation)
                                }
                                return@launch
                            }

                            is EmotionMapPageResult.Success -> {
                                val page = result.page
                                val origin = if (result.fromCache) "cache" else "network"
                                exploration.mapPinPageReceived(
                                    pageIndex = pageIndex,
                                    pinCount = page.pins.size,
                                    invalidPinCount = page.invalidItemCount,
                                    hasNext = page.hasNext,
                                    dataOrigin = origin,
                                    requestDurationMillis = requestMark.elapsedNow().inWholeMilliseconds,
                                )
                                invalidCount += page.invalidItemCount
                                if (request.generation != queryGeneration) {
                                    observation.ready(origin)
                                    observation.hidden()
                                    if (!_uiModel.value.isRecordLocationPicking) {
                                        requestedBounds?.let { publishSnapshot(it, queryGeneration) }
                                    }
                                    return@launch
                                }
                                page.pins.forEach { pins[it.id] = it }
                                partialPageBounds = request.bounds
                                partialPagePins = LinkedHashMap(pins)
                                displayedBounds = request.bounds
                                page.pins.forEach { locallyRegisteredPins.remove(it.id) }
                                val isComplete = !page.hasNext
                                val pinsForDisplay =
                                    LinkedHashMap(displayedPins).apply {
                                        // Only a complete response can remove deleted pins in its queried area.
                                        // Pins outside it remain available for small camera round trips.
                                        if (isComplete) entries.removeAll { request.bounds.contains(it.value) }
                                        putAll(pins.filterValues { request.bounds.contains(it) })
                                    }
                                observation.ready(origin)
                                val snapshot =
                                    EmotionPinSnapshot(
                                        generation = request.generation,
                                        pins = pinsForDisplay.values.toList(),
                                        kind =
                                            when {
                                                cursor != null -> "pagination"
                                                request.bypassCache -> "refresh"
                                                else -> "viewport"
                                            },
                                        origin = origin,
                                        observation = observation,
                                    )
                                _uiModel.value =
                                    _uiModel.value.copy(
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
                                    _uiModel.value =
                                        _uiModel.value.copy(
                                            isLoadingMoreEmotionPins = false,
                                            hasPartialEmotionPins = false,
                                        )
                                    pinSnapshotPublisher.finish(request.generation, snapshot)
                                    break
                                }
                                pinSnapshotPublisher.offer(
                                    forGeneration = request.generation,
                                    snapshot = snapshot,
                                    hasVisiblePins = page.pins.isNotEmpty(),
                                    hasNextPage = true,
                                )
                                val nextCursor = page.nextCursor
                                if (nextCursor.isNullOrBlank() || !usedCursors.add(nextCursor)) {
                                    retryCursor = null
                                    _uiModel.value =
                                        _uiModel.value.copy(
                                            isLoadingMoreEmotionPins = false,
                                            hasPartialEmotionPins = true,
                                            emotionPinsError = "감정 핀 목록을 완전히 불러오지 못했어요",
                                        )
                                    pinSnapshotPublisher.flush(request.generation)
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
                        pinSnapshotPublisher.flush(request.generation)
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

    private fun publishSnapshot(
        bounds: EmotionMapBounds,
        generation: Long = queryGeneration,
    ) {
        val snapshotPins =
            findEmotionMapSnapshot.withFilter(bounds, appliedFilterSettings.toSearchFilter())?.pins ?: return
        // An empty partial cache does not prove that the target pin query is empty.
        if (_uiModel.value.displayedRegionLevel != null && snapshotPins.isEmpty()) return
        val pins =
            LinkedHashMap(displayedPins).apply {
                snapshotPins.forEach { put(it.id, it) }
            }
        val published =
            EmotionPinSnapshot(
                generation = generation,
                pins = pins.values.toList(),
                kind = "cache",
                origin = "cache",
                observation = null,
            )
        pinSnapshotPublisher.seed(generation, published)
        publishEmotionPinSnapshot(published)
    }

    private fun publishEmotionPinSnapshot(snapshot: EmotionPinSnapshot) {
        if (snapshot.generation != queryGeneration) {
            snapshot.observation?.hidden()
            return
        }
        displayedPins = snapshot.pins.associateByTo(linkedMapOf(), EmotionMapPin::id)
        _uiModel.value.emotionContentLoad?.hidden()
        val contentLoad =
            snapshot.observation?.let { exploration.display(it, snapshot.origin) }
                ?: exploration.load(snapshot.kind, snapshot.origin).also { it.ready() }
        val bounds = pinViewportBounds ?: requestedBounds ?: displayedBounds
        val pins =
            if (bounds == null) {
                pinUiModelCache.orderedModels(
                    snapshot.pins.map(pinUiModelCache::toUiModel) + locallyRegisteredPins.values,
                )
            } else {
                pinUiModelCache.orderedPins(
                    pins = snapshot.pins,
                    localPins = locallyRegisteredPins.values,
                    hiddenIds = _uiModel.value.hiddenEmotionIds,
                    contains = { true },
                    containsLocal = { true },
                )
            }
        val retained = if (bounds == null) pins else retainEmotionPins(pins, bounds)
        pruneRetainedPinData(retained)
        _uiModel.value =
            _uiModel.value.copy(
                emotionPins = retained.takeUnless { it == _uiModel.value.emotionPins } ?: _uiModel.value.emotionPins,
                regionClusters = emptyList(),
                displayedRegionLevel = null,
                emotionContentLoad = contentLoad,
            )
    }

    private fun trimRetainedPins(bounds: EmotionMapBounds) {
        val previous = _uiModel.value.emotionPins
        val retained = retainEmotionPins(previous, bounds)
        if (retained == previous) return
        pruneRetainedPinData(retained)
        _uiModel.value = _uiModel.value.copy(emotionPins = retained)
    }

    private fun pruneRetainedPinData(pins: List<EmotionPinUiModel>) {
        val ids = pins.mapTo(mutableSetOf(), EmotionPinUiModel::id)
        displayedPins.keys.retainAll(ids)
        locallyRegisteredPins.keys.retainAll(ids)
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
            groupStampListRepository: GroupStampListRepository? = null,
            filterSettingsRepository: MapFilterSettingsRepository? = null,
            filterListRefresher: MapFilterListRefresher? = null,
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
                groupStampListRepository = groupStampListRepository,
                filterSettingsRepository = filterSettingsRepository,
                filterListRefresher = filterListRefresher,
            )

        private const val VIEWPORT_DEBOUNCE_MILLIS = 700L
    }
}

private fun MapFilterSettings.requiresFilteredPins(): Boolean {
    val filter = toSearchFilter()
    return filter.startDate != null || filter.content != MapFilterContent.ALL || filter.scope != MapFilterScope.ALL
}

private fun MapFilterSettings.toUiModel(current: MapFilterUiModel): MapFilterUiModel =
    current.copy(
        period =
            when (period) {
                MapFilterPeriod.TWO_WEEKS -> FilterPeriod.TwoWeeks
                MapFilterPeriod.ONE_MONTH -> FilterPeriod.OneMonth
                MapFilterPeriod.ALL -> FilterPeriod.All
                MapFilterPeriod.CUSTOM -> FilterPeriod.Custom
            },
        content =
            when (content) {
                MapFilterContent.ALL -> FilterContent.All
                MapFilterContent.MEMO -> FilterContent.Memo
                MapFilterContent.AUDIO -> FilterContent.Recording
            },
        scope =
            when (scope) {
                MapFilterScope.ALL -> FilterScope.All
                MapFilterScope.MY -> FilterScope.Mine
                MapFilterScope.GROUP -> FilterScope.Groups
            },
        startDate = customStartDate?.let { FilterDate(it.year, it.month, it.day) },
        endDate = customEndDate?.let { FilterDate(it.year, it.month, it.day) },
        selectedGroupIds = groupId?.let { setOf(it) } ?: emptySet(),
        applying = false,
        applyError = null,
    )

private fun MapFilterUiModel.toSettingsOrNull(): MapFilterSettings? {
    val start = startDate?.let { runCatching { SearchDate(it.year, it.month, it.day) }.getOrNull() }
    val end = endDate?.let { runCatching { SearchDate(it.year, it.month, it.day) }.getOrNull() }
    if (period == FilterPeriod.Custom && (start == null || end == null || start > end)) return null
    val selectedGroupId = selectedGroupIds.singleOrNull()
    if (scope == FilterScope.Groups && selectedGroupId == null) return null
    return runCatching {
        MapFilterSettings(
            period =
                when (period) {
                    FilterPeriod.TwoWeeks -> MapFilterPeriod.TWO_WEEKS
                    FilterPeriod.OneMonth -> MapFilterPeriod.ONE_MONTH
                    FilterPeriod.All -> MapFilterPeriod.ALL
                    FilterPeriod.Custom -> MapFilterPeriod.CUSTOM
                },
            customStartDate = if (period == FilterPeriod.Custom) start else null,
            customEndDate = if (period == FilterPeriod.Custom) end else null,
            content =
                when (content) {
                    FilterContent.All -> MapFilterContent.ALL
                    FilterContent.Memo -> MapFilterContent.MEMO
                    FilterContent.Recording -> MapFilterContent.AUDIO
                },
            scope =
                when (scope) {
                    FilterScope.All -> MapFilterScope.ALL
                    FilterScope.Mine -> MapFilterScope.MY
                    FilterScope.Groups -> MapFilterScope.GROUP
                },
            groupId = selectedGroupId.takeIf { scope == FilterScope.Groups },
        )
    }.getOrNull()
}

private data class EmotionMapRequest(
    val bounds: EmotionMapBounds,
    val generation: Long,
    val startCursor: String? = null,
    val existingPins: LinkedHashMap<Long, EmotionMapPin> = linkedMapOf(),
    val bypassCache: Boolean = false,
)

private data class EmotionPinSnapshot(
    val generation: Long,
    val pins: List<EmotionMapPin>,
    val kind: String,
    val origin: String,
    val observation: ContentLoad?,
)
