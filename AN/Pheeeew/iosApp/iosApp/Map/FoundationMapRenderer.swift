import MapLibre
import Shared
import UIKit

final class FoundationMapRenderer: NSObject, MLNMapViewDelegate {
    let mapView: MLNMapView
    private let eventSink: FoundationIosMapEventSink
    private var pendingState: FoundationIosMapRenderUiModel?
    private var styleIsReady = false
    private var didSetInitialCamera = false
    private var initialCameraUsedFallback = false
    private var lastAppliedCameraCommandId: Int64 = 0
    private var currentLocationSource: MLNShapeSource?
    private var fittedOrigin: CLLocationCoordinate2D?
    private var fittedSize: CGSize = .zero
    private var recordCameraBounds: RecordCameraBounds?

    init(eventSink: FoundationIosMapEventSink) {
        self.eventSink = eventSink
        mapView = MLNMapView(frame: .zero, styleURL: FoundationMapStyle.styleURL)
        super.init()
        mapView.delegate = self
        mapView.minimumZoomLevel = FoundationMapStyle.minimumZoom
        mapView.maximumZoomLevel = FoundationMapStyle.maximumZoom
        mapView.allowsScrolling = true
        mapView.allowsZooming = true
    }

    func update(state: FoundationIosMapRenderUiModel) {
        pendingState = state
        guard styleIsReady else { return }
        FoundationCurrentLocationLayer.update(currentLocation: state.currentLocation, source: currentLocationSource)
        applyInitialCameraIfNeeded(state)
        if !applyRecordCamera(state) { applyCameraCommandIfNeeded(state) }
    }

    func releaseResources() {
        mapView.delegate = nil
        pendingState = nil
        styleIsReady = false
        currentLocationSource = nil
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        styleIsReady = true
        currentLocationSource = FoundationCurrentLocationLayer.install(on: style)
        eventSink.onMapRecovered()
        if let pendingState {
            FoundationCurrentLocationLayer.update(
                currentLocation: pendingState.currentLocation,
                source: currentLocationSource
            )
            applyInitialCameraIfNeeded(pendingState)
            if !applyRecordCamera(pendingState) { applyCameraCommandIfNeeded(pendingState) }
        }
    }

    func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
        eventSink.onStyleLoadFailed()
    }

    func mapViewRendererDidError(_ mapView: MLNMapView) {
        eventSink.onStyleLoadFailed()
    }

    func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
        publishRecordViewport()
    }

    func mapViewRegionIsChanging(_ mapView: MLNMapView) {
        publishRecordViewport()
    }

    func mapView(_ mapView: MLNMapView, shouldChangeFrom oldCamera: MLNMapCamera, to newCamera: MLNMapCamera) -> Bool {
        guard pendingState?.isRecordLocationPicking == true, let bounds = recordCameraBounds else { return true }
        let center = newCamera.centerCoordinate
        return bounds.contains(coordinate: GeoCoordinate(latitude: center.latitude, longitude: center.longitude))
    }

    func mapViewDidFinishRenderingFrame(_ mapView: MLNMapView, fullyRendered: Bool) {
        if let state = pendingState, state.isRecordLocationPicking {
            _ = applyRecordCamera(state)
        }
    }

    private func applyRecordCamera(_ state: FoundationIosMapRenderUiModel) -> Bool {
        let picking = state.isRecordLocationPicking
        mapView.allowsScrolling = true
        mapView.allowsZooming = true
        mapView.allowsRotating = !picking
        mapView.allowsTilting = !picking
        guard picking else {
            if fittedOrigin != nil { mapView.minimumZoomLevel = FoundationMapStyle.minimumZoom }
            fittedOrigin = nil
            recordCameraBounds = nil
            return false
        }
        lastAppliedCameraCommandId = state.cameraCommandId
        guard let origin = state.recordOrigin, mapView.bounds.width > 0, mapView.bounds.height > 0 else { return true }
        let center = CLLocationCoordinate2D(latitude: origin.latitude, longitude: origin.longitude)
        if fittedOrigin?.latitude != center.latitude || fittedOrigin?.longitude != center.longitude || fittedSize != mapView.bounds.size {
            fittedOrigin = center
            fittedSize = mapView.bounds.size
            recordCameraBounds = RecordMapGeometryKt.recordCameraBounds(
                origin: GeoCoordinate(latitude: center.latitude, longitude: center.longitude)
            )
            mapView.minimumZoomLevel = FoundationMapStyle.minimumZoom
            let latitudeDelta = 500.0 / 6_371_000.0 * 180.0 / .pi
            let longitudeDelta = latitudeDelta / cos(center.latitude * .pi / 180.0)
            let bounds = MLNCoordinateBounds(
                sw: CLLocationCoordinate2D(latitude: center.latitude - latitudeDelta, longitude: center.longitude - longitudeDelta),
                ne: CLLocationCoordinate2D(latitude: center.latitude + latitudeDelta, longitude: center.longitude + longitudeDelta)
            )
            let camera = mapView.camera
            camera.heading = 0
            camera.pitch = 0
            mapView.setCamera(camera, animated: false)
            mapView.setVisibleCoordinateBounds(
                bounds,
                edgePadding: UIEdgeInsets(top: mapView.bounds.height * 0.22, left: mapView.bounds.width * 0.12, bottom: mapView.bounds.height * 0.22, right: mapView.bounds.width * 0.12),
                animated: false,
                completionHandler: nil
            )
            mapView.minimumZoomLevel = mapView.zoomLevel - 1.0
        }
        publishRecordViewport()
        return true
    }

    private func publishRecordViewport() {
        guard let state = pendingState, state.isRecordLocationPicking, let origin = state.recordOrigin else { return }
        let centerCoordinate = CLLocationCoordinate2D(latitude: origin.latitude, longitude: origin.longitude)
        let northCoordinate = CLLocationCoordinate2D(latitude: origin.latitude + 500.0 / 6_371_000.0 * 180.0 / .pi, longitude: origin.longitude)
        let center = mapView.convert(centerCoordinate, toPointTo: mapView)
        let north = mapView.convert(northCoordinate, toPointTo: mapView)
        let radius = hypot(north.x - center.x, north.y - center.y)
        guard radius > 0, radius.isFinite else { return }
        eventSink.onRecordViewportChanged(centerX: Float(center.x), centerY: Float(center.y), radius: Float(radius))
    }

    private func applyInitialCameraIfNeeded(_ state: FoundationIosMapRenderUiModel) {
        guard !didSetInitialCamera || (initialCameraUsedFallback && state.currentLocation != nil) else { return }
        let center = state.currentLocation.map {
            CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
        } ?? CLLocationCoordinate2D(
            latitude: state.fallbackCenter.latitude,
            longitude: state.fallbackCenter.longitude
        )
        mapView.setCenter(center, zoomLevel: FoundationMapStyle.initialZoom, animated: false)
        didSetInitialCamera = true
        initialCameraUsedFallback = state.currentLocation == nil
    }

    private func applyCameraCommandIfNeeded(_ state: FoundationIosMapRenderUiModel) {
        guard state.cameraCommandId > lastAppliedCameraCommandId else { return }
        lastAppliedCameraCommandId = state.cameraCommandId
        switch state.cameraCommandType {
        case 1:
            mapView.setCenter(
                CLLocationCoordinate2D(latitude: state.cameraLatitude, longitude: state.cameraLongitude),
                zoomLevel: state.cameraCommandValue,
                animated: true
            )
        case 2:
            mapView.setZoomLevel(mapView.zoomLevel + state.cameraCommandValue, animated: true)
        default:
            break
        }
    }
}
