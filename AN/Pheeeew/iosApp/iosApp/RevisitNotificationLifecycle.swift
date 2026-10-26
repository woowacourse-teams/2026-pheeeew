import UIKit
import Shared

@MainActor
final class RevisitNotificationLifecycle {
    static let shared = RevisitNotificationLifecycle()
    private var isForeground = false

    private init() {}

    func foreground() {
#if DEBUG
        if RevisitNotificationDiagnostics.inspectionOnly {
            RevisitNotificationDiagnostics.capture(reason: "inspection")
            return
        }
#endif
        // onAppear and scenePhase may both report the initial activation.
        guard !isForeground else { return }
        isForeground = true
        IosRevisitNotifications.shared.recordVisit(onComplete: { _ in
#if DEBUG
            RevisitNotificationDiagnostics.capture(reason: "foreground")
#endif
        })
    }

    func background() {
#if DEBUG
        if RevisitNotificationDiagnostics.inspectionOnly { return }
#endif
        isForeground = false
        RevisitNotificationBackgroundRun().start()
    }
}

@MainActor
private final class RevisitNotificationBackgroundRun {
    private var backgroundTask = UIBackgroundTaskIdentifier.invalid
    private var operationId: Int64?

    func start() {
        // Finish this short batch while entering the background; this is not a
        // periodically running background job or a delivery callback dependency.
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Revisit notifications") { [weak self] in
            Task { @MainActor in self?.expire() }
        }
        operationId = IosRevisitNotifications.shared.recordVisit { [self] _ in
#if DEBUG
            RevisitNotificationDiagnostics.capture(reason: "background")
#endif
            finish()
        }
    }

    private func expire() {
        if let operationId {
            IosRevisitNotifications.shared.cancel(operationId: operationId)
        }
        finish()
    }

    private func finish() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }
}
