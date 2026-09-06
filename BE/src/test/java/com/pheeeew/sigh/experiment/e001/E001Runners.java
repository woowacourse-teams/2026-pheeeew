package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

final class E001Runners {

    private E001Runners() {
    }

    static void distribution() throws IOException {
        requireV2Ready();
        Path project = Path.of(required("e001.projectDir"));
        Path reports = Path.of(required("e001.reportsDir"));
        String gradle = required("e001.gradleVersion");
        var metadata = E001RunContext.capture(project, gradle);
        E001ArtifactStore.publish(reports, E001RunContext.runId(), staging -> {
            var result = E001Distribution.run(E001Trial::run, E001Parameters::logIntensityStd);
            var conformance = E001Conformance.generate(result, E001Parameters::pointSampler);
            E001Artifacts.write(staging, result, metadata, conformance);
            E001RunContext.verifyUnchanged(project, gradle, metadata);
        });
    }

    static void performance() throws IOException {
        requireV2Ready();
        Path project = Path.of(required("e001.projectDir"));
        String gradle = required("e001.gradleVersion");
        var metadata = E001RunContext.capture(project, gradle);
        E001SidecarStore.publish(Path.of(required("e001.reportsDir")), "performance", E001RunContext.runId(),
                System.getProperty("e001.invalidationReason", ""), metadata, (staging, root, source) -> {
                    E001ArtifactFormat.write(staging.resolve("environment.json"), E001ArtifactFormat.json(metadata.environment()));
                    var batches = E001Performance.measureTo(staging.resolve("performance.csv"), source.d(), source.e());
                    var gate = E001Performance.evaluate(batches);
                    E001RunContext.verifyUnchanged(project, gradle, metadata);
                    return Map.of("machineRows", Map.of("performance.csv", batches.size()),
                            "medianNs", gate.medianNs(), "maxNs", gate.maxNs(), "performanceGatePassed", gate.passed());
                });
    }

    static void blind() throws IOException {
        requireV2Ready();
        Path project = Path.of(required("e001.projectDir"));
        String gradle = required("e001.gradleVersion");
        var metadata = E001RunContext.capture(project, gradle);
        E001SidecarStore.publish(Path.of(required("e001.reportsDir")), "blind", E001RunContext.runId(),
                System.getProperty("e001.invalidationReason", ""), metadata, (staging, root, source) -> {
                    E001Blind.write(staging, root, source, E001Blind.assign());
                    E001RunContext.verifyUnchanged(project, gradle, metadata);
                    return Map.of("machineRows", Map.of("coordinator-only/blind-key.csv", 4,
                            "reviewer-package/blind-pairs.csv", 4, "reviewer-package/blind-ballot-template.csv", 4),
                            "reviewStatus", "awaiting-five-reviewers");
                });
    }

    private static void requireV2Ready() throws IOException {
        // R3에서 v2 source·산출물·sidecar 계약을 연결하기 전에는 기존 결과도 변경하지 않아요.
        throw new IOException("E001-v2 실행기 전환이 끝나지 않았어요. 실제 분포·성능·블라인드 실행은 차단해요.");
    }

    private static String required(String key) throws IOException {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("전용 Gradle 실험 task로 실행해야 해요.");
        }
        return value;
    }
}
