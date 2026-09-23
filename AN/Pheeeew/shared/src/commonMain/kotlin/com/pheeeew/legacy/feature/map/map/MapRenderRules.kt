package com.pheeeew.legacy.feature.map.map

import com.pheeeew.legacy.domain.model.location.CurrentLocation
import com.pheeeew.legacy.domain.model.location.LocationState
import com.pheeeew.legacy.feature.map.MapPoint
import com.pheeeew.legacy.feature.map.MapRenderState
import com.pheeeew.legacy.feature.map.SighMarker

internal object MapRenderRules {
    fun renderableSighMarkers(markers: List<SighMarker>): List<SighMarker> =
        markers
            .asSequence()
            .filter { it.hasValidCoordinate() }
            .distinctBy(SighMarker::id)
            .toList()

    fun currentLocation(state: MapRenderState): CurrentLocation? {
        val available = state.locationState as? LocationState.Available ?: return null
        val location = state.currentLocation ?: return null

        return location.takeIf {
            it == available.location &&
                it.hasValidCoordinate() &&
                it.accuracyMeters.isFinite() &&
                it.accuracyMeters >= 0f
        }
    }

    fun initialCenter(state: MapRenderState): MapPoint? {
        currentLocation(state)?.let { location ->
            return MapPoint(
                id = "current-location",
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }

        return state.fallbackCenter?.takeIf { it.hasValidCoordinate() }
    }

    fun initialCenterIsProvisional(state: MapRenderState): Boolean =
        currentLocation(state) == null && initialCenter(state) != null

    private fun CurrentLocation.hasValidCoordinate(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0

    private fun SighMarker.hasValidCoordinate(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0

    private fun MapPoint.hasValidCoordinate(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
}
