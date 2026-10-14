import UIKit
import SwiftUI
import MachO
import Shared

struct ComposeView: UIViewControllerRepresentable {
    private static let foundationMapFactory = FoundationMapFactory()

    func makeUIViewController(context: Self.Context) -> UIViewController {
        FoundationIosMapBridge.shared.registerFactory(factory: Self.foundationMapFactory)
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-group-detail-profile") {
            GroupDetailProfileView()
        } else {
            ComposeView()
                .ignoresSafeArea()
        }
#else
        ComposeView()
            .ignoresSafeArea()
#endif
    }
}

#if DEBUG
private struct GroupDetailProfileComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        GroupDetailProfileViewControllerKt.GroupDetailProfileViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private struct GroupDetailProfileView: View {
    @StateObject private var metrics = GroupDetailProfileMetrics()

    var body: some View {
        ZStack(alignment: .topLeading) {
            GroupDetailProfileComposeView()
                .ignoresSafeArea()
            Text(metrics.report)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .padding(8)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                .padding(.top, 54)
                .padding(.leading, 8)
                .allowsHitTesting(false)
        }
        .onAppear { metrics.observeProfileRun() }
        .onDisappear { metrics.stop() }
    }
}

@MainActor
private final class GroupDetailProfileMetrics: NSObject, ObservableObject {
    @Published private(set) var report = "Detail fixture profile: waiting"

    private var startObserver: NSObjectProtocol?
    private var finishObserver: NSObjectProtocol?
    private var displayLink: CADisplayLink?
    private var previousTimestamp: CFTimeInterval?
    private var previousExpectedFrameMillis: Double?
    private var frameSamples: [FrameTimingSample] = []
    private var residentSamples: [UInt64] = []
    private var residentAtStart: UInt64?
    private var sampling = false

    func observeProfileRun() {
        let center = NotificationCenter.default
        startObserver = center.addObserver(
            forName: Notification.Name("com.pheeeew.group-detail-profile.started"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.start() }
        }
        finishObserver = center.addObserver(
            forName: Notification.Name("com.pheeeew.group-detail-profile.finished"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.finish() }
        }
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
        sampling = false
        if let startObserver { NotificationCenter.default.removeObserver(startObserver) }
        if let finishObserver { NotificationCenter.default.removeObserver(finishObserver) }
        startObserver = nil
        finishObserver = nil
    }

    private func start() {
        frameSamples.removeAll(keepingCapacity: true)
        residentSamples.removeAll(keepingCapacity: true)
        previousTimestamp = nil
        previousExpectedFrameMillis = nil
        residentAtStart = residentBytes()
        sampling = true
        displayLink?.invalidate()
        let link = CADisplayLink(target: self, selector: #selector(sampleFrame(_:)))
        link.add(to: .main, forMode: .common)
        displayLink = link
        report = "Detail fixture profile: collecting 200 local taps"
    }

    private func finish() {
        sampling = false
        displayLink?.invalidate()
        displayLink = nil
        let endResident = residentBytes()
        if let endResident { residentSamples.append(endResident) }
        let sortedFrames = frameSamples.map(\.intervalMillis).sorted()
        let peakResident = residentSamples.max()
        let deltaMegabytes: Double? =
            if let residentAtStart, let peakResident {
                Double(Int64(peakResident) - Int64(residentAtStart)) / 1_048_576.0
            } else {
                nil
            }
        let p50 = percentile(sortedFrames, 0.50)
        let p95 = percentile(sortedFrames, 0.95)
        let hitches = frameSamples.filter { $0.intervalMillis > $0.expectedIntervalMillis * 1.5 }.count
        report = String(
            format: "iOS frames n=%d interval p50=%.2f p95=%.2f ms hitches=%d RSS peak Δ=%@ MB",
            sortedFrames.count,
            p50,
            p95,
            hitches,
            deltaMegabytes.map { String(format: "%.1f", $0) } ?? "n/a"
        )
        print("IOS_GROUP_DETAIL_NATIVE_PROFILE \(report)")
    }

    @objc private func sampleFrame(_ link: CADisplayLink) {
        guard sampling else { return }
        if let previousTimestamp {
            if let previousExpectedFrameMillis {
                frameSamples.append(
                    FrameTimingSample(
                        intervalMillis: (link.timestamp - previousTimestamp) * 1000.0,
                        expectedIntervalMillis: previousExpectedFrameMillis
                    )
                )
            }
        }
        previousTimestamp = link.timestamp
        previousExpectedFrameMillis = (link.targetTimestamp - link.timestamp) * 1000.0
        if frameSamples.count.isMultiple(of: 30), let resident = residentBytes() {
            residentSamples.append(resident)
        }
    }

    private struct FrameTimingSample {
        let intervalMillis: Double
        let expectedIntervalMillis: Double
    }

    private func residentBytes() -> UInt64? {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<integer_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { infoPointer in
            infoPointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { intPointer in
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), intPointer, &count)
            }
        }
        guard result == KERN_SUCCESS else { return nil }
        return info.resident_size
    }

    private func percentile(_ values: [Double], _ fraction: Double) -> Double {
        guard !values.isEmpty else { return 0 }
        return values[min(Int(Double(values.count - 1) * fraction), values.count - 1)]
    }
}
#endif
