#if DEBUG
import Darwin
import QuartzCore
import SwiftUI
import UIKit

private func writeDisplayLinkDiagnostic(_ message: String) {
    message.withCString { cString in
        fputs(cString, stdout)
    }
    fputs("\n", stdout)
    fflush(stdout)
}

@MainActor
private final class DisplayLinkCallbackTarget: NSObject {
    weak var owner: DisplayLinkCallbackProbeView?

    @objc func displayLinkDidFire(_ displayLink: CADisplayLink) {
        owner?.recordCallback(at: CACurrentMediaTime())
    }
}

@MainActor
private enum DisplayLinkCallbackGateDiagnostics {
    private static let maximumLines = 16
    private static var emittedLines = 0
    private static var didLogHostValue = false

    static func logHostEnabledChecked() {
        guard !didLogHostValue else { return }
        didLogHostValue = true
        append(
            "event=host_enabled_checked env_enabled=true window=unknown " +
            "scene=unknown app_state=\(applicationStateName) eligible=unknown link_running=unknown"
        )
    }

    static func logState(
        event: String,
        probeID: String,
        hasWindow: Bool,
        sceneState: String,
        eligible: Bool,
        linkRunning: Bool
    ) {
        append(
            "event=\(event) probe=\(probeID) env_enabled=true " +
            "window=\(hasWindow) scene=\(sceneState) app_state=\(applicationStateName) " +
            "eligible=\(eligible) link_running=\(linkRunning)"
        )
    }

    private static var applicationStateName: String {
        switch UIApplication.shared.applicationState {
        case .active: return "active"
        case .inactive: return "inactive"
        case .background: return "background"
        @unknown default: return "unknown"
        }
    }

    private static func append(_ fields: String) {
        guard emittedLines < maximumLines else { return }
        emittedLines += 1
        writeDisplayLinkDiagnostic("[DisplayLinkCallbackGate] \(fields)")
    }
}

@MainActor
final class DisplayLinkCallbackProbeView: UIView {
    private static let maximumSummaryLines = 60
    private static let summaryInterval: CFTimeInterval = 5
    private static let maximumExactSamplesPerWindow = 10_000
    private static var emittedSummaryLines = 0

    private static let histogramLabels = [
        "lt4", "4to8", "8to12", "12to16", "16to24", "24to32", "32to50", "ge50"
    ]
    private static let histogramUpperBoundsMs = [4.0, 8.0, 12.0, 16.0, 24.0, 32.0, 50.0]

