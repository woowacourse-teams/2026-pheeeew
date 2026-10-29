import FirebaseMessaging
import Shared

@MainActor
enum PushTokenLifecycle {
    static func foreground() {
        IosPushTokenSync.shared.foreground()
        refreshToken()
    }

    static func background() {
        IosPushTokenSync.shared.background()
    }

    static func permissionChanged() {
        IosPushTokenSync.shared.permissionChanged()
        refreshToken()
    }

    static func refreshToken() {
        // FCM token retrieval must wait for the APNs association.
        guard Messaging.messaging().apnsToken != nil else { return }
        Messaging.messaging().token { token, _ in
            guard let token, !token.isEmpty else { return }
            DispatchQueue.main.async {
                IosPushTokenSync.shared.tokenReceived(token: token)
            }
        }
    }
}
