import SwiftUI
import Shared

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase
#if DEBUG
    @State private var monitoringSmokeTestScheduled = false
#endif

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
#if DEBUG
                    if !monitoringSmokeTestScheduled,
                       ["1", "crash"].contains(ProcessInfo.processInfo.environment["MONITORING_SMOKE_TEST"] ?? ""),
                       Bundle.main.object(forInfoDictionaryKey: "MONITORING_ENVIRONMENT") as? String == "dev" {
                        monitoringSmokeTestScheduled = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            if ProcessInfo.processInfo.environment["MONITORING_SMOKE_TEST"] == "crash" {
                                fatalError("Development monitoring native crash test")
                            }
                            let monitoring = IosMonitoring.shared.instance
                            monitoring.report(
                                error: KotlinIllegalStateException(message: "Development monitoring smoke test"),
                                origin: monitoring.snapshot()
                            )
                        }
                    }
#endif
                }
        }
    }
}
