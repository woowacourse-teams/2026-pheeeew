package com.pheeeew.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class MonitoringHttpBaseline {

    private static final int WARMUP = 3;
    private static final int SAMPLES = 20;
    private final String baseUrl;
    private final String token;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;
    private final List<Map<String, Object>> results = new ArrayList<>();

    MonitoringHttpBaseline(String baseUrl, String token, ObjectMapper mapper, MeterRegistry registry) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.mapper = mapper;
        this.registry = registry;
    }

    void run() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String level : List.of("SIDO", "SIGUNGU", "EMD")) {
                measure(client, "regions_" + level, request("/api/v2/emotions/map/regions?minLongitude=126"
                        + "&maxLongitude=128&minLatitude=37&maxLatitude=38&level=" + level).GET().build(),
                        (body, index) -> {
                            assertThat(body.isArray()).isTrue();
                            long total = 0;
                            for (JsonNode item : body) {
                                assertThat(item.path("properties").path("level").asString()).isEqualTo(level);
                                total += item.path("properties").path("totalCount").asLong();
                            }
                            assertThat(total).isEqualTo(10000);
                        }, "pheeeew.region.query", "intersecting_regions");
            }
            for (String platform : List.of("android", "ios")) {
                measure(client, "version_" + platform, request("/api/v2/app/version?platform=" + platform).GET().build(),
                        (body, index) -> assertThat(body.path("latestVersion").asString()).isEqualTo("1.0.0"), null, null);
            }
            measure(client, "health", request("/actuator/health").GET().build(),
                    (body, index) -> assertThat(body.path("status").asString()).isEqualTo("UP"), null, null);
            measure(client, "press_me", request("/api/v2/emotions/presses/me?daysAgo=0").GET().build(),
                    (body, index) -> assertThat(body.path("total").asLong()).isEqualTo(5),
                    "pheeeew.emotion.press.query", "my_daily");
            measure(client, "press_total", request("/api/v2/emotions/presses/total?daysAgo=0").GET().build(),
                    (body, index) -> assertThat(body.path("total").asLong()).isEqualTo(500),
                    "pheeeew.emotion.press.query", "daily_total");
            // 쓰기는 마지막에 실행해 앞선 조회의 초기 조건을 유지한다. 워밍업도 실제로 누른다.
            measure(client, "press_write", request("/api/v2/emotions/presses").header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"counts\":{\"ANGRY\":1}}")).build(),
                    (body, index) -> {
                        assertThat(body.path("counts").path("ANGRY").asLong()).isEqualTo(2 + index);
                        assertThat(body.path("total").asLong()).isEqualTo(6 + index);
                    }, "pheeeew.emotion.press.query", "increase");
        }
        Path output = Path.of("build/reports/monitoring/baseline.json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                "generatedAt", Instant.now().toString(), "profile", "test", "concurrency", 1,
                "warmup", WARMUP, "samplesPerCase", SAMPLES, "targetStartIntervalMs", 1000, "results", results));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + token);
    }

    private void measure(HttpClient client, String name, HttpRequest request, BiConsumer<JsonNode, Integer> check,
                         String metric, String operation) throws Exception {
        var samples = new ArrayList<Double>();
        var warmup = new ArrayList<Double>();
        Map<String, double[]> before = Map.of();
        long measuredStart = 0;
        for (int index = 0; index < WARMUP + SAMPLES; index++) {
            if (index == WARMUP) {
                before = timers();
                measuredStart = System.nanoTime();
            }
            long start = System.nanoTime();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            assertThat(response.statusCode()).as("%s HTTP status", name).isEqualTo(200);
            check.accept(mapper.readTree(response.body()), index);
            (index < WARMUP ? warmup : samples).add(elapsedMs);
            // 응답이 1초를 넘으면 밀린 요청을 한꺼번에 보내지 않고 다음 1건만 시작한다.
            if (index + 1 < WARMUP + SAMPLES) {
                TimeUnit.NANOSECONDS.sleep(Math.max(0, TimeUnit.SECONDS.toNanos(1) - (System.nanoTime() - start)));
            }
        }
        double wallMs = (System.nanoTime() - measuredStart) / 1_000_000.0;
        var stages = new ArrayList<Map<String, Object>>();
        for (var entry : timers().entrySet()) {
            double[] previous = before.getOrDefault(entry.getKey(), new double[]{0, 0});
            double count = entry.getValue()[0] - previous[0];
            if (count > 0) {
                double totalMs = entry.getValue()[1] - previous[1];
                stages.add(Map.of("timer", entry.getKey(), "count", count, "totalMs", totalMs, "meanMs", totalMs / count));
            }
        }
        if (metric != null) {
            Timer timer = registry.get(metric).tags("operation", operation, "outcome", "success").timer();
            assertThat(timer.count() - before.get(timer.getId().toString())[0]).as("%s 계측 호출 수", name).isEqualTo(SAMPLES);
        }
        var sorted = samples.stream().sorted().toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("case", name);
        result.put("method", request.method());
        result.put("path", request.uri().getRawPath() + (request.uri().getRawQuery() == null ? "" : "?" + request.uri().getRawQuery()));
        result.put("warmupMs", warmup);
        result.put("samplesMs", samples);
        result.put("p50Ms", sorted.get((int) Math.ceil(SAMPLES * 0.50) - 1));
        result.put("p95Ms", sorted.get((int) Math.ceil(SAMPLES * 0.95) - 1));
        result.put("maxMs", sorted.get(SAMPLES - 1));
        result.put("measuredWallMs", wallMs);
        result.put("dbTimers", stages);
        results.add(result);
    }

    private Map<String, double[]> timers() {
        var snapshot = new TreeMap<String, double[]>();
        for (Timer timer : registry.find("pheeeew.region.query").timers()) {
            snapshot.put(timer.getId().toString(), new double[]{timer.count(), timer.totalTime(TimeUnit.MILLISECONDS)});
        }
        for (Timer timer : registry.find("pheeeew.emotion.press.query").timers()) {
            snapshot.put(timer.getId().toString(), new double[]{timer.count(), timer.totalTime(TimeUnit.MILLISECONDS)});
        }
        for (Timer timer : registry.find("spring.data.repository.invocations").timers()) {
            snapshot.put(timer.getId().toString(), new double[]{timer.count(), timer.totalTime(TimeUnit.MILLISECONDS)});
        }
        return snapshot;
    }
}
