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
        to mapView: MLNMapView
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
            if let verticalPosition = command.verticalPosition?.doubleValue,
               verticalPosition.isFinite,
               (0.0...0.5).contains(verticalPosition) {
                mapView.contentInset = UIEdgeInsets(
                    top: 0,
                    left: 0,
                    bottom: mapView.bounds.height * (1.0 - 2.0 * verticalPosition),
                    right: 0
                )
            }
            mapView.setCenter(
                CLLocationCoordinate2D(
                    latitude: command.latitude,
                    longitude: command.longitude
                ),
                zoomLevel: zoom,
                animated: true
            )
            return true
        case .movetobounds:
            guard command.minLongitude.isFinite,
                  command.minLatitude.isFinite,
                  command.maxLongitude.isFinite,
                  command.maxLatitude.isFinite,
                  command.minLatitude <= command.maxLatitude else { return false }
            mapView.contentInset = .zero
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
        default:
            return false
        }
    }
}
