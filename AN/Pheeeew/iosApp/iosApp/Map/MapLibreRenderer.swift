import CoreLocation
import MapLibre
import Shared
import UIKit
import QuartzCore

final class MapLibreRenderer: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
    let mapView: MLNMapView

    private let eventSink: IosMapEventSink
    private var pendingState: IosMapRenderState?
    private var sighSource: MLNShapeSource?
    private var sighLayer: MLNSymbolStyleLayer?
    private var lastRenderedSighs: [RenderedSigh]?
    private var currentLocationSource: MLNShapeSource?
    private var styleIsReady = false
    private var didApplyProvisionalCamera = false
    private var didResolveInitialCamera = false
    private var lastCameraCommandID: Int64?
    private var lastReceivedCameraCommandID: Int64?
    private var lastFocusRequestID: String?
    private var pendingCameraCommands: [IosMapCameraCommand] = []
    private var cameraIsIdle = true
    private var lastPublishedProjection: ProjectionSnapshot?

    private static let userCameraReasonMask: UInt =
        (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) |
        (1 << 5) | (1 << 6) | (1 << 7) | (1 << 8)

    init(eventSink: IosMapEventSink) {
        self.eventSink = eventSink
        mapView = MLNMapView(frame: .zero, styleURL: MapLibreDarkStyle.styleURL)
        super.init()

        mapView.backgroundColor = MapLibreDarkStyle.mapBackground
        mapView.delegate = self
        mapView.showsUserLocation = false
        mapView.allowsScrolling = true
        mapView.allowsZooming = true
        mapView.minimumZoomLevel = MapLibreDarkStyle.minimumZoom
        mapView.maximumZoomLevel = MapLibreDarkStyle.maximumZoom
        mapView.compassView.isHidden = false

        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleMapTap(_:)))
        tapRecognizer.cancelsTouchesInView = false
        tapRecognizer.delegate = self
        mapView.addGestureRecognizer(tapRecognizer)
    }

    func update(state: IosMapRenderState) {
        dispatchPrecondition(condition: .onQueue(.main))
        pendingState = state
        if let command = state.cameraCommand, command.id != lastReceivedCameraCommandID {
            lastReceivedCameraCommandID = command.id
            pendingCameraCommands.append(command)
        }
        guard styleIsReady else { return }

        updateSighs(state.sighMarkers)
        updateCurrentLocation(state.currentLocation)
        applyCameraState(state)
        publishProjection(cameraIdle: cameraIsIdle)
    }

    func releaseResources() {
        mapView.delegate = nil
        pendingState = nil
        sighSource = nil
        sighLayer = nil
        lastRenderedSighs = nil

        currentLocationSource = nil
        pendingCameraCommands.removeAll()
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        styleIsReady = false
        MapLibreDarkStyle.addKoreanPoiLayerIfPossible(to: style)
        addRuntimeSourcesAndLayers(to: style)
        styleIsReady = true
        eventSink.onMapRecovered()

        if let state = pendingState {
            update(state: state)
        }
    }

    func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
        eventSink.onStyleLoadFailed()
    }

    func mapViewRendererDidError(_ mapView: MLNMapView) {
        eventSink.onStyleLoadFailed()
    }

    func mapView(
        _ mapView: MLNMapView,
        regionWillChangeWith reason: MLNCameraChangeReason,
        animated: Bool
    ) {
        cameraIsIdle = false
        eventSink.onProjectionChanged(points: projectionPoints(), cameraIdle: false)
        if reason.rawValue & Self.userCameraReasonMask != 0 {
            didResolveInitialCamera = true
        }
    }

    func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
        cameraIsIdle = true
        eventSink.onProjectionChanged(points: projectionPoints(), cameraIdle: true)
        let bounds = mapView.visibleCoordinateBounds
        eventSink.onBoundsChanged(
            minLongitude: bounds.sw.longitude,
            minLatitude: bounds.sw.latitude,
            maxLongitude: bounds.ne.longitude,
            maxLatitude: bounds.ne.latitude
        )
    }

    private func publishProjection(cameraIdle: Bool) {
        guard styleIsReady else { return }
        let snapshot = ProjectionSnapshot(points: projectionPoints(), cameraIdle: cameraIdle)
        if snapshot == lastPublishedProjection { return }
        lastPublishedProjection = snapshot
        let points = snapshot.points
        eventSink.onProjectionChanged(points: points, cameraIdle: cameraIdle)
    }

    private func projectionPoints() -> [IosMapScreenPoint] {
        guard let focus = pendingState?.focusRequest else { return [] }
        // MapLibre reports UIKit points, while Compose Canvas coordinates are pixels on iOS.
        // Convert the projected map points before sending them to the Compose animation overlay.
        let screenScale = mapView.window?.screen.scale ?? mapView.contentScaleFactor
        let coordinate = CLLocationCoordinate2D(latitude: focus.latitude, longitude: focus.longitude)
        let point = mapView.convert(coordinate, toPointTo: mapView)
        return [
            IosMapScreenPoint(
                id: focus.id,
                xPx: Double(point.x * screenScale),
                yPx: Double(point.y * screenScale)
            )
        ]
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc
    private func handleMapTap(_ recognizer: UITapGestureRecognizer) {
        guard recognizer.state == .ended else { return }
        let point = recognizer.location(in: mapView)
        let hitRect = CGRect(x: point.x - 22, y: point.y - 22, width: 44, height: 44)
        let features = mapView.visibleFeatures(
            in: hitRect,
            styleLayerIdentifiers: [MapLibreDarkStyle.sighLayerID],
            predicate: nil
        )
        guard let feature = features.first else { return }

        if let id = feature.attribute(forKey: "id") as? String {
            eventSink.onSighClick(id: id)
        } else if let id = feature.identifier as? String {
            eventSink.onSighClick(id: id)
        }
    }

    private func addRuntimeSourcesAndLayers(to style: MLNStyle) {
        let starImages: [(String, UIColor)] = [
            (MapLibreDarkStyle.starBlueImageID, MapLibreDarkStyle.starBlueColor),
            (MapLibreDarkStyle.starExistingImageID, MapLibreDarkStyle.starExistingColor),
            (MapLibreDarkStyle.starOrangeImageID, MapLibreDarkStyle.starOrangeColor),
            (MapLibreDarkStyle.starUnknownImageID, MapLibreDarkStyle.starUnknownColor),
        ]
        for (imageID, color) in starImages {
            style.setImage(MapLibreDarkStyle.makeSighStarImage(color: color), forName: imageID)
        }

        let sighSource = MLNShapeSource(
            identifier: MapLibreDarkStyle.sighSourceID,
            features: [],
            options: [.clustered: false]
        )
        style.addSource(sighSource)
        self.sighSource = sighSource

        let sighLayer = MLNSymbolStyleLayer(
            identifier: MapLibreDarkStyle.sighLayerID,
            source: sighSource
        )
        sighLayer.iconImageName = NSExpression(mglJSONObject: ["get", "starImage"])
        sighLayer.iconScale = NSExpression(mglJSONObject: ["get", "starScale"])
        sighLayer.iconOpacity = NSExpression(mglJSONObject: ["get", "starOpacity"])
        sighLayer.iconAllowsOverlap = NSExpression(forConstantValue: true)
        sighLayer.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        style.addLayer(sighLayer)
        self.sighLayer = sighLayer

        let currentSource = MLNShapeSource(
            identifier: MapLibreDarkStyle.currentLocationSourceID,
            features: [],
            options: nil
        )
        style.addSource(currentSource)
        currentLocationSource = currentSource

        let accuracyLayer = MLNFillStyleLayer(
            identifier: MapLibreDarkStyle.currentLocationAccuracyLayerID,
            source: currentSource
        )
        accuracyLayer.predicate = NSPredicate(format: "kind == 'accuracy'")
        accuracyLayer.fillColor = NSExpression(forConstantValue: MapLibreDarkStyle.locationBlue)
        accuracyLayer.fillOpacity = NSExpression(forConstantValue: 0.14)
        style.addLayer(accuracyLayer)

        let borderLayer = MLNCircleStyleLayer(
            identifier: MapLibreDarkStyle.currentLocationBorderLayerID,
            source: currentSource
        )
        borderLayer.predicate = NSPredicate(format: "kind == 'point'")
        borderLayer.circleColor = NSExpression(forConstantValue: UIColor.white)
        borderLayer.circleRadius = NSExpression(forConstantValue: 9)
        style.addLayer(borderLayer)

        let centerLayer = MLNCircleStyleLayer(
            identifier: MapLibreDarkStyle.currentLocationLayerID,
            source: currentSource
        )
        centerLayer.predicate = NSPredicate(format: "kind == 'point'")
        centerLayer.circleColor = NSExpression(forConstantValue: MapLibreDarkStyle.locationBlue)
        centerLayer.circleRadius = NSExpression(forConstantValue: 6)
        style.addLayer(centerLayer)
    }

    private func updateSighs(_ markers: [IosSighMarker]) {
        let renderedSighs = markers.map {
            RenderedSigh(
                id: $0.id,
                latitude: $0.latitude,
                longitude: $0.longitude,
                imageKey: $0.visual.imageKey,
                scale: $0.visual.scale,
                opacity: $0.visual.opacity
            )
        }
        guard renderedSighs != lastRenderedSighs else { return }
        lastRenderedSighs = renderedSighs
        let features = markers.map { marker -> MLNPointFeature in
            let feature = MLNPointFeature()
            feature.coordinate = CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)
            feature.identifier = marker.id as NSString
            feature.attributes = [
                "id": marker.id,
                "starImage": marker.visual.imageKey,
                "starScale": marker.visual.scale,
                "starOpacity": marker.visual.opacity,
            ]
            return feature
        }
        sighSource?.shape = MLNShapeCollectionFeature(shapes: features)
    }

    private struct RenderedSigh: Equatable {
        let id: String
        let latitude: Double
        let longitude: Double
        let imageKey: String
        let scale: Float
        let opacity: Float
    }

    private struct ProjectionSnapshot: Equatable {
        let points: [IosMapScreenPoint]
        let cameraIdle: Bool
    }

    private func updateCurrentLocation(_ location: IosCurrentLocation?) {
        guard let location else {
            currentLocationSource?.shape = MLNShapeCollectionFeature(shapes: [])
            return
        }

        let center = MLNPointFeature()
        center.coordinate = CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude)
        center.attributes = ["kind": "point"]

        var shapes: [MLNShape & MLNFeature] = [center]
        if location.accuracyMeters > 0 {
            var coordinates = accuracyRing(
                latitude: location.latitude,
                longitude: location.longitude,
                radiusMeters: location.accuracyMeters
            )
            let accuracy = MLNPolygonFeature(coordinates: &coordinates, count: UInt(coordinates.count))
            accuracy.attributes = ["kind": "accuracy"]
            shapes.insert(accuracy, at: 0)
        }
        currentLocationSource?.shape = MLNShapeCollectionFeature(shapes: shapes)
    }

    private func applyCameraState(_ state: IosMapRenderState) {
        if !didResolveInitialCamera, let center = state.initialCenter {
            if state.initialCenterIsProvisional {
                if !didApplyProvisionalCamera {
                    MapLibreCamera.applyInitialCenter(center, to: mapView)
                    didApplyProvisionalCamera = true
                }
            } else {
                MapLibreCamera.applyInitialCenter(center, to: mapView)
                didResolveInitialCamera = true
            }
        }

        if let focus = state.focusRequest, focus.id != lastFocusRequestID {
            cameraIsIdle = false
            lastFocusRequestID = focus.id
            MapLibreCamera.focus(focus, on: mapView)
            didResolveInitialCamera = true
        }

        while !pendingCameraCommands.isEmpty {
            cameraIsIdle = false
            let command = pendingCameraCommands.removeFirst()
            guard command.id != lastCameraCommandID else { continue }
            lastCameraCommandID = command.id
            if MapLibreCamera.apply(command, currentLocation: state.currentLocation, to: mapView) {
                didResolveInitialCamera = true
            }
        }
    }

    private func accuracyRing(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ) -> [CLLocationCoordinate2D] {
        let earthRadiusMeters = 6_371_008.8
        let angularDistance = radiusMeters / earthRadiusMeters
        let latitudeRadians = latitude * .pi / 180
        let longitudeRadians = longitude * .pi / 180

        return (0...64).map { index in
            let bearing = Double(index) * 2 * .pi / 64
            let targetLatitude = asin(
                sin(latitudeRadians) * cos(angularDistance)
                    + cos(latitudeRadians) * sin(angularDistance) * cos(bearing)
            )
            let targetLongitude = longitudeRadians + atan2(
                sin(bearing) * sin(angularDistance) * cos(latitudeRadians),
                cos(angularDistance) - sin(latitudeRadians) * sin(targetLatitude)
            )
            return CLLocationCoordinate2D(
                latitude: targetLatitude * 180 / .pi,
                longitude: targetLongitude * 180 / .pi
            )
        }
    }

}
