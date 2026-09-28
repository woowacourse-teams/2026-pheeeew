import MapLibre
import UIKit

// Recolors map surfaces while leaving every label and symbol layer untouched.
enum FoundationMapStyle {
    static let styleURL = URL(string: "https://tiles.openfreemap.org/styles/liberty")!
    static let initialZoom: Double = 11.0
    static let minimumZoom: Double = 2.0
    static let maximumZoom: Double = 20.0

    private static let background = color(hex: "F7F7F4")
    private static let landUse = color(hex: "F2F1EE")
    private static let building = color(hex: "ECEBE9")
    private static let park = color(hex: "CFE3CC")
    private static let parkOutline = color(hex: "C1D8C0")
    private static let water = color(hex: "B8D8E8")
    private static let road = UIColor.white
    private static let majorRoad = color(hex: "FAFBFA")
    private static let roadOutline = color(hex: "D9DEE2")

    static func applyMutedPalette(to style: MLNStyle) {
        for styleLayer in style.layers {
            let layerID = styleLayer.identifier.lowercased()

            if let layer = styleLayer as? MLNBackgroundStyleLayer, layerID == "background" {
                layer.backgroundColor = NSExpression(forConstantValue: background)
                continue
            }

            if let layer = styleLayer as? MLNFillStyleLayer {
                let sourceLayer = (layer.sourceLayerIdentifier ?? "").lowercased()
                if let color = fillColor(sourceLayer: sourceLayer, layerID: layerID) {
                    layer.fillColor = NSExpression(forConstantValue: color)
                }
                continue
            }

            if let layer = styleLayer as? MLNLineStyleLayer {
                let sourceLayer = (layer.sourceLayerIdentifier ?? "").lowercased()
                if let color = lineColor(sourceLayer: sourceLayer, layerID: layerID) {
                    layer.lineColor = NSExpression(forConstantValue: color)
                }
            }
        }
    }

    private static func fillColor(sourceLayer: String, layerID: String) -> UIColor? {
        if sourceLayer == "water" || layerID == "water" { return water }
        if sourceLayer == "building" || layerID.contains("building") { return building }
        if sourceLayer == "park" || layerID.contains("park") { return park }
        if sourceLayer == "landcover" && (layerID.contains("wood") || layerID.contains("grass")) { return park }
        if sourceLayer == "landuse" && layerID.contains("residential") { return landUse }
        return nil
    }

    private static func lineColor(sourceLayer: String, layerID: String) -> UIColor? {
        if sourceLayer == "waterway" || layerID.contains("waterway") { return water }
        if sourceLayer == "park" || layerID.contains("park_outline") { return parkOutline }
        if sourceLayer == "transportation" && layerID.contains("casing") { return roadOutline }
        if sourceLayer == "transportation" && majorRoadKeywords.contains(where: layerID.contains) { return majorRoad }
        if sourceLayer == "transportation" { return road }
        return nil
    }

    private static let majorRoadKeywords = ["motorway", "trunk", "primary", "secondary", "tertiary"]

    private static func color(hex: String) -> UIColor {
        let value = Int(hex, radix: 16) ?? 0
        return UIColor(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1
        )
    }
}
