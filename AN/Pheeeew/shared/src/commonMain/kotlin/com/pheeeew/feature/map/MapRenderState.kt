package com.pheeeew.feature.map

import com.pheeeew.domain.model.location.CurrentLocation
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.feature.map.star.StarAgeStage
import com.pheeeew.feature.map.star.StarVisual
import com.pheeeew.feature.map.star.StarVisualPolicy

data class MapPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
)

data class SighMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val visual: StarVisual = StarVisualPolicy.visualFor(StarAgeStage.Unknown),
)

data class MapFocusRequest(
    val id: String,
    val latitude: Double,
    val longitude: Double,
)

data class MapRenderState(
    val currentLocation: CurrentLocation?,
    val locationState: LocationState,
    val fallbackCenter: MapPoint?,
    val sighMarkers: List<SighMarker>,
    val focusRequest: MapFocusRequest?,
)
