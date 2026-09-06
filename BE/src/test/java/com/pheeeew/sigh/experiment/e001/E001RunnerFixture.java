package com.pheeeew.sigh.experiment.e001;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class E001RunnerFixture {

    static final E001Parameters D = E001Parameters.distance("D", 120);
    static final E001Parameters E = E001Parameters.of(120, E001Noise.GRADIENT, E001FieldProfile.P1, 0.4, "sir16");
    static final E001Artifacts.Metadata METADATA = E001Artifacts.Metadata.of("a".repeat(40), "b".repeat(40), Map.of());
    static final String FIRST = "1".repeat(32);
    static final String SECOND = "2".repeat(32);
    static final String THIRD = "3".repeat(32);

    private E001RunnerFixture() {
    }

    static Path distribution(Path parent) throws IOException {
        return distribution(parent, true);
    }

    static Path distribution(Path parent, boolean includeE) throws IOException {
        Path root = Files.createDirectory(parent.resolve("e001-v2"));
        Map<String, Object> manifest = new java.util.HashMap<>(Map.of("protocolVersion", "E001-v2", "schemaVersion", 2,
                "protocolSha", METADATA.protocolSha(), "runnerSha", METADATA.runnerSha(), "distributionOutcome", "e-review-ready",
                "selectedD", D.parameterSetId(), "selectedE", E.parameterSetId(), "parameters", List.of(
                        Map.of("modelId", "D", "parameterSetId", D.parameterSetId(), "sigmaMeters", 120),
                        Map.of("modelId", "E", "parameterSetId", E.parameterSetId(), "sigmaMeters", 120,
                                "noise", "GRADIENT", "profile", "P1", "beta", 0.4, "sampler", "sir16"))));
        manifest.put("baseProtocolBlobId", E001RunContext.BASE_PROTOCOL_BLOB_ID);
        manifest.put("distributionOutcome", includeE ? "e-review-ready" : "d-review-ready");
        manifest.put("outcomeReason", includeE ? "e-review-pending" : "e-spectral-failed");
        E001ArtifactFormat.write(root.resolve("manifest.json"), E001ArtifactFormat.json(manifest));
        for (String file : E001Checksums.REQUIRED) {
            if (!file.equals("manifest.json")) {
                E001ArtifactFormat.write(root.resolve(file), "fixture\n");
            }
        }
        seal(root);
        return root;
    }

    static void seal(Path root) throws IOException {
        Files.deleteIfExists(root.resolve("checksums.sha256"));
        E001Checksums.write(root);
        Files.copy(root.resolve("checksums.sha256"), root.resolve("verification-run1-checksums.sha256"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    static Map<String, Object> performance(Path staging, Path root, E001RunContext.Source source) throws IOException {
        List<E001Performance.Batch> batches = new java.util.ArrayList<>();
        for (String phase : List.of("warmup", "measurement")) {
            for (int index = 0; index < (phase.equals("warmup") ? 5 : 10); index++) {
                List<E001Parameters> parameters = source.e() == null ? List.of(source.d())
                        : index % 2 == 0 ? List.of(source.d(), source.e()) : List.of(source.e(), source.d());
                for (E001Parameters parameter : parameters) {
                    batches.add(E001Performance.Batch.of(parameter.modelId(), parameter.parameterSetId(), phase, index, 100_000, 100_000));
                }
            }
        }
        E001Performance.write(staging.resolve("performance.csv"), batches);
        E001ArtifactFormat.write(staging.resolve("environment.json"), "{}\n");
        return Map.of("machineRows", Map.of("performance.csv", batches.size()), "dPerformanceGatePassed", true);
    }
}
