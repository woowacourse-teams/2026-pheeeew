package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

final class E001Runners {

    private E001Runners() {
    }

    static void distribution() throws IOException {
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
        Path project = Path.of(required("e001.projectDir"));
        String gradle = required("e001.gradleVersion");
        var metadata = E001RunContext.capture(project, gradle);
        E001SidecarStore.publish(Path.of(required("e001.reportsDir")), "performance", E001RunContext.runId(),
                System.getProperty("e001.invalidationReason", ""), metadata, (staging, root, source) -> {
                    E001ArtifactFormat.write(staging.resolve("environment.json"), E001ArtifactFormat.json(metadata.environment()));
                    var batches = E001Performance.measureTo(staging.resolve("performance.csv"), source.d(), source.e());
                    var dGate = E001Performance.evaluate(batches, "D");
                    Map<String, Object> result = new java.util.TreeMap<>();
                    result.put("machineRows", Map.of("performance.csv", batches.size()));
                    result.put("dMedianNs", dGate.medianNs());
                    result.put("dMaxNs", dGate.maxNs());
                    result.put("dPerformanceGatePassed", dGate.passed());
                    if (source.e() != null) {
                        var eGate = E001Performance.evaluate(batches, "E");
                        result.put("eMedianNs", eGate.medianNs());
                        result.put("eMaxNs", eGate.maxNs());
                        result.put("ePerformanceGatePassed", eGate.passed());
                    }
                    E001RunContext.verifyUnchanged(project, gradle, metadata);
                    return result;
                });
    }

    static void blind() throws IOException {
        Path project = Path.of(required("e001.projectDir"));
        String gradle = required("e001.gradleVersion");
        var metadata = E001RunContext.capture(project, gradle);
        E001SidecarStore.publish(Path.of(required("e001.reportsDir")), "blind", E001RunContext.runId(),
                System.getProperty("e001.invalidationReason", ""), metadata, (staging, root, source) -> {
                    boolean includeE = E001SidecarStore.verifyPerformance(root, source);
                    var assignment = E001Blind.assign(includeE);
                    E001Blind.write(staging, root, source, assignment);
                    E001RunContext.verifyUnchanged(project, gradle, metadata);
                    int pairs = includeE ? 8 : 4;
                    return Map.of("machineRows", Map.of("coordinator-only/blind-key.csv", pairs,
                            "reviewer-package/blind-pairs.csv", pairs, "reviewer-package/blind-ballot-template.csv", pairs),
                            "reviewStatus", "awaiting-five-reviewers");
                });
    }

    private static String required(String key) throws IOException {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("전용 Gradle 실험 task로 실행해야 해요.");
        }
        return value;
    }
}
