package com.pheeeew.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class MonitoringRegionsCompanionLoad {

    private static final Map<String, String> PATHS = Map.of(
            "press_write", "/api/v2/emotions/presses",
            "regions", "/api/v2/emotions/map/regions?minLongitude=126&maxLongitude=128&minLatitude=37&maxLatitude=38&level=EMD",
            "version_android", "/api/v2/app/version?platform=android", "version_ios", "/api/v2/app/version?platform=ios",
            "health", "/actuator/health", "press_me", "/api/v2/emotions/presses/me?daysAgo=0",
            "press_total", "/api/v2/emotions/presses/total?daysAgo=0");
    private final String baseUrl;
    private final String token;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;
    private final HikariPoolMXBean pool;
    private final AtomicLongArray attemptedPosts = new AtomicLongArray(32);
    private final AtomicLongArray successfulPosts = new AtomicLongArray(32);

    private record Workload(int regions, int presses, boolean sameDevice, int intervalMs, boolean stress) {
    }

    MonitoringRegionsCompanionLoad(String baseUrl, String token, ObjectMapper mapper, MeterRegistry registry,
                                  HikariPoolMXBean pool) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.mapper = mapper;
        this.registry = registry;
        this.pool = pool;
    }

    void run() throws Exception {
        run(List.of(new Workload(0, 0, false, 1000, false), new Workload(4, 0, false, 1000, false),
                new Workload(16, 0, false, 1000, false)), List.of(token), "regions-companions.json");
    }

    Map<Integer, Long> runPress(List<String> tokens) throws Exception {
        assertThat(tokens).hasSize(32);
        run(List.of(new Workload(0, 0, false, 100, true), new Workload(0, 32, true, 100, true),
                new Workload(0, 32, false, 100, true), new Workload(32, 0, false, 100, true),
                new Workload(32, 32, false, 100, true)), tokens, "press-companions.json");
        return postCounts();
    }

    private void run(List<Workload> workloads, List<String> tokens, String filename) throws Exception {
        var phases = new ArrayList<Map<String, Object>>();
        try (var client = HttpClient.newHttpClient()) {
            for (Workload workload : workloads) {
                var phase = phase(client, workload, tokens);
                phases.add(phase);
                Path output = Path.of("build/reports/monitoring", filename);
                Files.createDirectories(output.getParent());
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                        "generatedAt", Instant.now().toString(), "profile", "test", "targetStartIntervalMs", 1000,
                        "poolSampleIntervalMs", 100, "warmup", 3, "samplesPerProbe", 20, "phases", phases));
                assertThat(phase.get("errors")).as("정상 응답/완료 조건; SLO 판정 아님").isEqualTo(List.of());
            }
        }
    }

    private Map<String, Object> phase(HttpClient client, Workload workload, List<String> tokens) throws Exception {
        var publishing = new AtomicBoolean(true);
        var sampling = new AtomicBoolean(true);
        var budgetExhausted = new AtomicBoolean(false);
        var successfulBefore = postCounts();
        var startGate = new CountDownLatch(1);
        var errors = new ArrayList<String>();
        var regionRows = new ArrayList<Map<String, Object>>();
        var workerRows = new ArrayList<List<Map<String, Object>>>();
        var probes = new TreeMap<String, List<Map<String, Object>>>();
        var poolRows = new ArrayList<Map<String, Object>>();
        var before = timers();
        long startedAt = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> sampler = executor.submit(() -> {
                try {
                    startGate.await();
                    while (sampling.get()) {
                        // 발행 예산이 끝나도 진행 중 요청 회수까지 풀 관측은 유지한다.
                        if (workload.stress() && elapsed(startedAt) >= 45000
                                && publishing.compareAndSet(true, false)) {
                            budgetExhausted.set(true);
                        }
                        var os = ManagementFactory.getOperatingSystemMXBean();
                        poolRows.add(Map.of("offsetMs", elapsed(startedAt), "active", pool.getActiveConnections(),
                                "waiting", pool.getThreadsAwaitingConnection(), "total", pool.getTotalConnections(),
                                "heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                                "processCpu", os instanceof com.sun.management.OperatingSystemMXBean nativeOs
                                        ? nativeOs.getProcessCpuLoad() : -1));
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                } finally {
                    publishing.set(false);
                }
                return null;
            });
            var loadTasks = new ArrayList<Future<?>>();
            for (int n = 0; n < workload.regions() + workload.presses(); n++) {
                String kind = n < workload.regions() ? "regions" : "press_write";
                int deviceIndex = kind.equals("regions") || workload.sameDevice() ? 0 : n - workload.regions();
                var rows = new ArrayList<Map<String, Object>>();
                workerRows.add(rows);
                loadTasks.add(executor.submit(() -> {
                    startGate.await();
                    repeat(client, kind, rows, publishing, startedAt, Integer.MAX_VALUE,
                            tokens.get(deviceIndex), deviceIndex, workload.intervalMs(), workload.stress());
                    return null;
                }));
            }
            var probeTasks = new ArrayList<Future<?>>();
            for (String kind : new TreeMap<>(PATHS).keySet()) {
                if (kind.equals("regions") || kind.equals("press_write")) {
                    continue;
                }
                var rows = new ArrayList<Map<String, Object>>();
                probes.put(kind, rows);
                probeTasks.add(executor.submit(() -> {
                    startGate.await();
                    repeat(client, kind, rows, publishing, startedAt, 23, token, 0, 1000, workload.stress());
                    return null;
                }));
            }
            startGate.countDown();
            try {
                await(probeTasks, publishing, errors);
            } finally {
                publishing.set(false);
                try {
                    await(loadTasks, publishing, errors);
                } finally {
                    sampling.set(false);
                    await(List.of(sampler), publishing, errors);
                }
            }
        }
        var pressRows = new ArrayList<Map<String, Object>>();
        workerRows.forEach(rows -> rows.forEach(row ->
                (row.get("kind").equals("regions") ? regionRows : pressRows).add(row)));
        if (poolRows.isEmpty() || probes.values().stream().anyMatch(rows -> rows.size() != 23)) {
            errors.add("incomplete_observation");
        }
        if (probes.values().stream().flatMap(List::stream).anyMatch(row -> !Boolean.TRUE.equals(row.get("valid")))
                || workerRows.stream().flatMap(List::stream).anyMatch(row -> !Boolean.TRUE.equals(row.get("valid")))) {
            errors.add("invalid_response_or_transport_error");
        }
        if (budgetExhausted.get() && probes.values().stream().anyMatch(rows -> rows.size() != 23)) {
            errors.add("budget_exhausted");
        }
        if (workerRows.stream().flatMap(List::stream).anyMatch(row -> "HttpTimeoutException".equals(row.get("errorClass")))
                || probes.values().stream().flatMap(List::stream).anyMatch(row -> "HttpTimeoutException".equals(row.get("errorClass")))) {
            errors.add("request_timeout");
        }
        var after = timers();
        var stages = new TreeMap<String, Map<String, Double>>();
        after.forEach((id, values) -> {
            double[] previous = before.getOrDefault(id, new double[]{0, 0});
            if (values[0] > previous[0]) {
                stages.put(id, Map.of("count", values[0] - previous[0], "totalMs", values[1] - previous[1]));
            }
        });
        var result = new LinkedHashMap<String, Object>();
        result.put("regionsWorkers", workload.regions());
        result.put("pressWorkers", workload.presses());
        result.put("samePressDevice", workload.sameDevice());
        result.put("loadTargetIntervalMs", workload.intervalMs());
        result.put("newRequestBudgetMs", workload.stress() ? 45000 : 0);
        result.put("newRequestBudgetExhausted", budgetExhausted.get());
        result.put("durationMs", elapsed(startedAt));
        result.put("probes", probes);
        result.put("regionRequests", regionRows);
        result.put("pressRequests", pressRows);
        result.put("successfulPostsBefore", successfulBefore);
        result.put("successfulPostsAfter", postCounts());
        result.put("unknownPressOutcomes", pressRows.stream().filter(row -> !Boolean.TRUE.equals(row.get("valid"))).count());
        result.put("poolSamples", poolRows);
        result.put("wholePhaseDbTimers", stages);
        result.put("errors", errors);
        return result;
    }

    private void await(List<Future<?>> tasks, AtomicBoolean publishing, List<String> errors) throws InterruptedException {
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (ExecutionException failure) {
                publishing.set(false);
                errors.add(failure.getCause().getClass().getSimpleName());
            }
        }
    }

    private void repeat(HttpClient client, String kind, List<Map<String, Object>> rows, AtomicBoolean publishing,
                        long phaseStart, int limit, String requestToken, int deviceIndex, int intervalMs,
                        boolean stress) throws InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + PATHS.get(kind)))
                .timeout(Duration.ofSeconds(stress ? 300 : 60)).header("Authorization", "Bearer " + requestToken);
        var request = kind.equals("press_write")
                ? builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"counts\":{\"ANGRY\":1}}")).build()
                : builder.GET().build();
        for (int n = 0; n < limit && publishing.get(); n++) {
            long start = System.nanoTime();
            // 응답 수신 시 성공 누계가 증가해도 늦게 도착한 정상 응답을 오판하지 않는다.
            long minimum = kind.equals("press_total") ? total(successfulPosts) : successfulPosts.get(deviceIndex);
            if (kind.equals("press_write")) {
                minimum++;
                attemptedPosts.incrementAndGet(deviceIndex);
            }
            int status = 0;
            double ms = -1;
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                ms = elapsed(start);
                status = response.statusCode();
                boolean valid = status == 200 && valid(kind, mapper.readTree(response.body()), deviceIndex, minimum);
                rows.add(Map.of("startMs", (start - phaseStart) / 1_000_000.0, "elapsedMs", ms,
                        "status", status, "valid", valid, "kind", kind, "deviceIndex", deviceIndex));
                if (valid && kind.equals("press_write")) {
                    successfulPosts.incrementAndGet(deviceIndex);
                }
                if (!valid) {
                    publishing.set(false);
                }
            } catch (Exception failure) {
                rows.add(Map.of("startMs", (start - phaseStart) / 1_000_000.0, "elapsedMs", ms >= 0 ? ms : elapsed(start),
                        "status", status, "valid", false, "errorClass", failure.getClass().getSimpleName(),
                        "kind", kind, "deviceIndex", deviceIndex));
                publishing.set(false);
            }
            if (n + 1 < limit && publishing.get()) {
                TimeUnit.NANOSECONDS.sleep(Math.max(0, TimeUnit.MILLISECONDS.toNanos(intervalMs) - (System.nanoTime() - start)));
            }
        }
    }

    private boolean valid(String kind, JsonNode body, int deviceIndex, long minimum) {
        if (kind.equals("regions")) {
            long total = 0;
            if (!body.isArray()) {
                return false;
            }
            for (JsonNode item : body) {
                if (!item.path("properties").path("level").asString("").equals("EMD")) {
                    return false;
                }
                total += item.path("properties").path("totalCount").asLong(-1);
            }
            return total == 10000;
        }
        return switch (kind) {
            case "press_me", "press_write" -> validPress(body, minimum, attemptedPosts.get(deviceIndex));
            case "press_total" -> body.path("total").asLong(-1) >= 500 + minimum
                    && body.path("total").asLong(-1) <= 500 + total(attemptedPosts);
            case "health" -> body.path("status").asString("").equals("UP");
            default -> body.path("latestVersion").asString("").equals("1.0.0");
        };
    }

    private boolean validPress(JsonNode body, long minimum, long attempts) {
        JsonNode counts = body.path("counts");
        long angry = counts.path("ANGRY").asLong(-1);
        return counts.isObject() && counts.size() == 5 && angry >= 1 + minimum && angry <= 1 + attempts
                && List.of("FRUSTRATED", "IRRITATED", "EXHAUSTED", "DISCOURAGED").stream()
                        .allMatch(state -> counts.path(state).asLong(-1) == 1)
                && body.path("total").asLong(-1) == angry + 4;
    }

    private Map<Integer, Long> postCounts() {
        var counts = new TreeMap<Integer, Long>();
        IntStream.range(0, 32).forEach(index -> counts.put(index, successfulPosts.get(index)));
        return counts;
    }

    private long total(AtomicLongArray counts) {
        return IntStream.range(0, counts.length()).mapToLong(counts::get).sum();
    }

    private Map<String, double[]> timers() {
        var result = new TreeMap<String, double[]>();
        for (String name : List.of("pheeeew.region.query", "pheeeew.emotion.press.query", "spring.data.repository.invocations")) {
            for (Timer timer : registry.find(name).timers()) {
                result.put(timer.getId().toString(), new double[]{timer.count(), timer.totalTime(TimeUnit.MILLISECONDS)});
            }
        }
        return result;
    }

    private double elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }
}
