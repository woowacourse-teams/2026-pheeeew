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
        applyCameraCommandIfNeeded(state)
        publishCameraState()
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
            applyCameraCommandIfNeeded(pendingState)
        }
        publishCameraState()
    }

    func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
        eventSink.onStyleLoadFailed()
    }

    func mapViewRendererDidError(_ mapView: MLNMapView) {
        eventSink.onStyleLoadFailed()
    }

    func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
        publishCameraState()
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

    private func publishCameraState() {
        guard styleIsReady else { return }
        eventSink.onCameraStateChanged(
            latitude: mapView.centerCoordinate.latitude,
            longitude: mapView.centerCoordinate.longitude,
            zoom: mapView.zoomLevel
        )
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
