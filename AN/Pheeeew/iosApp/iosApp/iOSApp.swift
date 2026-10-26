import SwiftUI
import Shared

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase
#if DEBUG
    @State private var monitoringSmokeTestScheduled = false
#endif

    init() {
#if DEBUG
        DeveloperToolsInitializationKt.initializeDeveloperTools()
        _ = IosMonitoring.shared.instance
#else
        _ = IosMonitoring.shared.instance
#endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onChange(of: scenePhase) { phase in
                    if phase == .active {
                        IosMonitoring.shared.foreground()
                        RevisitNotificationLifecycle.shared.foreground()
                        PushTokenLifecycle.foreground()
                    }
                    if phase == .background {
                        IosMonitoring.shared.background()
                        RevisitNotificationLifecycle.shared.background()
                        PushTokenLifecycle.background()
                    }
                }
                .onAppear {
                    if scenePhase == .active {
                        IosMonitoring.shared.foreground()
                        RevisitNotificationLifecycle.shared.foreground()
                        PushTokenLifecycle.foreground()
                    }
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
                            IosMonitoring.shared.smokeTestActivity()
                            monitoring.reportError(
                                error: KotlinIllegalStateException(message: "Development monitoring smoke test"),
                                context: monitoring.context(screen: "unknown", parent: nil)
                            )
                        }
                    }
#endif
                }
        }
    }
}
