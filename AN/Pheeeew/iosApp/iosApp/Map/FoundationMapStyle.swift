import MapLibre

enum FoundationMapStyle {
    static let styleURL = URL(string: "https://tiles.openfreemap.org/styles/liberty")!
    static let initialZoom: Double = 11.0
    static let minimumZoom: Double = 2.0
    static let maximumZoom: Double = 20.0
}
