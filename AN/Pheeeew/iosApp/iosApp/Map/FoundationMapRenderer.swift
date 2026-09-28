import MapLibre
import Shared
import UIKit
import CoreGraphics
import Foundation

final class FoundationMapRenderer: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
    let mapView: MLNMapView
    private let eventSink: FoundationIosMapEventSink
    private var pendingState: FoundationIosMapRenderUiModel?
    private var styleIsReady = false
    private var didSetInitialCamera = false
    private var initialCameraUsedFallback = false
    private var lastAppliedCameraCommandId: Int64 = 0
    private var currentLocationSource: MLNShapeSource?
    private var emotionPinSource: MLNShapeSource?
    private var lastEmotionPinImageKeys = Set<String>()
    private var fittedOrigin: CLLocationCoordinate2D?
    private var fittedSize: CGSize = .zero
    private var recordCameraBounds: RecordCameraBounds?
    private var didConfigureKoreanFontFaces = false

    init(eventSink: FoundationIosMapEventSink) {
        self.eventSink = eventSink
        mapView = MLNMapView(frame: .zero, styleURL: FoundationMapStyle.styleURL)
        super.init()
        mapView.delegate = self
        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleEmotionPinTap(_:)))
        tapRecognizer.cancelsTouchesInView = false
        tapRecognizer.delegate = self
        mapView.addGestureRecognizer(tapRecognizer)
        mapView.minimumZoomLevel = FoundationMapStyle.minimumZoom
        mapView.maximumZoomLevel = FoundationMapStyle.maximumZoom
        mapView.allowsScrolling = true
        mapView.allowsZooming = true
    }

    @objc private func handleEmotionPinTap(_ recognizer: UITapGestureRecognizer) {
        guard recognizer.state == .ended, styleIsReady, pendingState?.isRecordLocationPicking != true else { return }
        let point = recognizer.location(in: mapView)
        let features = mapView.visibleFeatures(at: point, styleLayerIdentifiers: Set(["foundation-emotion-pin-layer"]))
        if let id = features.first?.identifier as? NSNumber {
            eventSink.onEmotionPinClick(id: id.int64Value)
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }

    func update(state: FoundationIosMapRenderUiModel) {
        pendingState = state
        guard styleIsReady else { return }
        FoundationCurrentLocationLayer.update(currentLocation: state.currentLocation, source: currentLocationSource)
        updateEmotionPins(state)
        applyInitialCameraIfNeeded(state)
        if !applyRecordCamera(state) { applyCameraCommandIfNeeded(state) }
        publishViewportIfReady()
    }

    func releaseResources() {
        mapView.delegate = nil
        pendingState = nil
        styleIsReady = false
        currentLocationSource = nil
        emotionPinSource = nil
        lastEmotionPinImageKeys.removeAll()
    }

    private func styleJSON(_ style: MLNStyle, addingKoreanFonts fonts: (regular: URL, bold: URL)) -> String? {
        guard var styleJSON = (try? JSONSerialization.jsonObject(with: Data(style.styleJSON.utf8))) as? [String: Any] else {
            return nil
        }

        let fontFace: (URL) -> [[String: Any]] = { fontURL in [[
            "url": fontURL.absoluteString,
            "unicode-range": ["U+1100-11FF", "U+3130-318F", "U+A960-A97F", "U+AC00-D7AF", "U+D7B0-D7FF"],
        ]] }
        styleJSON["font-faces"] = [
            "Noto Sans Regular": fontFace(fonts.regular),
            "Noto Sans Italic": fontFace(fonts.regular),
            "Noto Sans Bold": fontFace(fonts.bold),
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: styleJSON) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func koreanFontURLs() -> (regular: URL, bold: URL)? {
        let resourcePath = "compose-resources/composeResources/pheeeew.shared.generated.resources/font"
        guard let regular = Bundle.main.url(forResource: "tap_noto_700", withExtension: "ttf", subdirectory: resourcePath) ?? Bundle.main.url(forResource: "tap_noto_700", withExtension: "ttf"),
              let bold = Bundle.main.url(forResource: "tap_noto_900", withExtension: "ttf", subdirectory: resourcePath) ?? Bundle.main.url(forResource: "tap_noto_900", withExtension: "ttf")
        else { return nil }
        return (regular, bold)
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        if !didConfigureKoreanFontFaces {
            didConfigureKoreanFontFaces = true
            if let fontURLs = koreanFontURLs(), let styleJSON = styleJSON(style, addingKoreanFonts: fontURLs) {
                mapView.styleJSON = styleJSON
                return
            }
        }
        FoundationMapStyle.applyMutedPalette(to: style)
        styleIsReady = true
        currentLocationSource = FoundationCurrentLocationLayer.install(on: style)
        emotionPinSource = installEmotionPinLayer(on: style)
        lastEmotionPinImageKeys.removeAll()
        eventSink.onMapRecovered()
        if let pendingState {
            FoundationCurrentLocationLayer.update(
                currentLocation: pendingState.currentLocation,
                source: currentLocationSource
            )
            applyInitialCameraIfNeeded(pendingState)
            if !applyRecordCamera(pendingState) { applyCameraCommandIfNeeded(pendingState) }
            updateEmotionPins(pendingState)
            publishViewportIfReady()
        }
    }

    func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
        eventSink.onStyleLoadFailed()
    }

    func mapViewRendererDidError(_ mapView: MLNMapView) {
        eventSink.onStyleLoadFailed()
    }

    func mapViewDidLayoutSubviews() {
        publishViewportIfReady()
    }

    func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
        publishRecordViewport()
        publishViewportIfReady()
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

    private func publishViewportIfReady() {
        guard styleIsReady, mapView.bounds.width > 0, mapView.bounds.height > 0,
              pendingState?.isRecordLocationPicking != true else { return }
        let bounds = mapView.visibleCoordinateBounds
        let queryBounds = EmotionMapBounds(
            minLongitude: bounds.sw.longitude,
            minLatitude: bounds.sw.latitude,
            maxLongitude: bounds.ne.longitude,
            maxLatitude: bounds.ne.latitude
        )
        if queryBounds.isValid() { eventSink.onViewportChanged(bounds: queryBounds) }
    }

    private func installEmotionPinLayer(on style: MLNStyle) -> MLNShapeSource {
        let source = MLNShapeSource(identifier: "foundation-emotion-pin-source", features: [], options: nil)
        style.addSource(source)

        let layer = MLNSymbolStyleLayer(identifier: "foundation-emotion-pin-layer", source: source)
        layer.iconImageName = NSExpression(mglJSONObject: ["get", "imageKey"])
        layer.iconRotation = NSExpression(mglJSONObject: ["get", "rotationDegrees"])
        layer.iconScale = NSExpression(forConstantValue: 1.0)
        layer.iconAllowsOverlap = NSExpression(forConstantValue: true)
        layer.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        style.addLayer(layer)
        return source
    }

    private func updateEmotionPins(_ state: FoundationIosMapRenderUiModel) {
        guard let style = mapView.style, let emotionPinSource else { return }
        let imageKeys = Set(state.emotionPinSymbolImages.map(\.key))
        if imageKeys != lastEmotionPinImageKeys {
            lastEmotionPinImageKeys.subtracting(imageKeys).forEach { style.removeImage(forName: $0) }
            for image in state.emotionPinSymbolImages {
                guard let uiImage = image.toUIImage(scale: mapView.window?.screen.scale ?? UIScreen.main.scale) else { continue }
                style.setImage(uiImage, forName: image.key)
            }
            lastEmotionPinImageKeys = imageKeys
        }

        let features = state.emotionPinCoordinates.map { pin -> MLNPointFeature in
            let feature = MLNPointFeature()
            feature.coordinate = CLLocationCoordinate2D(latitude: pin.latitude, longitude: pin.longitude)
            feature.identifier = NSNumber(value: pin.id)
            feature.attributes = [
                "id": pin.id,
                "imageKey": pin.imageKey,
                "rotationDegrees": pin.rotationDegrees,
            ]
            return feature
        }
        emotionPinSource.shape = MLNShapeCollectionFeature(shapes: features)
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

private extension FoundationIosMapSymbolImageUiModel {
    func toUIImage(scale: CGFloat) -> UIImage? {
        let imageWidth = Int(width)
        let imageHeight = Int(height)
        let byteCount = imageWidth * imageHeight * 4
        guard imageWidth > 0, imageHeight > 0, rgba.size == Int32(byteCount) else { return nil }

        var pixelBytes = Data(count: byteCount)
        pixelBytes.withUnsafeMutableBytes { buffer in
            guard let baseAddress = buffer.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return }
            for index in 0..<byteCount {
                baseAddress[index] = UInt8(bitPattern: rgba.get(index: Int32(index)))
            }
            for index in stride(from: 0, to: byteCount, by: 4) {
                let alpha = UInt16(baseAddress[index + 3])
                baseAddress[index] = UInt8((UInt16(baseAddress[index]) * alpha + 127) / 255)
                baseAddress[index + 1] = UInt8((UInt16(baseAddress[index + 1]) * alpha + 127) / 255)
                baseAddress[index + 2] = UInt8((UInt16(baseAddress[index + 2]) * alpha + 127) / 255)
            }
        }

        guard let provider = CGDataProvider(data: pixelBytes as CFData),
              let cgImage = CGImage(
                  width: imageWidth,
                  height: imageHeight,
                  bitsPerComponent: 8,
                  bitsPerPixel: 32,
                  bytesPerRow: imageWidth * 4,
                  space: CGColorSpaceCreateDeviceRGB(),
                  bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue).union(.byteOrder32Big),
                  provider: provider,
                  decode: nil,
                  shouldInterpolate: true,
                  intent: .defaultIntent
              ) else { return nil }
        return UIImage(cgImage: cgImage, scale: scale, orientation: .up)
    }
}
