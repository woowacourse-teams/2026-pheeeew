import SwiftUI
import Shared

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() { _ = IosMonitoring.shared.instance }
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onChange(of: scenePhase) { phase in
                    if phase == .active { IosMonitoring.shared.foreground() }
                    if phase == .background { IosMonitoring.shared.background() }
                }
                .onAppear {
                    if scenePhase == .active { IosMonitoring.shared.foreground() }
                }
        }
    }
}