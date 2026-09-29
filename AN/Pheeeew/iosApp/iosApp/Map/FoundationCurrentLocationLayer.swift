import CoreLocation
import MapLibre
import Shared
import UIKit

enum FoundationCurrentLocationLayer {
    private static let sourceID = "foundation-current-location-source"
    private static let borderLayerID = "foundation-current-location-border"
    private static let dotLayerID = "foundation-current-location-dot"
    private static let locationBlue = UIColor(red: 47 / 255, green: 128 / 255, blue: 237 / 255, alpha: 1)

    static func install(on style: MLNStyle) -> MLNShapeSource {
        let source = MLNShapeSource(identifier: sourceID, features: [], options: nil)
        style.addSource(source)

        let borderLayer = MLNCircleStyleLayer(identifier: borderLayerID, source: source)
        borderLayer.circleColor = NSExpression(forConstantValue: UIColor.white)
        borderLayer.circleRadius = NSExpression(forConstantValue: 9)
        style.addLayer(borderLayer)

        let dotLayer = MLNCircleStyleLayer(identifier: dotLayerID, source: source)
        dotLayer.circleColor = NSExpression(forConstantValue: locationBlue)
        dotLayer.circleRadius = NSExpression(forConstantValue: 6)
        style.addLayer(dotLayer)
        return source
    }

    static func update(
        currentLocation: FoundationIosCurrentLocationUiModel?,
        source: MLNShapeSource?
    ) {
        guard let currentLocation,
              currentLocation.latitude.isFinite,
              currentLocation.longitude.isFinite else {
            source?.shape = MLNShapeCollectionFeature(shapes: [])
            return
        }

        let point = MLNPointFeature()
        point.coordinate = CLLocationCoordinate2D(
            latitude: currentLocation.latitude,
            longitude: currentLocation.longitude
        )
        source?.shape = MLNShapeCollectionFeature(shapes: [point])
    }
}
