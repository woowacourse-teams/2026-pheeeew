package com.pheeeew.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.di.LocationDependencies
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.domain.usecase.CreateSighUseCase
import com.pheeeew.feature.map.map.MapCameraCommand
import com.pheeeew.feature.map.map.MapDarkStyle
import com.pheeeew.feature.map.map.MapError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

private const val MIN_SIGH_SUBMITTING_DURATION_MILLIS = 2_000L
private const val SIGH_BOUNDS_DEBOUNCE_MILLIS = 250L

typealias MapPerformanceLogger = (String) -> Unit

class MapViewModel(
    private val sighRepository: SighRepository,
    private val createSigh: CreateSighUseCase,
    private val locationDependencies: LocationDependencies?,
    private val mapPerformanceLogger: MapPerformanceLogger,
) : ViewModel() {
    private var nextCameraCommandId = 0L
    private var pendingRegistration: CreateSighCommand? = null
    private var pendingMemoDraft: PendingSighDraft? = null
    private var pendingSighDetailId: Long? = null
    private val sighOperationMutex = Mutex()
    private val locallyRegisteredSighs = mutableMapOf<Long, SighPin>()
    private var mapIsForeground = false
    private var loadSighsJob: Job? = null
    private var loadSighListJob: Job? = null
    private var loadSighDetailJob: Job? = null
    private var lastSighBounds: SighBounds? = null
    private var cameraBeforeSighDetailBounds: SighBounds? = null
    private var detailCameraMovePending = false
    private var restoringSighDetailBounds: SighBounds? = null
    private var lastRequestedSighBounds: SighBounds? = null
    private var latestSighRequestId = 0L
    private var latestSighListRequestId = 0L
    private var latestSighDetailRequestId = 0L
    private var myLocationJob: Job? = null

    private val _uiState =
        MutableStateFlow<MapUiState>(
            MapUiState(
                location =
                    MapLocationUiState(
                        state = locationDependencies?.repository?.locationState?.value ?: LocationState.Loading,
                    ),
            ),
        )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        locationDependencies?.let { dependencies ->
            viewModelScope.launch {
                dependencies.repository.locationState.collect { locationState ->
                    _uiState.update { state ->
                        state.copy(
                            location = state.location.copy(state = locationState),
                        )
                    }
                }
            }
        }
    }

    fun loadSighs(bounds: SighBounds) {
        if (!bounds.isValidForQuery()) {
            mapPerformanceLogger("invalid_bounds_skipped")
            return
        }
        if (_uiState.value.sighBrowser.selectedSigh != null) {
            lastSighBounds = bounds
            detailCameraMovePending = false
            mapPerformanceLogger("detail_camera_bounds_skipped")
            return
        }
        if (detailCameraMovePending) {
            lastSighBounds = bounds
            detailCameraMovePending = false
            mapPerformanceLogger("detail_camera_bounds_skipped")
            return
        }
        restoringSighDetailBounds?.let { restoringBounds ->
            if (bounds.isApproximatelyEqualTo(restoringBounds)) {
                restoringSighDetailBounds = null
            }
            mapPerformanceLogger("detail_restore_bounds_skipped")
            return
        }
        val isInitialBounds = lastSighBounds == null
        lastSighBounds = bounds
        mapPerformanceLogger("bounds_received")

        val browser = _uiState.value.sighBrowser
        if (isInitialBounds && browser.isVisible && browser.items.isEmpty() && !browser.isLoading) {
            loadFirstSighPage(bounds)
        }

        if (!mapIsForeground) return
        if (bounds == lastRequestedSighBounds) {
            mapPerformanceLogger("duplicate_bounds_skipped")
            return
        }

        lastRequestedSighBounds = bounds
        val requestId = ++latestSighRequestId

        loadSighsJob?.cancel()

        loadSighsJob =
            viewModelScope.launch {
                try {
                    delay(SIGH_BOUNDS_DEBOUNCE_MILLIS)
                    mapPerformanceLogger("request_started id=$requestId")

                    val serverSighs =
                        try {
                            sighRepository
                                .getMapSighs(bounds)
                                .distinctBy(SighPin::id)
                                .sortedBy(SighPin::id)
                        } catch (e: ApiException) {
                            sighOperationMutex.withLock {
                                if (requestId != latestSighRequestId || !mapIsForeground) {
                                    mapPerformanceLogger("stale_error_dropped id=$requestId")
                                    return@withLock
                                }

                                lastRequestedSighBounds = null
                                mapPerformanceLogger("request_failed id=$requestId")
                                _uiState.update { state ->
                                    state.copy(
                                        errors = state.errors.copy(refreshMessage = e.toUserMessage()),
                                    )
                                }
                            }
                            return@launch
                        }

                    sighOperationMutex.withLock {
                        if (requestId != latestSighRequestId || !mapIsForeground) {
                            mapPerformanceLogger("stale_response_dropped id=$requestId")
                            return@withLock
                        }

                        val serverIds = serverSighs.mapTo(mutableSetOf(), SighPin::id)
                        serverIds.forEach(locallyRegisteredSighs::remove)
                        val mergedSighs =
                            (serverSighs + locallyRegisteredSighs.values)
                                .distinctBy(SighPin::id)
                                .sortedBy(SighPin::id)
                        _uiState.update { state ->
                            state.copy(
                                sighs = mergedSighs,
                                errors = state.errors.copy(refreshMessage = null),
                            )
                        }
                        mapPerformanceLogger("response_applied id=$requestId")
                    }
                } catch (e: CancellationException) {
                    mapPerformanceLogger("request_cancelled id=$requestId")
                    throw e
                }
            }
    }

    fun setSighListVisible(visible: Boolean) {
        if (!visible) {
            restoreCameraBeforeSighDetail()
            latestSighListRequestId += 1L
            latestSighDetailRequestId += 1L
            loadSighListJob?.cancel()
            loadSighListJob = null
            loadSighDetailJob?.cancel()
            loadSighDetailJob = null
            pendingSighDetailId = null
            _uiState.update { state ->
                state.copy(
                    sighBrowser =
                        state.sighBrowser.copy(
                            isVisible = false,
                            selectedSigh = null,
                            isLoading = false,
                            isDetailLoading = false,
                            isLoadingMore = false,
                            isLoadMoreError = false,
                            errorMessage = null,
                        ),
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                sighBrowser = state.sighBrowser.copy(isVisible = true, selectedSigh = null),
            )
        }
        lastSighBounds?.let { bounds -> loadFirstSighPage(bounds) }
    }

    fun openSighFromPin(id: Long) {
        if (!mapIsForeground || lastSighBounds == null) return

        latestSighDetailRequestId += 1L
        val requestId = latestSighDetailRequestId
        loadSighDetailJob?.cancel()
        loadSighDetailJob = null

        _uiState.update { state ->
            state.copy(
                sighBrowser =
                    state.sighBrowser.copy(
                        isVisible = true,
                        selectedSigh = null,
                        isDetailLoading = false,
                        errorMessage = null,
                    ),
            )
        }

        if (_uiState.value.sighBrowser.items.any { it.id == id }) {
            pendingSighDetailId = null
            selectSigh(id)
            return
        }

        pendingSighDetailId = id
        _uiState.update { state ->
            state.copy(
                sighBrowser = state.sighBrowser.copy(isDetailLoading = true),
            )
        }
        loadSighDetailJob =
            viewModelScope.launch {
                try {
                    val sigh = sighRepository.getById(id)
                    if (
                        requestId != latestSighDetailRequestId ||
                        !mapIsForeground ||
                        !_uiState.value.sighBrowser.isVisible
                    ) {
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            sighBrowser =
                                state.sighBrowser.copy(
                                    items = (listOf(sigh) + state.sighBrowser.items).distinctBy(Sigh::id),
                                    isDetailLoading = false,
                                    errorMessage = null,
                                ),
                        )
                    }
                    pendingSighDetailId = null
                    selectSigh(id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: ApiException) {
                    if (
                        requestId != latestSighDetailRequestId ||
                        !mapIsForeground ||
                        !_uiState.value.sighBrowser.isVisible
                    ) {
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            sighBrowser =
                                state.sighBrowser.copy(
                                    isDetailLoading = false,
                                    errorMessage = exception.toUserMessage(),
                                ),
                        )
                    }
                }
            }
    }

    fun refreshSighList() {
        if (!_uiState.value.sighBrowser.isVisible) return
        pendingSighDetailId?.let { id ->
            openSighFromPin(id)
            return
        }
        lastSighBounds?.let { bounds -> loadFirstSighPage(bounds) }
    }

    fun loadNextSighPage() {
        val browser = _uiState.value.sighBrowser
        val cursor = browser.nextCursor ?: return
        if (!mapIsForeground || !browser.isVisible || browser.isLoading || browser.isLoadingMore) return
        val requestId = latestSighListRequestId

        _uiState.update { state ->
            state.copy(
                sighBrowser =
                    state.sighBrowser.copy(
                        isLoadingMore = true,
                        isLoadMoreError = false,
                        errorMessage = null,
                    ),
            )
        }
        loadSighListJob =
            viewModelScope.launch {
                try {
                    val page = sighRepository.getNextPage(cursor)
                    _uiState.update { state ->
                        val current = state.sighBrowser
                        if (
                            requestId != latestSighListRequestId ||
                            !mapIsForeground ||
                            !current.isVisible ||
                            current.nextCursor != cursor
                        ) {
                            return@update state
                        }
                        state.copy(
                            sighBrowser =
                                current.copy(
                                    items = (current.items + page.items).distinctBy(Sigh::id),
                                    nextCursor = page.nextCursor,
                                    isLoadingMore = false,
                                    isLoadMoreError = false,
                                ),
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: ApiException) {
                    _uiState.update { state ->
                        if (
                            requestId != latestSighListRequestId ||
                            !mapIsForeground ||
                            !state.sighBrowser.isVisible
                        ) {
                            return@update state
                        }
                        state.copy(
                            sighBrowser =
                                state.sighBrowser.copy(
                                    isLoadingMore = false,
                                    isLoadMoreError = true,
                                    errorMessage = exception.toUserMessage(),
                                ),
                        )
                    }
                }
            }
    }

    fun selectSigh(id: Long) {
        val browser = _uiState.value.sighBrowser
        if (!browser.isVisible) return
        val cachedSigh = browser.items.firstOrNull { it.id == id } ?: return
        val bounds = lastSighBounds ?: return

        if (cameraBeforeSighDetailBounds == null) {
            cameraBeforeSighDetailBounds = bounds
        }
        detailCameraMovePending = true
        sendCameraCommand { commandId ->
            MapCameraCommand.MoveToCoordinate(
                id = commandId,
                latitude = cachedSigh.coordinate.latitude,
                longitude = cachedSigh.coordinate.longitude,
                zoom = MapDarkStyle.SIGH_DETAIL_ZOOM,
                verticalPosition = DETAIL_MARKER_VERTICAL_POSITION,
            )
        }

        _uiState.update { state ->
            state.copy(sighBrowser = state.sighBrowser.copy(selectedSigh = cachedSigh))
        }
    }

    fun dismissSighDetail() {
        _uiState.update { state ->
            state.copy(sighBrowser = state.sighBrowser.copy(selectedSigh = null))
        }
    }

    private fun loadFirstSighPage(bounds: SighBounds) {
        if (!mapIsForeground || !_uiState.value.sighBrowser.isVisible || !bounds.isValidForQuery()) return

        val requestId = ++latestSighListRequestId
        loadSighListJob?.cancel()
        _uiState.update { state ->
            state.copy(
                sighBrowser =
                    state.sighBrowser.copy(
                        isLoading = true,
                        isLoadingMore = false,
                        isLoadMoreError = false,
                        selectedSigh = null,
                        nextCursor = null,
                        errorMessage = null,
                    ),
            )
        }
        loadSighListJob =
            viewModelScope.launch {
                try {
                    val page = sighRepository.getFirstPage(bounds)
                    _uiState.update { state ->
                        if (
                            requestId != latestSighListRequestId ||
                            !mapIsForeground ||
                            !state.sighBrowser.isVisible ||
                            lastSighBounds != bounds
                        ) {
                            return@update state
                        }
                        state.copy(
                            sighBrowser =
                                state.sighBrowser.copy(
                                    items = page.items.distinctBy(Sigh::id),
                                    selectedSigh = null,
                                    nextCursor = page.nextCursor,
                                    isLoading = false,
                                    isLoadMoreError = false,
                                    refreshRevision = state.sighBrowser.refreshRevision + 1L,
                                ),
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: ApiException) {
                    _uiState.update { state ->
                        if (
                            requestId != latestSighListRequestId ||
                            !mapIsForeground ||
                            !state.sighBrowser.isVisible ||
                            lastSighBounds != bounds
                        ) {
                            return@update state
                        }
                        state.copy(
                            sighBrowser =
                                state.sighBrowser.copy(
                                    isLoading = false,
                                    errorMessage = exception.toUserMessage(),
                                ),
                        )
                    }
                }
            }
    }

    private fun restoreCameraBeforeSighDetail() {
        val bounds = cameraBeforeSighDetailBounds ?: return
        cameraBeforeSighDetailBounds = null
        lastSighBounds = bounds
        detailCameraMovePending = false
        restoringSighDetailBounds = bounds
        sendCameraCommand { commandId ->
            MapCameraCommand.MoveToBounds(
                id = commandId,
                bounds = bounds,
            )
        }
    }

    fun beginMemoAfterExplosion() {
        val current = _uiState.value
        if (current.sighRelease !is SighReleaseState.Idle) return
        val location = (current.location.state as? LocationState.Available)?.location

        if (location == null) {
            val message =
                (current.location.state as? LocationState.Unavailable)?.reason?.toKoreanMessage()
                    ?: "GPS 수신이 원활하지 않습니다."
            _uiState.value =
                current.copy(
                    sighRelease = SighReleaseState.Error(message = message, canRetry = false),
                )
            return
        }

        val draft =
            pendingMemoDraft
                ?: PendingSighDraft(
                    requestId = Uuid.random().toString(),
                    coordinate = location.coordinate,
                ).also { pendingMemoDraft = it }
        _uiState.update { state -> state.copy(sighRelease = SighReleaseState.EditingMemo(draft)) }
    }

    fun submitMemo(rawMemo: String) {
        val state = _uiState.value.sighRelease as? SighReleaseState.EditingMemo ?: return
        val memo =
            try {
                MemoPolicy.normalize(rawMemo)
            } catch (e: IllegalArgumentException) {
                _uiState.update { current ->
                    current.copy(sighRelease = SighReleaseState.Error(e.message.orEmpty(), canRetry = false))
                }
                return
            }
        val command =
            pendingRegistration
                ?: createSigh
                    .prepare(
                        requestId = state.draft.requestId,
                        coordinate = state.draft.coordinate,
                        memo = memo,
                    ).also { pendingRegistration = it }
        submit(command)
    }

    fun skipMemo() {
        submitMemo(rawMemo = "")
    }

    fun dismissMemo() {
        if (_uiState.value.sighRelease !is SighReleaseState.EditingMemo) return
        clearPendingSigh()
        _uiState.update { state -> state.copy(sighRelease = SighReleaseState.Idle) }
    }

    fun retrySighCreation() {
        val command = (_uiState.value.sighRelease as? SighReleaseState.Error)?.command ?: return
        submit(command)
    }

    private fun submit(command: CreateSighCommand) {
        val submittingStartedAt = TimeSource.Monotonic.markNow()
        _uiState.update { state ->
            state.copy(
                sighRelease = SighReleaseState.Submitting(command),
            )
        }
        viewModelScope.launch {
            try {
                val sighPin = createSigh(command).toPin()
                waitForMinimumSubmittingDuration(submittingStartedAt)

                sighOperationMutex.withLock {
                    locallyRegisteredSighs[sighPin.id] = sighPin
                    clearPendingSigh()
                    _uiState.update { state ->
                        state.copy(
                            sighs =
                                (state.sighs + sighPin)
                                    .distinctBy(SighPin::id)
                                    .sortedBy(SighPin::id),
                            sighRelease = SighReleaseState.Idle,
                            viewport =
                                state.viewport.copy(
                                    focusRequest =
                                        MapFocusRequest(
                                            id = sighPin.id.toString(),
                                            latitude = sighPin.coordinate.latitude,
                                            longitude = sighPin.coordinate.longitude,
                                        ),
                                ),
                        )
                    }
                }
            } catch (e: ApiException) {
                waitForMinimumSubmittingDuration(submittingStartedAt)
                _uiState.update { state ->
                    state.copy(
                        sighRelease =
                            SighReleaseState.Error(
                                message = e.toUserMessage(),
                                canRetry = true,
                                command = command,
                            ),
                    )
                }
            }
        }
    }

    private suspend fun waitForMinimumSubmittingDuration(startedAt: TimeMark) {
        val remainingMillis =
            MIN_SIGH_SUBMITTING_DURATION_MILLIS - startedAt.elapsedNow().inWholeMilliseconds
        if (remainingMillis > 0) delay(remainingMillis)
    }

    fun cancelFailedSighRegistration() {
        clearPendingSigh()
        _uiState.update { state -> state.copy(sighRelease = SighReleaseState.Idle) }
    }

    private fun clearPendingSigh() {
        pendingRegistration = null
        pendingMemoDraft = null
    }

    fun consumeFocusRequest(id: String) {
        _uiState.update { state ->
            if (state.viewport.focusRequest?.id == id) {
                state.copy(viewport = state.viewport.copy(focusRequest = null))
            } else {
                state
            }
        }
    }

    fun onZoomInClick() = sendCameraCommand { id -> MapCameraCommand.ZoomBy(id = id, delta = 1.0) }

    fun onZoomOutClick() = sendCameraCommand { id -> MapCameraCommand.ZoomBy(id = id, delta = -1.0) }

    fun onMyLocationClick() {
        val dependencies = locationDependencies ?: return
        val current = _uiState.value
        if (current.location.isRequesting) return

        myLocationJob?.cancel()

        myLocationJob =
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(location = state.location.copy(isRequesting = true))
                }
                try {
                    val status = ensureLocationPermission(refreshLocation = true)
                    if (status != LocationPermissionStatus.Granted) {
                        return@launch
                    }
                    if (dependencies.repository.locationState.value is LocationState.Available) {
                        sendCameraCommand { id ->
                            MapCameraCommand.MoveToCurrentLocation(
                                id = id,
                                zoom = MapDarkStyle.FOCUS_ZOOM,
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // 위치나 권한 값은 로그에 남기지 않습니다. 지도는 현재 카메라를 유지합니다.
                } finally {
                    _uiState.update { state ->
                        state.copy(location = state.location.copy(isRequesting = false))
                    }
                }
            }
    }

    suspend fun ensureLocationPermission(refreshLocation: Boolean): LocationPermissionStatus {
        val dependencies =
            locationDependencies
                ?: return LocationPermissionStatus.Denied

        return try {
            val status =
                when (dependencies.permissionController.currentStatus()) {
                    LocationPermissionStatus.Granted -> {
                        LocationPermissionStatus.Granted
                    }

                    LocationPermissionStatus.Denied -> {
                        dependencies.permissionController.requestPermission()
                    }

                    LocationPermissionStatus.PermanentlyDenied -> {
                        LocationPermissionStatus.PermanentlyDenied
                    }

                    LocationPermissionStatus.ServicesDisabled -> {
                        // iOS에서는 앱 권한이 아직 결정되지 않았을 수 있으므로
                        // 위치 서비스가 꺼져 있어도 시스템 권한 요청을 먼저 시도합니다.
                        dependencies.permissionController.requestPermission()
                        // 앱 권한 요청 결과와 전역 위치 서비스 상태를 다시 반영합니다.
                        dependencies.permissionController.currentStatus()
                    }
                }
            // 권한이 없거나 위치 서비스가 꺼진 경우에도 LocationState를 갱신해
            // 지도 화면의 오류 배너에서 사용자가 안내를 열 수 있도록 합니다.
            if (refreshLocation) {
                dependencies.repository.refreshCurrentLocation()
            }
            status
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            LocationPermissionStatus.Denied
        }
    }

    suspend fun refreshLocationPermission(): LocationPermissionStatus? {
        val dependencies = locationDependencies ?: return null
        return try {
            val status = dependencies.permissionController.currentStatus()
            dependencies.repository.refreshCurrentLocation()
            status
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 위치나 권한 값은 로그에 남기지 않습니다.
            LocationPermissionStatus.Denied
        }
    }

    fun openLocationSettings() {
        val dependencies = locationDependencies ?: return

        viewModelScope.launch {
            val status = dependencies.permissionController.currentStatus()

            if (status == LocationPermissionStatus.ServicesDisabled) {
                dependencies.permissionSettingsLauncher.openLocationSettings()
            } else {
                dependencies.permissionSettingsLauncher.openAppSettings()
            }
        }
    }

    fun openAppSettings() {
        val dependencies = locationDependencies ?: return
        viewModelScope.launch {
            dependencies.permissionSettingsLauncher.openAppSettings()
        }
    }

    fun onMapError(error: MapError) {
        _uiState.update { state ->
            state.copy(
                errors = state.errors.copy(renderMessage = error.toUserMessage()),
            )
        }
    }

    fun onMapForeground() {
        mapIsForeground = true

        lastSighBounds?.let(::loadSighs)
    }

    fun onMapBackground() {
        mapIsForeground = false

        loadSighsJob?.cancel()
        loadSighsJob = null
        loadSighListJob?.cancel()
        loadSighListJob = null
        loadSighDetailJob?.cancel()
        loadSighDetailJob = null
        pendingSighDetailId = null
        lastRequestedSighBounds = null
        detailCameraMovePending = false
        restoringSighDetailBounds = null

        _uiState.update { state ->
            state.copy(
                sighBrowser =
                    state.sighBrowser.copy(
                        isLoading = false,
                        isDetailLoading = false,
                        isLoadingMore = false,
                    ),
            )
        }

        myLocationJob?.cancel()
        myLocationJob = null
    }

    private fun sendCameraCommand(create: (Long) -> MapCameraCommand) {
        nextCameraCommandId += 1L
        _uiState.update { state ->
            state.copy(
                viewport = state.viewport.copy(cameraCommand = create(nextCameraCommandId)),
            )
        }
    }
}

private fun SighBounds.isValidForQuery(): Boolean =
    minLongitude.isFinite() &&
        minLatitude.isFinite() &&
        maxLongitude.isFinite() &&
        maxLatitude.isFinite() &&
        minLongitude in -180.0..180.0 &&
        maxLongitude in -180.0..180.0 &&
        minLatitude in -90.0..90.0 &&
        maxLatitude in -90.0..90.0 &&
        minLatitude <= maxLatitude

private fun SighBounds.isApproximatelyEqualTo(other: SighBounds): Boolean =
    kotlin.math.abs(minLongitude - other.minLongitude) <= BOUNDS_RESTORE_TOLERANCE &&
        kotlin.math.abs(minLatitude - other.minLatitude) <= BOUNDS_RESTORE_TOLERANCE &&
        kotlin.math.abs(maxLongitude - other.maxLongitude) <= BOUNDS_RESTORE_TOLERANCE &&
        kotlin.math.abs(maxLatitude - other.maxLatitude) <= BOUNDS_RESTORE_TOLERANCE

private const val DETAIL_MARKER_VERTICAL_POSITION = 0.28
private const val BOUNDS_RESTORE_TOLERANCE = 0.0001
