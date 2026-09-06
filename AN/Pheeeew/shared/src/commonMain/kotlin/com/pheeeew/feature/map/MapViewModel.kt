package com.pheeeew.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.geo.toGridCenter
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.di.LocationDependencies
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.feature.map.map.MapCameraCommand
import com.pheeeew.feature.map.map.MapDarkStyle
import com.pheeeew.feature.map.map.MapError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

private const val MIN_SIGH_SUBMITTING_DURATION_MILLIS = 2_000L

class MapViewModel(
    private val sighRepository: SighRepository,
    private val locationDependencies: LocationDependencies?,
) : ViewModel() {
    private var nextCameraCommandId = 0L
    private var pendingRegistration: PendingSighRequest? = null
    private val sighOperationMutex = Mutex()
    private val locallyRegisteredSighs = mutableMapOf<Long, SighPin>()
    private var mapIsForeground = false
    private var loadSighsJob: Job? = null
    private var lastSighBounds: SighBounds? = null
    private var myLocationJob: Job? = null

    private val _uiState =
        MutableStateFlow<MapUiState>(
            MapUiState.Success(
                sighs = emptyList(),
                locationState = locationDependencies?.repository?.locationState?.value ?: LocationState.Loading,
                cameraCommand = null,
                isRequestingLocation = false,
            ),
        )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // 스플래시 화면 노출 시간 설정을 위한 플로우
    val isReady: Flow<Boolean> = uiState.map { it !is MapUiState.Loading }

    init {
        locationDependencies?.let { dependencies ->
            viewModelScope.launch {
                dependencies.repository.locationState.collect { locationState ->
                    _uiState.update { state ->
                        (state as? MapUiState.Success)?.copy(locationState = locationState) ?: state
                    }
                }
            }
        }
    }

    fun loadSighs(bounds: SighBounds) {
        lastSighBounds = bounds

        if (!mapIsForeground) return

        loadSighsJob?.cancel()

        loadSighsJob =
            viewModelScope.launch {
                sighOperationMutex.withLock {
                    try {
                        val serverSighs = sighRepository.getSighs(bounds).distinctBy(SighPin::id)
                        val serverIds = serverSighs.mapTo(mutableSetOf(), SighPin::id)
                        serverIds.forEach(locallyRegisteredSighs::remove)
                        val mergedSighs = (serverSighs + locallyRegisteredSighs.values).distinctBy(SighPin::id)
                        _uiState.update { state ->
                            (state as? MapUiState.Success)?.copy(
                                sighs = mergedSighs,
                                refreshErrorMessage = null,
                            ) ?: state
                        }
                    } catch (e: ApiException) {
                        _uiState.update { state ->
                            if (state is MapUiState.Success) {
                                state.copy(refreshErrorMessage = e.toUserMessage())
                            } else {
                                state
                            }
                        }
                    }
                }
            }
    }

    fun registerSighAfterExplosion() {
        val current = _uiState.value as? MapUiState.Success ?: return
        if (current.sighReleaseState is SighReleaseState.Submitting) return
        val location = (current.locationState as? LocationState.Available)?.location

        if (location == null) {
            val message =
                (current.locationState as? LocationState.Unavailable)?.reason?.toKoreanMessage()
                    ?: "GPS 수신이 원활하지 않습니다."
            _uiState.value =
                current.copy(
                    sighReleaseState = SighReleaseState.Error(message = message, canRetry = false),
                )
            return
        }

        val request =
            pendingRegistration
                ?: PendingSighRequest(
                    requestId = Uuid.random().toString(),
                    coordinate = location.coordinate.toGridCenter(),
                ).also { pendingRegistration = it }
        submit(request)
    }

    private fun submit(request: PendingSighRequest) {
        val submittingStartedAt = TimeSource.Monotonic.markNow()
        _uiState.update {
            (it as? MapUiState.Success)?.copy(
                sighReleaseState = SighReleaseState.Submitting,
            ) ?: it
        }
        viewModelScope.launch {
            sighOperationMutex.withLock {
                try {
                    val sighPin = sighRepository.registerSigh(request.requestId, request.coordinate)
                    locallyRegisteredSighs[sighPin.id] = sighPin
                    waitForMinimumSubmittingDuration(submittingStartedAt)
                    pendingRegistration = null
                    _uiState.update { state ->
                        (state as? MapUiState.Success)?.copy(
                            sighs = (state.sighs + sighPin).distinctBy(SighPin::id),
                            sighReleaseState = SighReleaseState.Idle,
                            focusRequest =
                                MapFocusRequest(
                                    id = sighPin.id.toString(),
                                    latitude = sighPin.coordinate.latitude,
                                    longitude = sighPin.coordinate.longitude,
                                ),
                        )
                            ?: state
                    }
                } catch (e: ApiException) {
                    waitForMinimumSubmittingDuration(submittingStartedAt)
                    _uiState.update {
                        (it as? MapUiState.Success)?.copy(
                            sighReleaseState = SighReleaseState.Error(message = e.toUserMessage(), canRetry = true),
                        ) ?: it
                    }
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
        pendingRegistration = null
        val current = _uiState.value as? MapUiState.Success ?: return
        _uiState.value = current.copy(sighReleaseState = SighReleaseState.Idle)
    }

    fun consumeFocusRequest(id: String) {
        _uiState.update { state ->
            if (state is MapUiState.Success && state.focusRequest?.id == id) state.copy(focusRequest = null) else state
        }
    }

    fun onZoomInClick() = sendCameraCommand { id -> MapCameraCommand.ZoomBy(id = id, delta = 1.0) }

    fun onZoomOutClick() = sendCameraCommand { id -> MapCameraCommand.ZoomBy(id = id, delta = -1.0) }

    fun onMyLocationClick() {
        val dependencies = locationDependencies ?: return
        val current = _uiState.value as? MapUiState.Success ?: return
        if (current.isRequestingLocation) return

        myLocationJob?.cancel()

        myLocationJob =
            viewModelScope.launch {
                _uiState.update { (it as? MapUiState.Success)?.copy(isRequestingLocation = true) ?: it }
                try {
                    val status = ensureLocationPermission()
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
                    _uiState.update { (it as? MapUiState.Success)?.copy(isRequestingLocation = false) ?: it }
                }
            }
    }

    suspend fun ensureLocationPermission(refreshLocation: Boolean = true): LocationPermissionStatus {
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
            (state as? MapUiState.Success)?.copy(
                mapErrorMessage = error.toUserMessage(),
            ) ?: state
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

        myLocationJob?.cancel()
        myLocationJob = null
    }

    private fun sendCameraCommand(create: (Long) -> MapCameraCommand) {
        nextCameraCommandId += 1L
        _uiState.update { state ->
            (state as? MapUiState.Success)?.copy(cameraCommand = create(nextCameraCommandId)) ?: state
        }
    }

    private data class PendingSighRequest(
        val requestId: String,
        val coordinate: Coordinate,
    )
}
