package com.pheeeew.feature.map.map

import com.pheeeew.domain.model.location.CurrentLocation
import com.pheeeew.domain.model.location.LocationError
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.feature.map.MapPoint
import com.pheeeew.feature.map.MapRenderState
import com.pheeeew.feature.map.SighMarker
import com.pheeeew.feature.map.star.StarAgeStage
import com.pheeeew.feature.map.star.StarVisualPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapRenderRulesTest {
    @Test
    fun `markers are deduplicated by server feature id`() {
        val markers =
            listOf(
                marker("same", 37.5, 127.0),
                marker("same", 37.6, 127.1),
                marker("other", 37.7, 127.2),
            )

        assertEquals(listOf(markers[0], markers[2]), MapRenderRules.renderableSighMarkers(markers))
    }

    @Test
    fun `invalid coordinates are not rendered`() {
        val markers =
            listOf(
                marker("valid", 37.5, 127.0),
                marker("bad-latitude", 91.0, 127.0),
                marker("bad-longitude", 37.5, Double.NaN),
            )

        assertEquals(listOf(markers.first()), MapRenderRules.renderableSighMarkers(markers))
    }

    @Test
    fun `fallback is never exposed as current location`() {
        val fallback = MapPoint("fallback", 37.5505, 127.0373)
        val state =
            MapRenderState(
                currentLocation = null,
                locationState = LocationState.Unavailable(LocationError.GpsUnavailable),
                fallbackCenter = fallback,
                sighMarkers = emptyList(),
                focusRequest = null,
            )

        assertNull(MapRenderRules.currentLocation(state))
        assertEquals(fallback, MapRenderRules.initialCenter(state))
    }

    @Test
    fun `loading fallback is marked provisional until the first gps result`() {
        val fallback = MapPoint("fallback", 37.5505, 127.0373)
        val state =
            MapRenderState(
                currentLocation = null,
                locationState = LocationState.Loading,
                fallbackCenter = fallback,
                sighMarkers = emptyList(),
                focusRequest = null,
            )

        assertEquals(fallback, MapRenderRules.initialCenter(state))
        assertEquals(true, MapRenderRules.initialCenterIsProvisional(state))
    }

    @Test
    fun `unavailable fallback is still provisional until the first gps result`() {
        val fallback = MapPoint("fallback", 37.5505, 127.0373)
        val state =
            MapRenderState(
                currentLocation = null,
                locationState = LocationState.Unavailable(LocationError.PermissionDenied),
                fallbackCenter = fallback,
                sighMarkers = emptyList(),
                focusRequest = null,
            )

        assertEquals(fallback, MapRenderRules.initialCenter(state))
        assertEquals(true, MapRenderRules.initialCenterIsProvisional(state))
    }

    @Test
    fun `available state only renders the same current location`() {
        val available = CurrentLocation(37.5, 127.0, 10f, 1L)
        val staleUiValue = available.copy(longitude = 128.0)
        val state =
            MapRenderState(
                currentLocation = staleUiValue,
                locationState = LocationState.Available(available),
                fallbackCenter = null,
                sighMarkers = emptyList(),
                focusRequest = null,
            )

        assertNull(MapRenderRules.currentLocation(state))
    }

    private fun marker(
        id: String,
        latitude: Double,
        longitude: Double,
    ): SighMarker =
        SighMarker(
            id = id,
            latitude = latitude,
            longitude = longitude,
            visual = StarVisualPolicy.visualFor(StarAgeStage.Unknown),
        )
}
