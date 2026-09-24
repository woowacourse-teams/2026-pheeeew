package com.pheeeew.feature.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.MapCameraState
import com.pheeeew.domain.usecase.RefreshLocationUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(
    private val refreshLocation: RefreshLocationUseCase,
) : ViewModel() {
    private val _uiModel = MutableStateFlow(MapUiModel())
    val uiModel: StateFlow<MapUiModel> = _uiModel.asStateFlow()

    private var hasStarted = false
    private var locationRequestJob: Job? = null
    private var nextCameraCommandId = 0L

    fun start() {
        if (hasStarted) return
        hasStarted = true
        requestCurrentLocation(moveCamera = false)
    }

    fun onMyLocationClick() {
        requestCurrentLocation(moveCamera = true)
    }

    fun onCameraChanged(cameraState: MapCameraState) {
        _uiModel.value = _uiModel.value.copy(cameraState = cameraState)
    }

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

        fun create(locationDependencies: LocationDependencies): MapViewModel =
            MapViewModel(
                refreshLocation =
                    RefreshLocationUseCase(
                        permissionController = locationDependencies.permissionController,
                        repository = locationDependencies.repository,
                    ),
            )
    }
}
