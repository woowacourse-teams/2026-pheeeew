import AVFoundation
import Shared

final class BreathAudioDetector {
    private let engine = AVAudioEngine()
    private var wantsRecording = false
    private var tapInstalled = false
    private var sessionGeneration = 0
    private var previousInput = 0.0
    private var previousHighPassed = 0.0
    private var lowPassed500 = 0.0
    private var lowPassed2000 = 0.0
    private var previousBandLimited = 0.0

    func requestPermission(completion: @escaping (Bool) -> Void) {
        AVAudioSession.sharedInstance().requestRecordPermission(completion)
    }

    func start() {
        guard !wantsRecording, !tapInstalled else { return }
        wantsRecording = true
        sessionGeneration += 1
        let generation = sessionGeneration
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard let self, self.wantsRecording, self.sessionGeneration == generation else { return }
                guard granted else {
                    self.wantsRecording = false
                    IosBreathBridge.shared.updateError(errorName: "PermissionDenied")
                    return
                }
                self.beginRecording()
            }
        }
    }

    func stop() {
        sessionGeneration += 1
        wantsRecording = false
        if tapInstalled { engine.inputNode.removeTap(onBus: 0); tapInstalled = false }
        engine.stop(); engine.reset(); resetAnalysisState()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func beginRecording() {
        guard !tapInstalled else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement)
            try session.setPreferredSampleRate(16_000)
            try session.setPreferredIOBufferDuration(0.04)
            try session.setActive(true)
            let input = engine.inputNode
            let format = input.inputFormat(forBus: 0)
            guard format.channelCount > 0, format.sampleRate > 0 else { throw NSError(domain: "PheeeewBreath", code: 1) }
            resetAnalysisState()
            input.installTap(onBus: 0, bufferSize: 2_048, format: format) { [weak self] buffer, _ in self?.process(buffer) }
            tapInstalled = true
            engine.prepare()
            try engine.start()
        } catch {
            stop()
            IosBreathBridge.shared.updateError(errorName: "StartFailed")
        }
    }

    private func process(_ buffer: AVAudioPCMBuffer) {
        guard wantsRecording, let channel = buffer.floatChannelData?.pointee, buffer.frameLength > 0 else { return }
        let generation = sessionGeneration
        let rate = buffer.format.sampleRate
        let dt = 1.0 / rate
        let hpRC = 1.0 / (2.0 * Double.pi * 80.0)
        let lp500RC = 1.0 / (2.0 * Double.pi * 500.0)
        let lp2000RC = 1.0 / (2.0 * Double.pi * 2_000.0)
        let hp = hpRC / (hpRC + dt)
        let lp500 = dt / (lp500RC + dt)
        let lp2000 = dt / (lp2000RC + dt)
        var energy = 0.0; var lowEnergy = 0.0; var crossings = 0
        let count = Int(buffer.frameLength)
        for index in 0..<count {
            let input = Double(channel[index])
            let high = hp * (previousHighPassed + input - previousInput)
            previousInput = input; previousHighPassed = high
            lowPassed500 += lp500 * (high - lowPassed500); lowPassed2000 += lp2000 * (high - lowPassed2000)
            energy += lowPassed2000 * lowPassed2000; lowEnergy += lowPassed500 * lowPassed500
            if (previousBandLimited < 0 && lowPassed2000 >= 0) || (previousBandLimited >= 0 && lowPassed2000 < 0) { crossings += 1 }
            previousBandLimited = lowPassed2000
        }
        let relevant = max(energy / Double(count), 1e-12)
        let amplitude = min(max((20.0 * log10(sqrt(relevant)) + 48.0) / 40.0, 0), 1)
        let lowPresence = min(max((lowEnergy / Double(count) / relevant - 0.12) / 0.58, 0), 1)
        let texture = min(max((Double(crossings) / Double(count) - 0.035) / 0.16, 0), 1)
        DispatchQueue.main.async { [weak self] in
            guard let self, self.wantsRecording, self.sessionGeneration == generation else { return }
            IosBreathBridge.shared.updateMetrics(
                amplitude: amplitude,
                lowFrequencyPresence: lowPresence,
                noisyTexture: texture
            )
        }
    }

    private func resetAnalysisState() {
        previousInput = 0; previousHighPassed = 0; lowPassed500 = 0; lowPassed2000 = 0; previousBandLimited = 0
    }
}
