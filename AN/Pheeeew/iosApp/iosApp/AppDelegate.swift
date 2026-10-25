import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions:
            [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self

        let notificationCenter = UNUserNotificationCenter.current()
        notificationCenter.delegate = self
        notificationCenter.requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { granted, error in
            print("알림 권한 허용:", granted)

            if let error {
                print("알림 권한 요청 실패:", error.localizedDescription)
            }
        }

        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
        print("APNs 등록 성공")
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
#if DEBUG
        if let fcmToken {
            print("FCM 테스트 토큰:", fcmToken)
        }
#endif
    }

}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler:
            @escaping (UNNotificationPresentationOptions) -> Void
    ) {
#if DEBUG
        print("포그라운드 알림 수신")
#endif
        // 수신한 알림의 표시 방식만 결정하며, 별도 로컬 알림을 생성하지 않는다.
        completionHandler([.banner, .list, .sound])
    }
}
