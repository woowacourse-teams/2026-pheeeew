import CoreLocation
import MapLibre
import Shared

enum MapLibreCamera {
    static func applyInitialCenter(
        _ center: IosMapCoordinate,
        to mapView: MLNMapView
    ) {
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: center.latitude, longitude: center.longitude),
            zoomLevel: MapLibreDarkStyle.initialZoom,
            animated: false
        )
    }

    static func focus(
        _ request: IosMapFocusRequest,
        on mapView: MLNMapView
    ) {
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: request.latitude, longitude: request.longitude),
            zoomLevel: MapLibreDarkStyle.focusZoom,
            animated: true
        )
    }

    @discardableResult
    static func apply(
        _ command: IosMapCameraCommand,
        currentLocation: IosCurrentLocation?,
        to mapView: MLNMapView,
        onCoordinateMoveStarted: (() -> Void)? = nil,
        onCoordinateMoveCompleted: (() -> Void)? = nil
    ) -> Bool {
        switch command.kind {
        case .zoomby:
            guard command.delta.isFinite, command.delta != 0 else { return false }
            let zoom = min(
                mapView.maximumZoomLevel,
                max(mapView.minimumZoomLevel, mapView.zoomLevel + command.delta)
            )
            mapView.setZoomLevel(zoom, animated: true)
            return true
        case .movetocurrentlocation:
            guard let location = currentLocation else { return false }
            let requestedZoom = command.zoom?.doubleValue
            let zoom = requestedZoom?.isFinite == true ? requestedZoom! : mapView.zoomLevel
            mapView.setCenter(
                CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude),
                zoomLevel: zoom,
                animated: true
            )
            return true
        case .movetocoordinate:
            guard command.latitude.isFinite,
                  command.longitude.isFinite,
                  (-90.0...90.0).contains(command.latitude),
                  (-180.0...180.0).contains(command.longitude) else { return false }
            let requestedZoom = command.zoom?.doubleValue
            let zoom = requestedZoom?.isFinite == true ? requestedZoom! : mapView.zoomLevel
            let coordinate = CLLocationCoordinate2D(
                latitude: command.latitude,
                longitude: command.longitude
            )
            if let verticalPosition = command.verticalPosition?.doubleValue,
               verticalPosition.isFinite,
               (0.0...0.5).contains(verticalPosition),
               !mapView.bounds.isEmpty {
                let camera = mapView.camera
                camera.centerCoordinate = coordinate
                camera.altitude = MLNAltitudeForZoomLevel(
                    zoom,
                    camera.pitch,
                    coordinate.latitude,
                    mapView.frame.size
                )
                let contentInset = mapView.contentInset
                let desiredY = mapView.bounds.height * verticalPosition
                let bottomPadding = max(
                    0,
                    mapView.bounds.height + contentInset.top - contentInset.bottom - (2 * desiredY)
                )
                // Keep detail padding inside this camera transition. Mutating contentInset here
                // lets MapLibre's automatic safe-area adjustment cancel the animated move.
                onCoordinateMoveStarted?()
                mapView.setCamera(
                    camera,
                    withDuration: cameraAnimationDuration,
                    animationTimingFunction: nil,
                    edgePadding: UIEdgeInsets(
                        top: 0,
                        left: 0,
                        bottom: bottomPadding,
                        right: 0
                    ),
                    completionHandler: onCoordinateMoveCompleted
                )
            } else {
                mapView.setCenter(
                    coordinate,
                    zoomLevel: zoom,
                    animated: true
                )
            }
            return true
        case .movetobounds:
            guard command.minLongitude.isFinite,
                  command.minLatitude.isFinite,
                  command.maxLongitude.isFinite,
                  command.maxLatitude.isFinite,
                  command.minLatitude <= command.maxLatitude else { return false }
            mapView.setVisibleCoordinateBounds(
                MLNCoordinateBounds(
                    sw: CLLocationCoordinate2D(
                        latitude: command.minLatitude,
                        longitude: command.minLongitude
                    ),
                    ne: CLLocationCoordinate2D(
                        latitude: command.maxLatitude,
                        longitude: command.maxLongitude
                    )
                ),
                animated: true
            )
            return true
        case .movetocamerastate:
            guard command.latitude.isFinite,
                  command.longitude.isFinite,
                  command.zoom?.doubleValue.isFinite == true,
                  (-90.0...90.0).contains(command.latitude),
                  (-180.0...180.0).contains(command.longitude) else { return false }
            let camera = mapView.camera
            camera.centerCoordinate = CLLocationCoordinate2D(
                latitude: command.latitude,
                longitude: command.longitude
            )
            camera.altitude = MLNAltitudeForZoomLevel(
                command.zoom!.doubleValue,
                camera.pitch,
                command.latitude,
                mapView.frame.size
            )
            mapView.setCamera(
                camera,
                withDuration: cameraAnimationDuration,
                animationTimingFunction: nil,
                edgePadding: .zero,
                completionHandler: nil
            )
            return true
        default:
            return false
        }
    }

    @discardableResult
    static func clearTransientPaddingPreservingViewport(on mapView: MLNMapView) -> Bool {
        let contentFrame = mapView.bounds.inset(by: mapView.contentInset)
        guard !contentFrame.isEmpty else { return false }
        // Change the logical center as padding is cleared so MapLibre does not treat this as a
        // no-op, while the geographic point at the normal content center stays visually fixed.
        let contentCenter = CGPoint(x: contentFrame.midX, y: contentFrame.midY)
        let unpaddedCenter = mapView.convert(contentCenter, toCoordinateFrom: mapView)
        guard CLLocationCoordinate2DIsValid(unpaddedCenter) else { return false }

        let camera = mapView.camera
        camera.centerCoordinate = unpaddedCenter
        camera.altitude = MLNAltitudeForZoomLevel(
            mapView.zoomLevel,
            camera.pitch,
            unpaddedCenter.latitude,
            mapView.frame.size
        )
        mapView.setCamera(
            camera,
            withDuration: 0,
            animationTimingFunction: nil,
            edgePadding: .zero,
            completionHandler: nil
        )
        return true
    }

    private static let cameraAnimationDuration: TimeInterval = 0.3
}
