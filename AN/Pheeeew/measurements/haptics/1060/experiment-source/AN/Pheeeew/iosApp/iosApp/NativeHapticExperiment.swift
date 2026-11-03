#if DEBUG
import AVFAudio
import CoreHaptics
import SwiftUI
import UIKit

struct NativeHapticExperimentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { NativeHapticExperimentController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@MainActor
private final class NativeHapticExperimentController: UIViewController {
    private var hapticEngine: CHHapticEngine?
    private var patternPlayer: CHHapticPatternPlayer?
    private var generator: UIImpactFeedbackGenerator?
    private var timer: DispatchSourceTimer?
    private var pauseUntil: CFTimeInterval = 0
    private var pauseDone = false
    private var index = 0
    private var started: CFTimeInterval = 0
    private var previous: CFTimeInterval?
    private var wasIdleDisabled = false
    private let env = ProcessInfo.processInfo.environment

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let label = UILabel()
        label.text = "Native haptic experiment — no Compose / no synthetic presses"
        label.numberOfLines = 0
        label.frame = CGRect(x: 24, y: 100, width: 320, height: 120)
        view.addSubview(label)
        if env["PRESS_NATIVE_BOUND"] == "1", #available(iOS 17.5, *) {
            generator = UIImpactFeedbackGenerator(style: .medium, view: view)
        } else {
            generator = UIImpactFeedbackGenerator(style: .medium)
        }
        wasIdleDisabled = UIApplication.shared.isIdleTimerDisabled
        UIApplication.shared.isIdleTimerDisabled = true
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        try? AVAudioSession.sharedInstance().setActive(true)
        if env["PRESS_NATIVE_CORE_HAPTICS"] == "1" {
            do {
                let engine = try CHHapticEngine()
                engine.playsHapticsOnly = true
                engine.isAutoShutdownEnabled = false
                engine.stoppedHandler = { reason in print("[NativeHaptic] event=engineStopped reason=\(reason.rawValue)") }
                engine.resetHandler = { print("[NativeHaptic] event=engineReset trialRequiresRestart=true") }
                try engine.start()
                let event = CHHapticEvent(eventType: .hapticTransient, parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.6),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.5)
                ], relativeTime: 0)
                let pattern = try CHHapticPattern(events: [event], parameters: [])
                patternPlayer = try engine.makePlayer(with: pattern)
                hapticEngine = engine
                print("[NativeHaptic] event=coreSetup enginesCreated=1 playersCreated=1")
            } catch {
                print("[NativeHaptic] event=setupError error=\(error)")
                return
            }
        }
        let interval = max(20, Int(env["PRESS_NATIVE_INTERVAL_MS"] ?? "20") ?? 20)
        let count = max(300, Int(env["PRESS_NATIVE_COUNT"] ?? "600") ?? 600)
        let prepared = env["PRESS_NATIVE_PREPARE"] == "1"
        print("[NativeHaptic] event=armed prepare=\(prepared) bound=\(env["PRESS_NATIVE_BOUND"] ?? "0") count=\(count) intervalMs=\(interval)")
        let timer = DispatchSource.makeTimerSource(queue: .main)
        self.timer = timer
        timer.schedule(deadline: .now() + 5, repeating: .milliseconds(interval), leeway: .milliseconds(1))
        timer.setEventHandler { [weak self] in
            guard let self, let generator = self.generator else { return }
            let now = CACurrentMediaTime()
            if now < self.pauseUntil { return }
            if self.env["PRESS_NATIVE_PAUSE"] == "1", self.index == 300, !self.pauseDone {
                self.pauseDone = true
                self.pauseUntil = now + 8
                print("[NativeHaptic] event=pause afterIndex=300 durationMs=8000")
                return
            }
            if self.index == 0 { self.started = now }
            self.index += 1
            let delta = self.previous.map { (now - $0) * 1000 } ?? 0
            self.previous = now
            print("[NativeHaptic] event=impact index=\(self.index) prepare=\(prepared) elapsedMs=\(Int((now-self.started)*1000)) intervalMs=\(Int(delta))")
            if let player = self.patternPlayer {
                do { try player.start(atTime: CHHapticTimeImmediate) }
                catch { print("[NativeHaptic] event=playerError index=\(self.index) error=\(error)") }
            } else {
                generator.impactOccurred()
                if prepared { generator.prepare() }
            }
            if self.index == count {
                self.timer?.cancel()
                self.timer = nil
                self.hapticEngine?.stop(completionHandler: nil)
                self.patternPlayer = nil
                UIApplication.shared.isIdleTimerDisabled = self.wasIdleDisabled
                print("[NativeHaptic] event=complete impacts=\(self.index) physicalOutput=unknown")
            }
        }
        timer.resume()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        timer?.cancel()
        timer = nil
        hapticEngine?.stop(completionHandler: nil)
        patternPlayer = nil
        UIApplication.shared.isIdleTimerDisabled = wasIdleDisabled
    }
}
#endif