    private let diagnosticsEnabled =
        ProcessInfo.processInfo.environment["PHEEEW_DISPLAY_LINK_DIAGNOSTICS"] == "1"
    private let probeID = String(UUID().uuidString.prefix(8))
    private let callbackTarget = DisplayLinkCallbackTarget()
    private var displayLink: CADisplayLink?
    private var sampleWindowStartedAt: CFTimeInterval?
    private var previousCallbackAt: CFTimeInterval?
    private var sampleCount = 0
    private var intervalTotalMs = 0.0
    private var minimumIntervalMs = Double.greatestFiniteMagnitude
    private var maximumIntervalMs = 0.0
    private var histogramCounts = Array(repeating: 0, count: 8)
    private var exactIntervalSamplesMs: [Double] = []
    private var exactSampleLimitReached = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        displayLink?.invalidate()
    }

    private func configure() {
        isHidden = true
        isUserInteractionEnabled = false
        accessibilityElementsHidden = true
        guard diagnosticsEnabled else { return }

        callbackTarget.owner = self

        let center = NotificationCenter.default
        [
            UIApplication.didBecomeActiveNotification,
            UIApplication.willResignActiveNotification,
            UIApplication.didEnterBackgroundNotification,
            UIScene.didActivateNotification,
            UIScene.willDeactivateNotification,
            UIScene.didEnterBackgroundNotification
        ].forEach { notification in
            center.addObserver(
                self,
                selector: #selector(lifecycleStateDidChange(_:)),
                name: notification,
                object: nil
            )
        }
        refreshMonitoringState(event: "probe_initialized")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        refreshMonitoringState(event: "window_changed")
    }

    @objc private func lifecycleStateDidChange(_ notification: Notification) {
        refreshMonitoringState(event: notification.name.rawValue)
    }

    private func refreshMonitoringState(event: String) {
        let sceneState = sceneActivationStateName
        let sceneIsActive = sceneState == "foregroundActive"
        let appIsActive = UIApplication.shared.applicationState == .active
        let shouldMonitor = diagnosticsEnabled && window != nil && sceneIsActive && appIsActive

        if shouldMonitor {
            startMonitoringIfNeeded()
        } else {
            stopMonitoring()
        }

        DisplayLinkCallbackGateDiagnostics.logState(
            event: event,
            probeID: probeID,
            hasWindow: window != nil,
            sceneState: sceneState,
            eligible: shouldMonitor,
            linkRunning: displayLink != nil
        )
    }

    private var sceneActivationStateName: String {
        guard let activationState = window?.windowScene?.activationState else { return "none" }
        switch activationState {
        case .foregroundActive: return "foregroundActive"
        case .foregroundInactive: return "foregroundInactive"
        case .background: return "background"
        case .unattached: return "unattached"
        @unknown default: return "unknown"
        }
    }

    private func startMonitoringIfNeeded() {
        guard displayLink == nil,
              Self.emittedSummaryLines < Self.maximumSummaryLines else { return }

        resetSampleWindow(startedAt: CACurrentMediaTime())
        let link = CADisplayLink(
            target: callbackTarget,
            selector: #selector(DisplayLinkCallbackTarget.displayLinkDidFire(_:))
        )
        displayLink = link
        link.add(to: .main, forMode: .common)
    }

    func recordCallback(at callbackTime: CFTimeInterval) {
        guard displayLink != nil else { return }
        guard Self.emittedSummaryLines < Self.maximumSummaryLines else {
            displayLink?.invalidate()
            displayLink = nil
            resetSampleWindow(startedAt: nil)
            return
        }

        guard let previousCallbackAt else {
            self.previousCallbackAt = callbackTime
            if sampleWindowStartedAt == nil {
                sampleWindowStartedAt = callbackTime
            }
            return
        }

        let intervalMs = (callbackTime - previousCallbackAt) * 1_000
        self.previousCallbackAt = callbackTime
        guard intervalMs.isFinite, intervalMs >= 0 else { return }

        sampleCount += 1
        intervalTotalMs += intervalMs
        minimumIntervalMs = min(minimumIntervalMs, intervalMs)
        maximumIntervalMs = max(maximumIntervalMs, intervalMs)
        histogramCounts[histogramIndex(for: intervalMs)] += 1
        if exactIntervalSamplesMs.count < Self.maximumExactSamplesPerWindow {
            exactIntervalSamplesMs.append(intervalMs)
        } else {
            exactSampleLimitReached = true
        }

        guard let sampleWindowStartedAt,
              callbackTime - sampleWindowStartedAt >= Self.summaryInterval else { return }
        emitSummary(
            at: callbackTime,
            reason: "interval",
            preserveLatestCallback: true
        )
    }

    private func histogramIndex(for intervalMs: Double) -> Int {
        Self.histogramUpperBoundsMs.firstIndex(where: { intervalMs < $0 })
            ?? Self.histogramUpperBoundsMs.count
    }

    private func emitSummary(
        at now: CFTimeInterval,
        reason: String,
        preserveLatestCallback: Bool = false
    ) {
        guard Self.emittedSummaryLines < Self.maximumSummaryLines else {
            displayLink?.invalidate()
            displayLink = nil
            resetSampleWindow(startedAt: nil)
            return
        }

        guard sampleCount > 0, let sampleWindowStartedAt else {
            resetSampleWindow(startedAt: now)
            return
        }

        let durationSeconds = max(0, now - sampleWindowStartedAt)
        let meanIntervalMs = intervalTotalMs / Double(sampleCount)
        let histogram = zip(Self.histogramLabels, histogramCounts)
            .map { pair in "\(pair.0):\(pair.1)" }
            .joined(separator: ",")
        let percentiles = exactPercentiles()
        let p95 = (percentiles?.p95).map(formatted) ?? "unavailable"
        let p99 = (percentiles?.p99).map(formatted) ?? "unavailable"
        let reachesLogLimit = Self.emittedSummaryLines + 1 >= Self.maximumSummaryLines
        let fields = [
            "[DisplayLinkCallbackInterval]",
            "probe=\(probeID)",
            "metric=callback_entry_interarrival_ms",
            "duration_s=\(formatted(durationSeconds))",
            "count=\(sampleCount)",
            "mean_ms=\(formatted(meanIntervalMs))",
            "min_ms=\(formatted(minimumIntervalMs))",
            "max_ms=\(formatted(maximumIntervalMs))",
            "histogram_ms={\(histogram)}",
            "p95_ms=\(p95)",
            "p99_ms=\(p99)",
            "percentile_method=nearest_rank",
            "exact_sample_limit=\(Self.maximumExactSamplesPerWindow)",
            "exact_sample_limit_reached=\(exactSampleLimitReached)",
            "summary_stdout_sync_flush=true",
            "summary=\(reason)",
            "log_limit_reached=\(reachesLogLimit)"
        ]
        writeDisplayLinkDiagnostic(fields.joined(separator: " "))
        Self.emittedSummaryLines += 1
        resetSampleWindow(
            startedAt: now,
            previousCallbackAt: preserveLatestCallback ? previousCallbackAt : nil
        )

        if reachesLogLimit {
            displayLink?.invalidate()
            displayLink = nil
            resetSampleWindow(startedAt: nil)
        }
    }

    private func formatted(_ value: Double) -> String {
        String(format: "%.3f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    private func exactPercentiles() -> (p95: Double, p99: Double)? {
        guard !exactSampleLimitReached, !exactIntervalSamplesMs.isEmpty else { return nil }
        let sortedSamples = exactIntervalSamplesMs.sorted()
        func nearestRank(_ percentile: Double) -> Double {
            let rank = max(1, Int(ceil(percentile * Double(sortedSamples.count))))
            return sortedSamples[rank - 1]
        }
        return (p95: nearestRank(0.95), p99: nearestRank(0.99))
    }

    private func resetSampleWindow(
        startedAt: CFTimeInterval?,
        previousCallbackAt: CFTimeInterval? = nil
    ) {
        sampleWindowStartedAt = startedAt
        self.previousCallbackAt = previousCallbackAt
        sampleCount = 0
        intervalTotalMs = 0
        minimumIntervalMs = Double.greatestFiniteMagnitude
        maximumIntervalMs = 0
        histogramCounts = Array(repeating: 0, count: Self.histogramLabels.count)
        exactIntervalSamplesMs.removeAll(keepingCapacity: true)
        exactSampleLimitReached = false
    }

    private func stopMonitoring() {
        guard displayLink != nil else { return }
        displayLink?.invalidate()
        displayLink = nil
        emitSummary(at: CACurrentMediaTime(), reason: "lifecycle_stop")
        resetSampleWindow(startedAt: nil)
    }

    func dispose() {
        stopMonitoring()
        NotificationCenter.default.removeObserver(self)
    }
}

@MainActor
struct DebugDisplayLinkCallbackDiagnosticsHost: UIViewRepresentable {
    static var isEnabled: Bool {
        let enabled = ProcessInfo.processInfo.environment["PHEEEW_DISPLAY_LINK_DIAGNOSTICS"] == "1"
        if enabled {
            DisplayLinkCallbackGateDiagnostics.logHostEnabledChecked()
        }
        return enabled
    }

    func makeUIView(context: Context) -> DisplayLinkCallbackProbeView {
        DisplayLinkCallbackProbeView(frame: .zero)
    }

    func updateUIView(_ uiView: DisplayLinkCallbackProbeView, context: Context) {
    }

    static func dismantleUIView(_ uiView: DisplayLinkCallbackProbeView, coordinator: ()) {
        uiView.dispose()
    }
}
#endif
