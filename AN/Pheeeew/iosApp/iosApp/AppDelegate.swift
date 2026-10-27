import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import Shared

final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions:
            [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
#if DEBUG
        RevisitNotificationDiagnostics.configureOnLaunch()
#endif

        let notificationCenter = UNUserNotificationCenter.current()
        notificationCenter.delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
        print("APNs 등록 성공")
        DispatchQueue.main.async { PushTokenLifecycle.refreshToken() }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("APNs 등록 실패:", error.localizedDescription)
    }

    func messaging(
        _ messaging: Messaging,
        didReceiveRegistrationToken fcmToken: String?
    ) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        DispatchQueue.main.async {
            IosPushTokenSync.shared.tokenReceived(token: fcmToken)
        }
    }

}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // Ignore dismissals and custom actions; only a tap on the notification opens its target.
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
            completionHandler()
            return
        }

        let request = response.notification.request
        let fields = request.content.userInfo.reduce(into: [String: String]()) { result, entry in
            if let key = entry.key as? String, let value = entry.value as? String {
                result[key] = value
            }
        }
        let requestId = request.identifier
        DispatchQueue.main.async {
            IosNotificationClicks.shared.receive(data: fields, requestId: requestId)
#if DEBUG
            print("알림 클릭 데이터 공통 코드 전달 완료")
#endif
            completionHandler()
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler:
            @escaping (UNNotificationPresentationOptions) -> Void
    ) {
#if DEBUG
        print("포그라운드 알림 수신")
#endif
        let request = notification.request
        let fields = request.content.userInfo.reduce(into: [String: String]()) { result, entry in
            if let key = entry.key as? String, let value = entry.value as? String {
                result[key] = value
            }
        }
        if fields["type"] == "REVISIT_REMINDER" {
            completionHandler([])
            return
        }
        DispatchQueue.main.async {
            guard UIApplication.shared.applicationState == .active else {
                return
            }
            IosNotificationMessages.shared.receive(
                data: fields,
                messageId: request.identifier
            )
        }
        completionHandler([.banner, .list, .sound])
    }
}
