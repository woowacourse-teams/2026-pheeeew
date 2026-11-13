import MapLibre
import Shared
import UIKit

final class FoundationRecordStampLayer {
    private var source: MLNShapeSource?
    private var lastPin: FoundationIosEmotionPinCoordinateUiModel?

    func install(on style: MLNStyle) {
        lastPin = nil
        let source = MLNShapeSource(identifier: "record-stamp", features: [], options: nil)
        self.source = source
        style.addSource(source)
        let halo = MLNCircleStyleLayer(identifier: "record-stamp-halo", source: source)
        halo.circleRadius = NSExpression(forConstantValue: 34)
        halo.circleColor = NSExpression(forConstantValue: UIColor(red: 1, green: 243.0 / 255, blue: 191.0 / 255, alpha: 0.3))
        halo.circleStrokeWidth = NSExpression(forConstantValue: 2)
        halo.circleStrokeColor = NSExpression(forConstantValue: UIColor(red: 229.0 / 255, green: 190.0 / 255, blue: 40.0 / 255, alpha: 1))
        style.addLayer(halo)
        let icon = MLNSymbolStyleLayer(identifier: "record-stamp-icon", source: source)
        icon.iconScale = NSExpression(forConstantValue: 62.0 / 40)
        icon.iconAllowsOverlap = NSExpression(forConstantValue: true)
        icon.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        style.addLayer(icon)
    }

    func update(_ pin: FoundationIosEmotionPinCoordinateUiModel?, scale: Float, style: MLNStyle) {
        (style.layer(withIdentifier: "record-stamp-icon") as? MLNSymbolStyleLayer)?.iconScale = NSExpression(forConstantValue: 62.0 / 40 * Double(scale))
        if let halo = style.layer(withIdentifier: "record-stamp-halo") as? MLNCircleStyleLayer {
            halo.circleRadius = NSExpression(forConstantValue: 34 * Double(scale))
            halo.circleStrokeWidth = NSExpression(forConstantValue: 2 * Double(scale))
        }
        let drawable = pin.flatMap { style.image(forName: $0.imageKey) == nil ? nil : $0 }
        guard drawable != lastPin else { return }
        if let drawable {
            let feature = MLNPointFeature()
            feature.coordinate = CLLocationCoordinate2D(latitude: drawable.latitude, longitude: drawable.longitude)
            source?.shape = feature
            (style.layer(withIdentifier: "record-stamp-icon") as? MLNSymbolStyleLayer)?.iconImageName = NSExpression(forConstantValue: drawable.imageKey)
        } else {
            source?.shape = MLNShapeCollectionFeature(shapes: [])
        }
        lastPin = drawable
    }
}
