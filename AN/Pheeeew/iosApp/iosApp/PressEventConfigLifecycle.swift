import FirebaseRemoteConfig
import Shared

// Firebase callbacks are explicitly delivered to the main queue for the common state owner.
private final class FirebasePressEventConfigSource: PressEventConfigSource {
    private let remoteConfig = RemoteConfig.remoteConfig()

    func initialize(
        parameterKey: String,
        minimumFetchIntervalSeconds: Int64,
        completion: @escaping (KotlinBoolean) -> Void
    ) {
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = TimeInterval(minimumFetchIntervalSeconds)
        settings.fetchTimeout = 10
        remoteConfig.configSettings = settings
        remoteConfig.setDefaults([parameterKey: NSNumber(value: false)])
        remoteConfig.ensureInitialized { error in
            DispatchQueue.main.async { completion(KotlinBoolean(bool: error == nil)) }
        }
    }

    func activatedBoolean(parameterKey: String) -> Bool {
        remoteConfig[parameterKey].boolValue
    }

    func fetchAndActivate(completion: @escaping (KotlinBoolean) -> Void) {
        remoteConfig.fetchAndActivate { status, error in
            let succeeded = error == nil && status != .error
            DispatchQueue.main.async { completion(KotlinBoolean(bool: succeeded)) }
        }
    }
}

@MainActor
enum PressEventConfigLifecycle {
    private static let source = FirebasePressEventConfigSource()
    private static var active = false

    static func foreground() {
        guard !active else { return }
        active = true
        IosPressEventConfig.shared.foreground(source: source)
    }

    static func background() {
        active = false
    }
}
