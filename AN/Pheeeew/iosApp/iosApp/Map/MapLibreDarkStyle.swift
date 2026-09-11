import Foundation
import MapLibre
import Shared
import UIKit

enum MapLibreDarkStyle {
    static let styleURL = URL(string: "https://tiles.openfreemap.org/styles/dark")!
    static let mapBackground = color(hex: DesignSystemColors.shared.MAP_BACKGROUND_HEX)
    static let landColor = color(hex: DesignSystemColors.shared.MAP_LAND_HEX)
    static let buildingColor = color(hex: DesignSystemColors.shared.MAP_BUILDING_HEX)
    static let roadColor = color(hex: DesignSystemColors.shared.MAP_ROAD_HEX)
    static let majorRoadColor = color(hex: DesignSystemColors.shared.MAP_MAJOR_ROAD_HEX)
    static let parkColor = color(hex: DesignSystemColors.shared.MAP_PARK_HEX)
    static let waterColor = color(hex: DesignSystemColors.shared.MAP_WATER_HEX)
    static let labelColor = color(hex: DesignSystemColors.shared.MAP_LABEL_HEX)

    static let initialZoom = 12.6
    static let poiLabelMinZoom = 14.5
    static let focusZoom = 15.5
    static let minimumZoom = 0.0
    static let maximumZoom = 22.0

    static let sighSourceID = "sigh-source"
    static let sighLayerID = "sigh-symbol-layer"
    static let starBlueImageID = "sigh-star-blue"
    static let starExistingImageID = "sigh-star-existing"
    static let starOrangeImageID = "sigh-star-orange"
    static let starUnknownImageID = "sigh-star-unknown"
    static let starBlueColor = color(hex: DesignSystemColors.shared.STAR_BLUE_HEX)
    static let starExistingColor = color(hex: DesignSystemColors.shared.STAR_EXISTING_HEX)
    static let starOrangeColor = color(hex: DesignSystemColors.shared.STAR_ORANGE_HEX)
    static let starUnknownColor = color(hex: DesignSystemColors.shared.STAR_UNKNOWN_HEX)

    static let currentLocationSourceID = "current-location-source"
    static let currentLocationAccuracyLayerID = "current-location-accuracy-layer"
    static let currentLocationBorderLayerID = "current-location-border-layer"
    static let currentLocationLayerID = "current-location-layer"
    static let koreanPoiLayerID = "important-poi-label-ko"
    static let koreanBuildingLayerID = "building-label-ko"

    private static let localizedSourceLayers: Set<String> = [
        "aerodrome_label",
        "place",
        "transportation_name",
        "water_name",
    ]

    static let locationBlue = UIColor(red: 47 / 255, green: 128 / 255, blue: 237 / 255, alpha: 1)

    static func addKoreanPoiLayerIfPossible(to style: MLNStyle) {
        applyBackgroundColor(to: style)
        recolorBaseLayers(in: style)
        hideUnnecessarySymbolLayers(in: style)

        let localizedName = NSExpression(
            mglJSONObject: [
                "coalesce",
                ["get", "name:ko"],
                ["get", "name"],
                ["get", "name_en"],
                ["get", "name:en"],
            ]
        )
        for case let layer as MLNSymbolStyleLayer in style.layers
        where localizedSourceLayers.contains(layer.sourceLayerIdentifier ?? "") {
            layer.text = localizedName
            layer.textColor = NSExpression(forConstantValue: labelColor)
            layer.textHaloColor = NSExpression(forConstantValue: landColor)
            layer.textHaloWidth = NSExpression(forConstantValue: 1.0)
        }

        guard style.layer(withIdentifier: koreanPoiLayerID) == nil,
              let source = style.source(withIdentifier: "openmaptiles") else {
            return
        }

        let poiLayers = style.layers.compactMap { $0 as? MLNSymbolStyleLayer }
            .filter { $0.sourceLayerIdentifier == "poi" }

        for layer in poiLayers {
            layer.isVisible = false
        }

        let layer = MLNSymbolStyleLayer(identifier: koreanPoiLayerID, source: source)
        layer.sourceLayerIdentifier = "poi"
        layer.minimumZoomLevel = Float(poiLabelMinZoom)
        let namedPoi = NSCompoundPredicate(orPredicateWithSubpredicates: [
            NSPredicate(format: "%K != NIL", "name:ko"),
            NSPredicate(format: "%K != NIL", "name"),
            NSPredicate(format: "%K != NIL", "name_en"),
            NSPredicate(format: "%K != NIL", "name:en"),
        ])
        layer.predicate = NSCompoundPredicate(andPredicateWithSubpredicates: [
            namedPoi,
            NSPredicate(format: "CAST(%K, 'NSNumber') < 7", "rank"),
        ])
        layer.text = localizedName
        layer.textColor = NSExpression(forConstantValue: labelColor)
        layer.textHaloColor = NSExpression(forConstantValue: landColor)
        layer.textHaloWidth = NSExpression(forConstantValue: 1.0)
        layer.textFontNames = NSExpression(forConstantValue: ["Noto Sans Regular"])
        layer.textFontSize = NSExpression(forConstantValue: 12)
        layer.textAllowsOverlap = NSExpression(forConstantValue: false)
        layer.iconOpacity = NSExpression(forConstantValue: 0)
        style.addLayer(layer)
        addBuildingLabels(to: style, source: source, localizedName: localizedName)
    }

