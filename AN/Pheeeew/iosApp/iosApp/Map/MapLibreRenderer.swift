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
    private var sighLayers: [MLNSymbolStyleLayer] = []
    private var sighPulseDisplayLink: CADisplayLink?
    private var sighPulseStartedAt = CACurrentMediaTime()
    private var currentLocationSource: MLNShapeSource?
    private var styleIsReady = false
    private var didApplyProvisionalCamera = false
    private var didResolveInitialCamera = false
    private var lastCameraCommandID: Int64?
    private var lastReceivedCameraCommandID: Int64?
    private var lastFocusRequestID: String?
    private var pendingCameraCommands: [IosMapCameraCommand] = []
    private var cameraIsIdle = true
    private var lastPublishedProjectionSignature: String?
    private var isInBackground = false
    private var lifecycleObservers: [NSObjectProtocol] = []

    private static let userCameraReasonMask: UInt =
        (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) |
        (1 << 5) | (1 << 6) | (1 << 7) | (1 << 8)

    init(eventSink: IosMapEventSink) {
        self.eventSink = eventSink
        mapView = MLNMapView(frame: .zero, styleURL: MapLibreDarkStyle.styleURL)
        super.init()

        registerApplicationLifecycleObservers()

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
        sighLayers.removeAll()

        sighPulseDisplayLink?.invalidate()
        sighPulseDisplayLink = nil

        let center = NotificationCenter.default
        lifecycleObservers.forEach(center.removeObserver)
        lifecycleObservers.removeAll()

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
        let points = projectionPoints()
        let signature = points.map { "\($0.id):\($0.xPx):\($0.yPx)" }.joined(separator: "|") + ":\(cameraIdle)"
        if signature == lastPublishedProjectionSignature { return }
        lastPublishedProjectionSignature = signature
        eventSink.onProjectionChanged(points: points, cameraIdle: cameraIdle)
    }

    private func projectionPoints() -> [IosMapScreenPoint] {
        guard let state = pendingState else { return [] }
        // MapLibre reports UIKit points, while Compose Canvas coordinates are pixels on iOS.
        // Convert the projected map points before sending them to the Compose animation overlay.
        let screenScale = mapView.window?.screen.scale ?? mapView.contentScaleFactor
        var targets: [(String, CLLocationCoordinate2D)] = state.sighMarkers.map {
            ($0.id, CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude))
        }
        if let focus = state.focusRequest, !targets.contains(where: { $0.0 == focus.id }) {
            targets.append((focus.id, CLLocationCoordinate2D(latitude: focus.latitude, longitude: focus.longitude)))
        }
        return targets.map { id, coordinate in
            let point = mapView.convert(coordinate, toPointTo: mapView)
            return IosMapScreenPoint(
                id: id,
                xPx: Double(point.x * screenScale),
                yPx: Double(point.y * screenScale)
            )
        }
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
            styleLayerIdentifiers: Set(Self.sighLayerIDs),
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
        style.setImage(MapLibreDarkStyle.makeSighStarImage(), forName: MapLibreDarkStyle.sighImageID)

        let sighSource = MLNShapeSource(
            identifier: MapLibreDarkStyle.sighSourceID,
            features: [],
            options: [.clustered: false]
        )
        style.addSource(sighSource)
        self.sighSource = sighSource

        sighLayers.removeAll(keepingCapacity: true)
        for group in 0..<Self.sighPulseGroupCount {
            let sighLayer = MLNSymbolStyleLayer(
                identifier: Self.sighLayerID(for: group),
                source: sighSource
            )
            sighLayer.predicate = NSPredicate(format: "%K == %d", Self.sighPulseGroupProperty, group)
            sighLayer.iconImageName = NSExpression(forConstantValue: MapLibreDarkStyle.sighImageID)
            sighLayer.iconScale = NSExpression(forConstantValue: 0.3)
            sighLayer.iconAllowsOverlap = NSExpression(forConstantValue: true)
            sighLayer.iconIgnoresPlacement = NSExpression(forConstantValue: true)
            style.addLayer(sighLayer)
            sighLayers.append(sighLayer)
        }
        startSighPulse()

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
        let features = markers.map { marker -> MLNPointFeature in
            let feature = MLNPointFeature()
            feature.coordinate = CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)
            feature.identifier = marker.id as NSString
            feature.attributes = [
                "id": marker.id,
                Self.sighPulseGroupProperty: Self.pulseGroup(for: marker.id),
            ]
            return feature
        }
        sighSource?.shape = MLNShapeCollectionFeature(shapes: features)
    }

    private func startSighPulse() {
        sighPulseDisplayLink?.invalidate()
        sighPulseStartedAt = CACurrentMediaTime()

        let displayLink = CADisplayLink(
            target: self,
            selector: #selector(updateSighPulse)
        )

        displayLink.preferredFramesPerSecond = 30
        displayLink.add(to: .main, forMode: .common)
        displayLink.isPaused = isInBackground

        sighPulseDisplayLink = displayLink
    }

    @objc
    private func updateSighPulse() {
        let elapsed = CACurrentMediaTime() - sighPulseStartedAt
        for (group, sighLayer) in sighLayers.enumerated() {
            let phase = (elapsed / Self.sighPulsePeriod + Double(group) / Double(Self.sighPulseGroupCount))
                .truncatingRemainder(dividingBy: 1)
            let wave = (sin(phase * 2 * .pi) + 1) / 2
            let pulse = wave * wave * (3 - (2 * wave))
            sighLayer.iconScale = NSExpression(forConstantValue: 0.24 + (pulse * 0.12))
            sighLayer.iconOpacity = NSExpression(forConstantValue: 0.72 + (pulse * 0.28))
        }
    }

    private func pauseAnimations() {
        isInBackground = true
        sighPulseDisplayLink?.isPaused = true
    }

    private func resumeAnimations() {
        isInBackground = false
        sighPulseDisplayLink?.isPaused = false
    }

    private static let sighPulseGroupCount = 12
    private static let sighPulsePeriod = 1.8
    private static let sighPulseGroupProperty = "sighPulseGroup"
    private static var sighLayerIDs: [String] {
        (0..<sighPulseGroupCount).map { sighLayerID(for: $0) }
    }

    private static func sighLayerID(for group: Int) -> String {
        group == 0 ? MapLibreDarkStyle.sighLayerID : "\(MapLibreDarkStyle.sighLayerID)-\(group)"
    }

    private static func pulseGroup(for markerID: String) -> Int {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in markerID.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1_099_511_628_211
        }
        return Int(hash % UInt64(sighPulseGroupCount))
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

    private func registerApplicationLifecycleObservers() {
        let center = NotificationCenter.default

        lifecycleObservers = [
            center.addObserver(
                forName: UIApplication.didEnterBackgroundNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.pauseAnimations()
            },

            center.addObserver(
                forName: UIApplication.willEnterForegroundNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.resumeAnimations()
            },
        ]
    }
}
