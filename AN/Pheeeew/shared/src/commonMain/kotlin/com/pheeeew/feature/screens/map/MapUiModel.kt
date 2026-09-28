package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.LocationError
import com.pheeeew.domain.model.LocationState
import com.pheeeew.feature.screens.map.monitoring.ContentLoad

data class MapUiModel(
    val hiddenEmotionIds: Set<Long> = emptySet(),
    val locationState: LocationState = LocationState.Loading,
    val mapError: MapErrorUiModel? = null,
    val isOffline: Boolean = false,
    val locationError: LocationError? = null,
    val mapRevision: Int = 0,
    val cameraCommand: MapCameraCommandUiModel? = null,
    val isRequestingLocation: Boolean = false,
    val isEmotionSelectorExpanded: Boolean = false,
    val isRecordLocationPicking: Boolean = false,
    val recordOrigin: GeoCoordinate? = null,
    val emotionPins: List<EmotionPinUiModel> = emptyList(),
    val emotionPinSymbolImages: List<EmotionPinSymbolImage> = emptyList(),
    val isLoadingEmotionPins: Boolean = false,
    val isLoadingMoreEmotionPins: Boolean = false,
    val hasPartialEmotionPins: Boolean = false,
    val invalidEmotionPinCount: Int = 0,
    val emotionPinsError: String? = null,
    val emotionContentLoad: ContentLoad? = null,
)