    private static func recolorBaseLayers(in style: MLNStyle) {
        for styleLayer in style.layers {
            if let layer = styleLayer as? MLNFillStyleLayer,
               let color = fillColor(
                   sourceLayer: layer.sourceLayerIdentifier ?? "",
                   layerID: layer.identifier
               ) {
                layer.fillColor = NSExpression(forConstantValue: color)
                continue
            }

            if let layer = styleLayer as? MLNFillExtrusionStyleLayer,
               isBuildingLayer(
                   sourceLayer: layer.sourceLayerIdentifier ?? "",
                   layerID: layer.identifier
               ) {
                layer.fillExtrusionColor = NSExpression(forConstantValue: buildingColor)
                continue
            }

            if let layer = styleLayer as? MLNLineStyleLayer,
               let color = lineColor(
                   sourceLayer: layer.sourceLayerIdentifier ?? "",
                   layerID: layer.identifier
               ) {
                layer.lineColor = NSExpression(forConstantValue: color)
            }
        }
    }

    private static func hideUnnecessarySymbolLayers(in style: MLNStyle) {
        for case let layer as MLNSymbolStyleLayer in style.layers {
            let sourceLayer = layer.sourceLayerIdentifier ?? ""
            if !sourceLayer.isEmpty && !localizedSourceLayers.contains(sourceLayer) {
                layer.isVisible = false
            }
        }
    }

    private static func addBuildingLabels(
        to style: MLNStyle,
        source: MLNSource,
        localizedName: NSExpression
    ) {
        guard style.layer(withIdentifier: koreanBuildingLayerID) == nil else { return }

        let namedBuilding = NSCompoundPredicate(orPredicateWithSubpredicates: [
            NSPredicate(format: "%K != NIL", "name:ko"),
            NSPredicate(format: "%K != NIL", "name"),
            NSPredicate(format: "%K != NIL", "name_en"),
            NSPredicate(format: "%K != NIL", "name:en"),
        ])
        let layer = MLNSymbolStyleLayer(identifier: koreanBuildingLayerID, source: source)
        layer.sourceLayerIdentifier = "building"
        layer.minimumZoomLevel = Float(poiLabelMinZoom)
        layer.predicate = namedBuilding
        layer.text = localizedName
        layer.textColor = NSExpression(forConstantValue: labelColor)
        layer.textHaloColor = NSExpression(forConstantValue: landColor)
        layer.textHaloWidth = NSExpression(forConstantValue: 1.0)
        layer.textFontNames = NSExpression(forConstantValue: ["Noto Sans Regular"])
        layer.textFontSize = NSExpression(forConstantValue: 11)
        layer.textAllowsOverlap = NSExpression(forConstantValue: false)
        layer.iconOpacity = NSExpression(forConstantValue: 0)
        style.addLayer(layer)
    }

    private static func fillColor(sourceLayer: String, layerID: String) -> UIColor? {
        let source = sourceLayer.lowercased()
        let identifier = layerID.lowercased()
        if isBuildingLayer(sourceLayer: source, layerID: identifier) {
            return buildingColor
        }
        if isWaterLayer(sourceLayer: source, layerID: identifier) {
            return waterColor
        }
        if isParkLayer(sourceLayer: source, layerID: identifier) {
            return parkColor
        }
        if landSourceLayers.contains(source) || identifier.contains("land") {
            return landColor
        }
        return nil
    }

