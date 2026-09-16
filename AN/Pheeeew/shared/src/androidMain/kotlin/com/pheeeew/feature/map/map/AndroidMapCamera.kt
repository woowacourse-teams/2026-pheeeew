package com.pheeeew.feature.map.map

import com.pheeeew.feature.map.MapFocusRequest
import com.pheeeew.feature.map.MapRenderState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/** 카메라/포커스 명령 ID를 소비해 재구성 시 중복 실행을 차단합니다. */
internal class AndroidMapCamera {
    private var hasAppliedProvisionalCenter = false
    private var hasResolvedInitialCenter = false
    private var lastCameraCommandId: Long? = null
    private var lastFocusRequestId: String? = null

    fun onCameraMoveStarted(reason: Int) {
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            hasResolvedInitialCenter = true
        }
    }

    fun render(
        map: MapLibreMap,
        state: MapRenderState,
        cameraCommand: MapCameraCommand?,
        viewportHeightPx: Int,
    ) {
        setInitialCenterIfNeeded(map, state)
        consumeFocusRequest(map, state.focusRequest)
        consumeCameraCommand(map, state, cameraCommand, viewportHeightPx)
    }

    private fun setInitialCenterIfNeeded(
        map: MapLibreMap,
        state: MapRenderState,
    ) {
        if (hasResolvedInitialCenter) return
        val center = MapRenderRules.initialCenter(state) ?: return
        val isProvisional = MapRenderRules.initialCenterIsProvisional(state)
        if (isProvisional && hasAppliedProvisionalCenter) return

        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(center.latitude, center.longitude),
                MapDarkStyle.INITIAL_ZOOM,
            ),
        )
        if (isProvisional) {
            hasAppliedProvisionalCenter = true
        } else {
            hasResolvedInitialCenter = true
        }
    }

    private fun consumeFocusRequest(
        map: MapLibreMap,
        focusRequest: MapFocusRequest?,
    ) {
        focusRequest ?: return
        if (focusRequest.id == lastFocusRequestId) return
        lastFocusRequestId = focusRequest.id
        if (!focusRequest.hasValidCoordinate()) return
        hasResolvedInitialCenter = true

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(focusRequest.latitude, focusRequest.longitude),
                MapDarkStyle.FOCUS_ZOOM,
            ),
            CAMERA_ANIMATION_MILLIS,
        )
    }

    private fun consumeCameraCommand(
        map: MapLibreMap,
        state: MapRenderState,
        cameraCommand: MapCameraCommand?,
        viewportHeightPx: Int,
    ) {
        cameraCommand ?: return
        if (cameraCommand.id == lastCameraCommandId) return
        lastCameraCommandId = cameraCommand.id

        when (cameraCommand) {
            is MapCameraCommand.ZoomBy -> {
                if (cameraCommand.delta.isFinite() && cameraCommand.delta != 0.0) {
                    hasResolvedInitialCenter = true
                    map.animateCamera(
                        CameraUpdateFactory.zoomBy(cameraCommand.delta),
                        CAMERA_ANIMATION_MILLIS,
                    )
                }
            }

            is MapCameraCommand.MoveToCurrentLocation -> {
                val location = MapRenderRules.currentLocation(state) ?: return
                hasResolvedInitialCenter = true
                val update =
                    cameraCommand.zoom?.takeIf(Double::isFinite)?.let { zoom ->
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            zoom,
                        )
                    } ?: CameraUpdateFactory.newLatLng(
                        LatLng(location.latitude, location.longitude),
                    )
                map.animateCamera(update, CAMERA_ANIMATION_MILLIS)
            }

            is MapCameraCommand.MoveToCoordinate -> {
                if (!cameraCommand.hasValidCoordinate()) return
                hasResolvedInitialCenter = true
                val coordinate = LatLng(cameraCommand.latitude, cameraCommand.longitude)
                val update =
                    cameraCommand.verticalPosition
                        ?.takeIf { it.isFinite() && it in 0.0..0.5 && viewportHeightPx > 0 }
                        ?.let { verticalPosition ->
                            val bottomPadding = viewportHeightPx * (1.0 - 2.0 * verticalPosition)
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition
                                    .Builder(map.cameraPosition)
                                    .target(coordinate)
                                    .zoom(cameraCommand.zoom?.takeIf(Double::isFinite) ?: map.cameraPosition.zoom)
                                    .padding(0.0, 0.0, 0.0, bottomPadding)
                                    .build(),
                            )
                        } ?: cameraCommand.zoom?.takeIf(Double::isFinite)?.let { zoom ->
                        CameraUpdateFactory.newLatLngZoom(coordinate, zoom)
                    } ?: CameraUpdateFactory.newLatLng(coordinate)
                map.animateCamera(update, CAMERA_ANIMATION_MILLIS)
            }

            is MapCameraCommand.MoveToBounds -> {
                if (!cameraCommand.hasValidBounds()) return
                hasResolvedInitialCenter = true
                val bounds = cameraCommand.bounds
                map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.from(
                            bounds.maxLatitude,
                            bounds.maxLongitude,
                            bounds.minLatitude,
                            bounds.minLongitude,
                        ),
                        0,
                    ),
                    CAMERA_ANIMATION_MILLIS,
                )
            }
        }
    }

    private fun MapCameraCommand.MoveToCoordinate.hasValidCoordinate(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0

    private fun MapCameraCommand.MoveToBounds.hasValidBounds(): Boolean =
        bounds.minLongitude.isFinite() &&
            bounds.minLatitude.isFinite() &&
            bounds.maxLongitude.isFinite() &&
            bounds.maxLatitude.isFinite() &&
            bounds.minLatitude <= bounds.maxLatitude

    private fun MapFocusRequest.hasValidCoordinate(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0

    private companion object {
        const val CAMERA_ANIMATION_MILLIS = 500
    }
}
