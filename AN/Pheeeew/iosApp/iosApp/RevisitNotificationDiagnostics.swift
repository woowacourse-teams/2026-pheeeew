#if DEBUG
import Foundation
import UserNotifications
import Shared

@MainActor
enum RevisitNotificationDiagnostics {
    private static let testModeKey = "debug.revisitNotification.minuteIntervals"

    static func configureOnLaunch() {
        // Environment variables only exist for the developer-launched process.
        // Keep this Debug-only choice across normal icon/notification launches.
        if let value = ProcessInfo.processInfo.environment["REVISIT_NOTIFICATION_SMOKE_TEST"] {
            UserDefaults.standard.set(value == "1", forKey: testModeKey)
        }
        if !inspectionOnly && UserDefaults.standard.bool(forKey: testModeKey) {
            IosRevisitNotifications.shared.useShortIntervalsForTesting()
        }
    }

    static var inspectionOnly: Bool {
        ProcessInfo.processInfo.environment["REVISIT_NOTIFICATION_INSPECT_ONLY"] == "1"
    }

    static func capture(reason: String) {
        Task { @MainActor in
            let center = UNUserNotificationCenter.current()
            let settings = await center.notificationSettings()
            let pending = await center.pendingNotificationRequests()
            let delivered = await center.deliveredNotifications()
            let deliveredRevisit = delivered.filter {
                IosRevisitNotifications.shared.isRevisit(identifier: $0.request.identifier)
            }
            let requests = pending.filter { IosRevisitNotifications.shared.isRevisit(identifier: $0.identifier) }
            let intervals = requests.compactMap { ($0.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval }.sorted()
            let dates = requests.compactMap { ($0.trigger as? UNTimeIntervalNotificationTrigger)?.nextTriggerDate() }.sorted()
            let spacingIsMinute = intervals.count == 30 && zip(intervals, intervals.dropFirst()).allSatisfy {
                abs(($0.1 - $0.0) - 60) < 2
            }
            let repeatingCount = requests.filter { $0.trigger?.repeats == true }.count
            if let first = intervals.first, let last = intervals.last {
                print("[Revisit test] firstIntervalSeconds=\(first) lastIntervalSeconds=\(last)")
            }
            let format = ISO8601DateFormatter()
            print("[Revisit test] alerts=\(settings.alertSetting.rawValue) lockScreen=\(settings.lockScreenSetting.rawValue) notificationCenter=\(settings.notificationCenterSetting.rawValue) sound=\(settings.soundSetting.rawValue) scheduledDelivery=\(settings.scheduledDeliverySetting.rawValue)")
            print("[Revisit test] delivered=\(deliveredRevisit.count)")
            for notification in deliveredRevisit.sorted(by: { $0.date < $1.date }).suffix(3) {
                print("[Revisit test] deliveredAt=\(format.string(from: notification.date)) id=\(notification.request.identifier)")
            }
            print("[Revisit test] reason=\(reason) permission=\(settings.authorizationStatus.rawValue) pending=\(requests.count) repeating=\(repeatingCount) minuteSpacing=\(spacingIsMinute)")
            if let first = dates.first, let last = dates.last {
                print("[Revisit test] first=\(format.string(from: first)) last=\(format.string(from: last))")
            }
            if let firstId = requests.sorted(by: { $0.identifier < $1.identifier }).first?.identifier {
                print("[Revisit test] firstRequestId=\(firstId)")
            }
        }
    }
}
#endif