    private static func lineColor(sourceLayer: String, layerID: String) -> UIColor? {
        let source = sourceLayer.lowercased()
        let identifier = layerID.lowercased()
        if isWaterLayer(sourceLayer: source, layerID: identifier) {
            return waterColor
        }
        if source == "transportation" || roadIDKeywords.contains(where: identifier.contains) {
            return majorRoadIDKeywords.contains(where: identifier.contains) ? majorRoadColor : roadColor
        }
        return nil
    }

    private static func isBuildingLayer(sourceLayer: String, layerID: String) -> Bool {
        sourceLayer.lowercased() == "building" || layerID.lowercased().contains("building")
    }

    private static func isWaterLayer(sourceLayer: String, layerID: String) -> Bool {
        waterSourceLayers.contains(sourceLayer.lowercased()) || layerID.lowercased().contains("water")
    }

    private static func isParkLayer(sourceLayer: String, layerID: String) -> Bool {
        let source = sourceLayer.lowercased()
        let identifier = layerID.lowercased()
        return parkSourceLayers.contains(source) && parkIDKeywords.contains(where: identifier.contains)
    }

    private static func applyBackgroundColor(to style: MLNStyle) {
        if let backgroundLayer = style.layer(withIdentifier: "background") as? MLNBackgroundStyleLayer {
            backgroundLayer.backgroundColor = NSExpression(forConstantValue: mapBackground)
            return
        }

        let backgroundLayer = MLNBackgroundStyleLayer(identifier: "pheeeew-background")
        backgroundLayer.backgroundColor = NSExpression(forConstantValue: mapBackground)
        style.insertLayer(backgroundLayer, at: 0)
    }

    private static func color(hex: String) -> UIColor {
        let value = Int(hex.dropFirst(), radix: 16) ?? 0
        return UIColor(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1,
        )
    }

    private static let landSourceLayers: Set<String> = ["land", "landcover", "landuse"]
    private static let parkSourceLayers: Set<String> = ["landcover", "landuse", "park"]
    private static let waterSourceLayers: Set<String> = ["water", "water_name", "waterway"]
    private static let roadIDKeywords = ["road", "street", "highway", "transportation"]
    private static let majorRoadIDKeywords = ["motorway", "trunk", "primary", "secondary", "major"]
    private static let parkIDKeywords = [
        "park", "wood", "forest", "grass", "garden", "recreation", "cemetery", "nature",
    ]

    static func makeSighStarImage(color: UIColor) -> UIImage {
        let size = CGSize(width: 64, height: 64)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let graphics = context.cgContext
            let center = CGPoint(x: size.width / 2, y: size.height / 2)

            let glowColors = [
                color.withAlphaComponent(0.70).cgColor,
                color.withAlphaComponent(0.24).cgColor,
                UIColor.clear.cgColor,
            ] as CFArray
            if let glow = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: glowColors,
                locations: [0, 0.52, 1]
            ) {
                graphics.drawRadialGradient(
                    glow,
                    startCenter: center,
                    startRadius: 0,
                    endCenter: center,
                    endRadius: 31,
                    options: []
                )
            }

            let star = UIBezierPath()
            for index in 0..<16 {
                let angle = -.pi / 2 + Double(index) * .pi / 8
                let radius: Double
                if index.isMultiple(of: 2) {
                    radius = index.isMultiple(of: 4) ? 22 : 16
                } else {
                    radius = 7
                }
                let point = CGPoint(
                    x: center.x + CGFloat(cos(angle) * radius),
                    y: center.y + CGFloat(sin(angle) * radius)
                )
                index == 0 ? star.move(to: point) : star.addLine(to: point)
            }
            star.close()

            graphics.saveGState()
            graphics.addPath(star.cgPath)
            graphics.clip()

            let coreColors = [
                MapLibreDarkStyle.color(hex: DesignSystemColors.shared.STAR_CORE_HEX).cgColor,
                MapLibreDarkStyle.color(hex: DesignSystemColors.shared.STAR_EXISTING_HEX)
                    .withAlphaComponent(0.72).cgColor,
            ] as CFArray
            if let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: coreColors,
                locations: [0, 1]
            ) {
                graphics.drawRadialGradient(
                    gradient,
                    startCenter: center,
                    startRadius: 0,
                    endCenter: center,
                    endRadius: 23,
                    options: []
                )
            }
            graphics.restoreGState()
        }
    }
}
