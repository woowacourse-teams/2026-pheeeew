package com.pheeeew.sigh.experiment.e001;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class E001RunContext {

    static final String BASE_PROTOCOL_BLOB_ID = "bc679aa3afc90de3f282ca7be449d18978156ad1";
    private static final String BASE_PROTOCOL = "docs/experiments/e001-map-star-location-distribution/README.md";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> SOURCES = List.of("build.gradle", "settings.gradle", "gradle", "gradlew",
            "src/test/java/com/pheeeew/sigh/experiment/e001", "src/test/resources/experiments/e001",
            BASE_PROTOCOL, "docs/experiments/e001-map-star-location-distribution/PROTOCOL-V2.md");

    private E001RunContext() {
    }

    static E001Artifacts.Metadata capture(Path project, String gradleVersion) throws IOException {
        List<String> status = new ArrayList<>(List.of("status", "--porcelain", "--"));
        status.addAll(SOURCES);
        if (!git(project, status).isBlank()) {
            throw new IOException("실험 관련 코드와 프로토콜을 먼저 커밋해야 해요.");
        }
        if (!BASE_PROTOCOL_BLOB_ID.equals(git(project, List.of("hash-object", BASE_PROTOCOL)))) {
            throw new IOException("동결된 v1 기준 프로토콜이 변경됐어요.");
        }
        String protocol = git(project, List.of("log", "-1", "--format=%H", "--", SOURCES.getLast()));
        List<String> history = new ArrayList<>(List.of("log", "-1", "--format=%H", "--"));
        history.addAll(SOURCES);
        String runner = git(project, history);
        Map<String, Object> environment = new HashMap<>();
        environment.put("jdkVendor", System.getProperty("java.vendor"));
        environment.put("jdkVersion", System.getProperty("java.version"));
        environment.put("osName", System.getProperty("os.name"));
        environment.put("osVersion", System.getProperty("os.version"));
        environment.put("osArch", System.getProperty("os.arch"));
        environment.put("processors", Runtime.getRuntime().availableProcessors());
        environment.put("gradleVersion", gradleVersion);
        environment.put("locale", Locale.getDefault().toLanguageTag());
        environment.put("timezone", TimeZone.getDefault().getID());
        environment.put("gitDirty", !git(project, List.of("status", "--porcelain")).isBlank());
        return E001Artifacts.Metadata.of(protocol, runner, environment);
    }

    static void verifyUnchanged(Path project, String gradleVersion, E001Artifacts.Metadata before) throws IOException {
        if (!before.equals(capture(project, gradleVersion))) {
            throw new IOException("실행 도중 코드 또는 환경이 바뀌었어요.");
        }
    }

    static String runId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static JsonNode read(Path path) throws IOException {
        return JSON.readTree(Files.readString(path, UTF_8));
    }

    static Map<String, Object> readMap(Path path) throws IOException {
        return new HashMap<>(JSON.readValue(Files.readString(path, UTF_8), new tools.jackson.core.type.TypeReference<Map<String, Object>>() { }));
    }

    static Source source(Path root, E001Artifacts.Metadata metadata) throws IOException {
        E001Checksums.verify(root);
        if (Files.mismatch(root.resolve("checksums.sha256"), root.resolve("verification-run1-checksums.sha256")) != -1) {
            throw new IOException("분포의 두 실행 checksum이 같지 않아요.");
        }
        JsonNode manifest = read(root.resolve("manifest.json"));
        String outcome = manifest.path("distributionOutcome").asString();
        if (!"E001-v2".equals(manifest.path("protocolVersion").asString())
                || manifest.path("schemaVersion").asInt() != 2
                || !BASE_PROTOCOL_BLOB_ID.equals(manifest.path("baseProtocolBlobId").asString())
                || !List.of("d-review-ready", "e-review-ready").contains(outcome)
                || !metadata.protocolSha().equals(manifest.path("protocolSha").asString())
                || !metadata.runnerSha().equals(manifest.path("runnerSha").asString())) {
            throw new IOException("같은 v2 protocol·runner의 d-review-ready 또는 e-review-ready 분포가 필요해요.");
        }
        String reason = manifest.path("outcomeReason").asString();
        if (outcome.equals("e-review-ready") ? !reason.equals("e-review-pending")
                : !List.of("no-e", "e-confirmation-failed", "e-spectral-failed").contains(reason)) {
            throw new IOException("분포 종료 상태와 이유가 맞지 않아요.");
        }
        E001Parameters d = selected(manifest, "D");
        // 탈락한 E의 이력은 manifest에 남아도 다음 단계의 후보로 복원하지 않아요.
        E001Parameters e = outcome.equals("e-review-ready") ? selected(manifest, "E") : null;
        if (e != null && d.sigma() != e.sigma()) {
            throw new IOException("선택한 D와 E의 sigma가 달라요.");
        }
        return Source.of(E001ArtifactFormat.sha256(root.resolve("checksums.sha256")), d, e);
    }

    private static E001Parameters selected(JsonNode manifest, String model) throws IOException {
        String id = manifest.path("selected" + model).asString();
        List<E001Parameters> matches = new ArrayList<>();
        for (JsonNode value : manifest.path("parameters")) {
            if (id.equals(value.path("parameterSetId").asString())) {
                try {
                    E001Parameters parameter = model.equals("D") ? E001Parameters.distance("D", value.path("sigmaMeters").asInt())
                            : E001Parameters.of(value.path("sigmaMeters").asInt(), E001Noise.valueOf(value.path("noise").asString()),
                                    E001FieldProfile.valueOf(value.path("profile").asString()), value.path("beta").asDouble(), value.path("sampler").asString());
                    if (!model.equals(value.path("modelId").asString()) || !id.equals(parameter.parameterSetId())) {
                        throw new IOException("선택 모델 식별자와 파라미터가 달라요.");
                    }
                    matches.add(parameter);
                } catch (IllegalArgumentException exception) {
                    throw new IOException("선택 모델 파라미터가 유효하지 않아요.", exception);
                }
            }
        }
        if (matches.size() != 1) {
            throw new IOException("선택 모델은 manifest에 정확히 한 번 있어야 해요.");
        }
        return matches.getFirst();
    }

    private static String git(Path project, List<String> arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of("git", "-C", project.toString()));
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            String output = new String(process.getInputStream().readAllBytes(), UTF_8).strip();
            if (process.waitFor() != 0) {
                throw new IOException("Git 실행 이력을 확인하지 못했어요.");
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Git 이력 확인이 중단됐어요.", exception);
        }
    }

    record Source(String checksum, E001Parameters d, E001Parameters e) {

        static Source of(String checksum, E001Parameters d, E001Parameters e) {
            return new Source(checksum, d, e);
        }
    }
}
