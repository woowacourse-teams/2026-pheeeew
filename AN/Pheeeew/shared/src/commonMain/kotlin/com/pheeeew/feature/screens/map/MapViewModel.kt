package com.pheeeew.feature.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.domain.model.emotion.EmotionMapPageResult
import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.usecase.FindEmotionMapPageUseCase
import com.pheeeew.domain.usecase.FindEmotionMapSnapshotUseCase
import com.pheeeew.domain.usecase.RefreshLocationUseCase
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
) : ViewModel() {
    private val _uiModel = MutableStateFlow(MapUiModel())
    val uiModel: StateFlow<MapUiModel> = _uiModel.asStateFlow()

    private var hasStarted = false
    private var locationRequestJob: Job? = null
    private var nextCameraCommandId = 0L
    private var viewportJob: Job? = null
    private var emotionMapJob: Job? = null
    private var queryGeneration = 0L
    private var activeEmotionMapRequestId = 0L
    private var pendingEmotionMapRequest: EmotionMapRequest? = null
    private var requestedBounds: EmotionMapBounds? = null
    private var displayedBounds: EmotionMapBounds? = null
    private var retryCursor: String? = null
    private var displayedPins = linkedMapOf<Long, EmotionMapPin>()

    fun start() {
        if (hasStarted) return
        hasStarted = true
        requestCurrentLocation(moveCamera = false)
    }

    fun onEmotionHidden(id: Long) {
        _uiModel.value = _uiModel.value.copy(hiddenEmotionIds = _uiModel.value.hiddenEmotionIds + id)
    }

    fun onMyLocationClick() {
        requestCurrentLocation(moveCamera = true)
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
        val wasPicking = _uiModel.value.isRecordLocationPicking
        _uiModel.value = _uiModel.value.copy(isRecordLocationPicking = isPicking)
        if (isPicking) {
            queryGeneration++
            viewportJob?.cancel()
            pendingEmotionMapRequest = null
            cancelEmotionMapRequest()
            _uiModel.value =
                _uiModel.value.copy(
                    isLoadingEmotionPins = false,
                    isLoadingMoreEmotionPins = false,
                )
        } else if (wasPicking) {
            displayedBounds = null
        }
        // The renderer initially fits the circle and constrains camera movement around the origin.
    }

    fun onViewportChanged(bounds: EmotionMapBounds) {
        if (_uiModel.value.isRecordLocationPicking || !bounds.isValid()) return
        if (bounds == requestedBounds && (viewportJob?.isActive == true || emotionMapJob?.isActive == true)) return
        if (bounds == displayedBounds && emotionMapJob?.isActive != true) return
        requestedBounds = bounds
        publishSnapshot(bounds)
        val generation = ++queryGeneration
        viewportJob?.cancel()
        pendingEmotionMapRequest = null
        viewportJob =
            viewModelScope.launch {
                delay(VIEWPORT_DEBOUNCE_MILLIS)
                if (generation == queryGeneration) {
                    enqueueEmotionMapRequest(EmotionMapRequest(bounds, generation))
                }
            }
    }

    fun refreshEmotionPins() {
        if (_uiModel.value.isRecordLocationPicking) return
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
                existingPins = LinkedHashMap(displayedPins),
                bypassCache = true,
            ),
        )
    }

    private fun enqueueEmotionMapRequest(request: EmotionMapRequest) {
        if (request.generation != queryGeneration || _uiModel.value.isRecordLocationPicking) return
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
                val usedCursors = mutableSetOf<String>()
                var cursor: String? = request.startCursor
                cursor?.let(usedCursors::add)
                var firstPage = request.startCursor == null
                var invalidCount = if (firstPage) 0 else _uiModel.value.invalidEmotionPinCount
                try {
                    while (true) {
                        if (request.generation != queryGeneration) return@launch
                        when (
                            val result =
                                findEmotionMapPage(
                                    request.bounds,
                                    cursor = cursor,
                                    forceRefresh = request.bypassCache && cursor == null,
                                )
                        ) {
                            is EmotionMapPageResult.Failure -> {
                                if (request.generation == queryGeneration) {
                                    retryCursor = cursor
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
                                invalidCount += page.invalidItemCount
                                page.pins.forEach { pins[it.id] = it }
                                if (request.generation != queryGeneration) {
                                    if (!_uiModel.value.isRecordLocationPicking) {
                                        requestedBounds?.let(::publishSnapshot)
                                    }
                                    return@launch
                                }
                                displayedBounds = request.bounds
                                displayedPins = pins
                                val ordered = pins.values.toOrderedUiPins()
                                _uiModel.value =
                                    _uiModel.value.copy(
                                        emotionPins = ordered,
                                        isLoadingEmotionPins = false,
                                        isLoadingMoreEmotionPins = page.hasNext,
                                        hasPartialEmotionPins = false,
                                        invalidEmotionPinCount = invalidCount,
                                        emotionPinsError = null,
                                    )
                                firstPage = false
                                if (!page.hasNext) break
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
                    throw cancellation
                } catch (_: Exception) {
                    if (request.generation == queryGeneration) {
                        retryCursor = cursor
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

    private fun cancelEmotionMapRequest() {
        activeEmotionMapRequestId++
        emotionMapJob?.cancel()
        emotionMapJob = null
    }

    private fun publishSnapshot(bounds: EmotionMapBounds) {
        val pins = findEmotionMapSnapshot(bounds)?.pins ?: return
        displayedPins = pins.associateByTo(linkedMapOf()) { it.id }
        _uiModel.value = _uiModel.value.copy(emotionPins = pins.toOrderedUiPins())
    }

    private fun Collection<EmotionMapPin>.toOrderedUiPins(): List<EmotionPinUiModel> =
        sortedWith(compareBy<EmotionMapPin> { Instant.parse(it.createdAt) }.thenBy { it.id })
            .map { it.toUiModel() }

    fun onMapError(error: MapErrorUiModel) {
        _uiModel.value = _uiModel.value.copy(mapError = error)
    }

    fun onMapRecovered() {
        _uiModel.value = _uiModel.value.copy(mapError = null)
    }

    fun retryMap() {
        _uiModel.value =
            _uiModel.value.copy(
                mapError = null,
                mapRevision = _uiModel.value.mapRevision + 1,
            )
    }

    private fun requestCurrentLocation(moveCamera: Boolean) {
        if (locationRequestJob?.isActive == true) return
        locationRequestJob =
            viewModelScope.launch {
                _uiModel.value = _uiModel.value.copy(isRequestingLocation = true)
                try {
                    val locationState = refreshLocation()
                    _uiModel.value = _uiModel.value.copy(locationState = locationState)
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
                    ),
            )
    }

    companion object {
        private const val LOCATION_FOCUS_ZOOM = 15.5

        fun create(
            locationDependencies: LocationDependencies,
            findEmotionMapPage: FindEmotionMapPageUseCase,
            findEmotionMapSnapshot: FindEmotionMapSnapshotUseCase,
        ): MapViewModel =
            MapViewModel(
                refreshLocation =
                    RefreshLocationUseCase(
                        permissionController = locationDependencies.permissionController,
                        repository = locationDependencies.repository,
                    ),
                findEmotionMapPage = findEmotionMapPage,
                findEmotionMapSnapshot = findEmotionMapSnapshot,
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
