import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    private static let foundationMapFactory = FoundationMapFactory()

    func makeUIViewController(context: Self.Context) -> UIViewController {
        FoundationIosMapBridge.shared.registerFactory(factory: Self.foundationMapFactory)
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
