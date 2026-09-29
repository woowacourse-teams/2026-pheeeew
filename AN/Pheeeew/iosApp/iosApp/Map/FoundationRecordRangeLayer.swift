import MapLibre
import Shared
import UIKit

final class FoundationRecordRangeLayer {
    private var source: MLNShapeSource?
    private var lastOrigin: GeoCoordinate?

    func install(on style: MLNStyle) {
        lastOrigin = nil
        let source = MLNShapeSource(identifier: "record-range-source", features: [], options: nil)
        style.addSource(source)
        self.source = source
        let blue = UIColor(red: 57 / 255, green: 140 / 255, blue: 1, alpha: 1)

        let dim = MLNFillStyleLayer(identifier: "record-range-dim", source: source)
        dim.predicate = NSPredicate(format: "kind == %@", "dim")
        dim.fillColor = NSExpression(forConstantValue: UIColor.black)
        dim.fillOpacity = NSExpression(forConstantValue: 0.45)
        style.addLayer(dim)

        let fill = MLNFillStyleLayer(identifier: "record-range-fill", source: source)
        fill.predicate = NSPredicate(format: "kind == %@", "range")
        fill.fillColor = NSExpression(forConstantValue: blue)
        fill.fillOpacity = NSExpression(forConstantValue: 0.14)
        style.addLayer(fill)

        let border = MLNLineStyleLayer(identifier: "record-range-border", source: source)
        border.predicate = NSPredicate(format: "kind == %@", "range")
        border.lineColor = NSExpression(forConstantValue: blue)
        border.lineWidth = NSExpression(forConstantValue: 1.5)
        border.lineDashPattern = NSExpression(forConstantValue: [7.0 / 1.5, 4.0])
        style.addLayer(border)
    }

    func update(state: FoundationIosMapRenderUiModel) {
        let origin = state.isRecordLocationPicking ? state.recordOrigin.map {
            GeoCoordinate(latitude: $0.latitude, longitude: $0.longitude)
        } : nil
        guard let source, origin != lastOrigin else { return }
        let json = RecordRangeGeometryKt.recordRangeGeoJson(origin: origin)
        guard let shape = try? MLNShape(data: Data(json.utf8), encoding: String.Encoding.utf8.rawValue) else { return }
        source.shape = shape
        lastOrigin = origin
    }
}
