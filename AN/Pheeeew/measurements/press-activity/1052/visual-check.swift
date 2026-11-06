import AppKit
import Foundation
let root = CommandLine.arguments[1]
var rows: [[String: Any]] = []
for i in 0..<4 {
    let bitmap = NSBitmapImageRep(data: try Data(contentsOf: URL(fileURLWithPath: "\(root)/candidate-visual-\(i).png")))!
    let x = bitmap.pixelsWide/2, y = bitmap.pixelsHigh/2
    var sum = 0, nonWhite = 0
    for py in (y-35)...(y+35) {
        for px in (x-35)...(x+35) {
            let c = bitmap.colorAt(x: px, y: py)!.usingColorSpace(.deviceRGB)!
            let rgb = [c.redComponent, c.greenComponent, c.blueComponent].map { Int(($0*255).rounded()) }
            sum += rgb.reduce(0,+)
            if rgb != [255,255,255] { nonWhite += 1 }
        }
    }
    rows.append(["sample":i,"centralRegionRGBSum":sum,"centralRegionNonWhite":nonWhite])
}
let data = try JSONSerialization.data(withJSONObject: rows, options: [.prettyPrinted,.sortedKeys])
print(String(data:data,encoding:.utf8)!)
