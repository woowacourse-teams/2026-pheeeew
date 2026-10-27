package com.pheeeew.pressrace.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 앱 이미지와 저장소 nginx 설정을 사용하는 선택 실행 시험이다.
 * PHEEEEW_PRESS_OPERATIONS_IMAGE에 이번 소스로 빌드한 로컬 이미지 태그를 지정한다.
 * 합성 WS 관찰이며 실제 앱 화면 지연·운영 배포·부하 시험을 대신하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "PHEEEEW_PRESS_OPERATIONS_IMAGE", matches = ".+")
class PressOperationsProxyIntegrationTest {

    private static final String GATE = "/app/press-admission";
    private static final String NORMAL_WS = "/api/v1/press-race";
    private static final String RECOVERY_WS = NORMAL_WS + "/recovery";
    private static final UUID BLUE_GENERATION = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<WebSocket> sockets = new ArrayList<>();
    private final String prefix = "press-ops-" + UUID.randomUUID().toString().substring(0, 8);

    @TempDir
    private Path fixture;

    private Network network;
    private PostgreSQLContainer database;
    private GenericContainer<?> blue;
    private GenericContainer<?> green;
    private GenericContainer<?> nginx;
    private HttpClient client;
    private KeyPair signingKeys;
    private Path work;
    private UUID devicePublicId;
    private long deviceId;
    private String token;
    private LocalDate activationDate;
    private UUID candidateGeneration;

    @BeforeEach
    void setUp() throws Exception {
        network = Network.newNetwork();
        database = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:17-3.5")
                .asCompatibleSubstituteFor("postgres"))
                .withNetwork(network).withNetworkAliases("fixture-db")
                .withDatabaseName("press_operations").withUsername("fixture").withPassword("fixture");
        database.start();
        signingKeys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        blue = startApp("blue", BLUE_GENERATION);
        createFixture();
        update("UPDATE press_race_control SET active_server_generation = '" + BLUE_GENERATION
                + "', enabled = TRUE, activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date WHERE id = TRUE");
        activationDate = LocalDate.parse(scalar("SELECT activation_date::text FROM press_race_control"));
        fixtureGate(blue, "OPEN");
        createProxy();
        createOperator();
        token = token();
    }

    @AfterEach
    void tearDown() {
        sockets.forEach(WebSocket::abort);
        if (client != null) {
            client.close();
        }
        if (nginx != null) {
            nginx.stop();
        }
        if (green != null) {
            green.stop();
        }
        if (blue != null) {
            blue.stop();
        }
        if (database != null) {
            database.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    void 정상_ON_배포는_nginx를_전환하고_새_WS에서_미확인_입력을_중복_없이_정산한다() throws Exception {
        // given: 실제 nginx 인증 경로에 확정 성공·미확정 접수·미접수 입력이 공존한다.
        RecoveryInputs inputs = prepareRecoveryInputs();
        prepareCandidate();

        // when: 경쟁 상태와 날짜를 바꾸지 않고 정상 세대·프록시·입력 자격을 전환한다.
        operator("handoff", greenName());
        operator("close-active", blueName());
        pointAt("app-green");
        operator("verify", greenName());
        operator("open-target", greenName());

        // then: 새 session.ready로 정산한 뒤에도 동일 입력은 한 번만 가산된다.
        assertThat(inputs.original().closed.poll(8, TimeUnit.SECONDS)).isNotNull();
        assertThat(gateMode(green)).isEqualTo("OPEN");
        assertThat(scalar("SELECT enabled::text FROM press_race_control")).isEqualTo("true");
        assertThat(scalar("SELECT activation_date::text FROM press_race_control")).isEqualTo(activationDate.toString());
        assertCandidateRecovery(inputs, 4);
        operator("cleanup-active", blueName());
        clearJournal();
        blue = null;
        assertTotals(5);
        assertThat(work.resolve(".press-deploy.json")).doesNotExist();
    }

    /** 보류 중인 CLOSED 재시작 후 HTTP timeout을 포함한다. 정상 ON 경계 통과와 구분한다. */
    @Test
    void 실제_nginx와_Spring_배포_복귀_정산_중단_재시작에서도_프레스가_중복_가산되지_않는다() throws Exception {
        RecoveryInputs inputs = prepareRecoveryInputs();
        SocketListener original = inputs.original();
        UUID applied = inputs.applied();
        UUID pending = inputs.pending();
        UUID absent = inputs.absent();
        List<Map<String, Object>> unknown = inputs.unknown();

        // when: 실제 Spring HTTP 저장을 DB trigger에서 멈춰 배타 전환의 종료 대기를 확인한다.
        prepareCandidate();
        long lockKey = UUID.randomUUID().getMostSignificantBits();
        update("CREATE FUNCTION hold_operations_press() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN PERFORM pg_advisory_xact_lock(" + lockKey + "); RETURN NEW; END $$");
        update("CREATE TRIGGER hold_operations_press AFTER INSERT OR UPDATE ON device_daily_presses "
                + "FOR EACH ROW WHEN (NEW.device_id = " + deviceId + ") EXECUTE FUNCTION hold_operations_press()");
        try (Connection inflight = connection(); var executor = Executors.newFixedThreadPool(2)) {
            inflight.setAutoCommit(false);
            inflight.createStatement().execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
            long timingStart = System.nanoTime();
            timing(timingStart, "fixture-lock-acquired");
            CompletableFuture<HttpResponse<String>> saving = CompletableFuture.supplyAsync(() -> {
                try {
                    timing(timingStart, "http-start");
                    HttpResponse<String> response = press();
                    timing(timingStart, "http-response-" + response.statusCode());
                    return response;
                } catch (Exception exception) {
                    timing(timingStart, "http-failure-" + exception.getClass().getSimpleName());
                    throw new IllegalStateException(exception);
                }
            }, executor);
            await(this::savingWaitsForFixture, "실제 Spring HTTP 저장 트랜잭션의 advisory 대기");
            timing(timingStart, "http-db-wait-observed");
            Files.createFile(work.resolve("lose-db-answer"));
            CompletableFuture<Integer> handoff = CompletableFuture.supplyAsync(() -> {
                timing(timingStart, "handoff-start");
                int code = operatorCode("handoff", greenName());
                timing(timingStart, "handoff-exit-" + code);
                return code;
            }, executor);
            await(() -> gateMode(blue).equals("PAUSED"), "원본 신규 입력 차단");
            timing(timingStart, "paused-observed");
            assertThat(handoff.isDone()).as("진행 중 공유 잠금이 끝나기 전에는 세대를 넘기지 않는다").isFalse();
            timing(timingStart, "rejected-http-start");
            assertThat(press().statusCode()).isEqualTo(503);
            timing(timingStart, "rejected-http-503");
            timing(timingStart, "totals-read-start");
            assertTotals(4);
            timing(timingStart, "totals-read-end");
            timing(timingStart, "fixture-lock-release-start");
            inflight.commit();
            timing(timingStart, "fixture-lock-release-end");
            assertThat(saving.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            timing(timingStart, "saving-result-confirmed");
            assertThat(handoff.get(35, TimeUnit.SECONDS)).isZero();
            assertThat(work.resolve("lose-db-answer")).doesNotExist();
        } finally {
            update("DROP TRIGGER hold_operations_press ON device_daily_presses");
            update("DROP FUNCTION hold_operations_press()");
        }
        operator("close-active", blueName());
        pointAt("app-green");
        operator("verify", greenName());

        // then: 후보 입력 개방 전 실패 복귀는 ON과 날짜를 유지하되 구 연결 자격을 살리지 않는다.
        operator("restore", greenName());
        pointAt("app-blue");
        operator("open-active", blueName());
        assertThat(scalar("SELECT enabled::text FROM press_race_control")).isEqualTo("true");
        assertThat(scalar("SELECT activation_date::text FROM press_race_control")).isEqualTo(activationDate.toString());
        assertThat(original.closed.poll(8, TimeUnit.SECONDS)).isNotNull();
        SocketListener restored = connect(NORMAL_WS);
        JsonNode restoredReady = initialize(restored, unknown, "session.ready");
        assertRecovered(restoredReady, applied, pending, absent, 5);
        restored.send(submit(applied, restoredReady, 3));
        assertThat(restored.receive("input.result").path("status").stringValue()).isEqualTo("APPLIED");
        assertTotals(5);
        operator("cleanup-target", greenName());
        clearJournal();
        green = null;

        // when: 실제 OPEN CAS가 끝난 뒤 docker-exec 응답을 유실시켜 파일 재조회로 정산한다.
        prepareCandidate();
        operator("handoff", greenName());
        operator("close-active", blueName());
        pointAt("app-green");
        operator("verify", greenName());
        Files.createFile(work.resolve("lose-open-answer"));
        assertThat(operatorCode("open-target", greenName())).isZero();
        assertThat(work.resolve("lose-open-answer")).doesNotExist();
        // 성공한 exec 응답을 잃었어도 실제 파일로 확인한다. 개방 이후 ON 자동 복귀도 금지된다.
        assertThat(operatorCode("restore", greenName())).isNotZero();
        assertThat(gateMode(green)).isEqualTo("OPEN");
        assertCandidateRecovery(inputs, 5);
        operator("cleanup-active", blueName());
        clearJournal();
        blue = null;

        // then: 같은 컨테이너를 CLOSED 상태로 재시작해도 차단과 UUID가 보존된다.
        fixtureGate(green, "CLOSED");
        String gateBeforeRestart = gate(green);
        String containerId = green.getContainerId();
        command(List.of("docker", "restart", containerId), fixture, Map.of());
        await(() -> appHealthy(green), "실제 앱 컨테이너 재시작 완료");
        assertThat(green.getContainerId()).isEqualTo(containerId);
        assertThat(gate(green)).isEqualTo(gateBeforeRestart);
        assertThat(press().statusCode()).isEqualTo(503);
        assertTotals(6);
        // 재시작 시험의 수동 차단은 격리 fixture에서만 해제한다. 실제 복구 명령의 우회가 아니다.
        fixtureGate(green, "OPEN");

        // when: 긴급 OFF는 HTTP를 유지하고, 복구 소켓에서 신규 점수·순위는 허용하지 않는다.
        operator("stop", greenName(), "--expected-generation", greenGeneration().toString(),
                "--expected-activation-date", activationDate.toString(), "--probe-port", proxyPort());
        assertThat(scalar("SELECT enabled::text FROM press_race_control")).isEqualTo("false");
        assertThat(press().statusCode()).isEqualTo(200);
        SocketListener recovery = connect(RECOVERY_WS);
        JsonNode recovered = initialize(recovery, unknown, "session.recovered");
        assertThat(recovered.path("inputEnabled").booleanValue()).isFalse();
        assertRecovered(recovered, applied, pending, absent, 7);
        recovery.send(submit(UUID.randomUUID(), recovered, 10));
        assertThat(recovery.receive("protocol.error").path("code").stringValue()).isEqualTo("RECOVERY_ONLY");
        assertTotals(7);
        SocketListener disabled = connect(NORMAL_WS);
        assertThat(disabled.receive("protocol.error").path("code").stringValue()).isEqualTo("RACE_DISABLED");

        // then: 최초/재활성화 예약은 명시한 다음 KST 날짜 전의 경쟁 쓰기를 열지 않는다.
        LocalDate next = LocalDate.parse(scalar("SELECT ((clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date + 1)::text"));
        operator("activate", greenName(), "--expected-generation", greenGeneration().toString(),
                "--expected-activation-date", activationDate.toString(), "--activation-date", next.toString(),
                "--probe-port", proxyPort());
        assertThat(scalar("SELECT activation_date::text FROM press_race_control")).isEqualTo(next.toString());
        SocketListener early = connect(NORMAL_WS);
        assertThat(early.receive("protocol.error").path("code").stringValue()).isEqualTo("RACE_DISABLED");
        assertThat(recovery.receivedTypes).doesNotContain("session.ready", "state.snapshot");
        assertTotals(7);
        assertProxyTransportTermination();
    }

    private RecoveryInputs prepareRecoveryInputs() throws Exception {
        HttpResponse<String> unauthenticated = request("GET", "/api/v2/emotions/presses/me", null, false);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(press().statusCode()).isEqualTo(200);
        SocketListener original = connect(NORMAL_WS);
        JsonNode ready = initialize(original, List.of(), "session.ready");
        UUID applied = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID absent = UUID.randomUUID();
        String connection = ready.path("connectionGeneration").stringValue();
        original.send(submit(applied, ready, 3));
        // 성공 응답은 앱에서 소비하지 않는다. PENDING은 장애 시점을 고정한 별도 DB fixture다.
        await(() -> total() == 4, "첫 입력의 원본·투영 저장");
        pendingFixture(pending, connection, 2);
        assertTotals(4);
        return RecoveryInputs.of(original, applied, pending, absent, List.of(
                pending(applied, connection, 3), pending(pending, connection, 2), pending(absent, connection, 2)));
    }

    private void assertCandidateRecovery(RecoveryInputs inputs, long expected) throws Exception {
        SocketListener current = connect(NORMAL_WS);
        JsonNode ready = initialize(current, inputs.unknown(), "session.ready");
        assertThat(ready.path("serverGeneration").stringValue()).isEqualTo(greenGeneration().toString());
        assertRecovered(ready, inputs.applied(), inputs.pending(), inputs.absent(), expected);
        current.send(submit(inputs.applied(), ready, 3));
        assertThat(current.receive("input.result").path("status").stringValue()).isEqualTo("APPLIED");
        assertTotals(expected);
        assertThat(press().statusCode()).isEqualTo(200);
        assertTotals(expected + 1);
    }

    private record RecoveryInputs(SocketListener original, UUID applied, UUID pending, UUID absent,
                                  List<Map<String, Object>> unknown) {

        private static RecoveryInputs of(SocketListener original, UUID applied, UUID pending, UUID absent,
                                         List<Map<String, Object>> unknown) {
            return new RecoveryInputs(original, applied, pending, absent, unknown);
        }
    }

    private void assertProxyTransportTermination() throws Exception {
        // 날짜 경계는 앞에서 검증했다. 아래는 현재 날짜의 연결 종료만 위한 격리 DB fixture다.
        update("UPDATE press_race_control SET activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date WHERE id = TRUE");
        List<Long> participants = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            long participant = insertDevice(UUID.randomUUID());
            participants.add(participant);
            update("INSERT INTO device_daily_presses(press_date, state, device_id, press_count, created_at, updated_at) "
                    + "VALUES ((clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, 'ANGRY', " + participant
                    + ", 1, clock_timestamp(), clock_timestamp())");
            update("INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id) VALUES ("
                    + participant + ", (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, 1, clock_timestamp(), gen_random_uuid())");
        }
        UUID slowDevice = UUID.randomUUID();
        UUID heartbeatDevice = UUID.randomUUID();
        insertDevice(slowDevice);
        insertDevice(heartbeatDevice);
        SocketListener normal = connect(NORMAL_WS);
        initialize(normal, List.of(), "session.ready");
        normal.send(Map.of("v", 1, "type", "view.change", "view", "ALL"));
        JsonNode all = normal.receive("state.snapshot");
        if (!all.path("view").stringValue().equals("ALL")) {
            all = normal.receive("state.snapshot");
        }
        assertThat(all.path("view").stringValue()).isEqualTo("ALL");
        assertThat(all.path("top100").size()).isEqualTo(100);

        try (RawPeer noPong = rawPeer(RECOVERY_WS, token(heartbeatDevice));
                RawPeer stalled = rawPeer(NORMAL_WS, token(slowDevice))) {
            stalled.send(Map.of("v", 1, "type", "session.init", "view", "ALL", "pending", List.of()));
            String ids = participants.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(50);
            int revision = 0;
            while (System.nanoTime() < deadline) {
                String prefix = revision++ % 2 == 0 ? "가나다라마바사아자" : "차자아사바마라다나";
                update("UPDATE devices SET nickname = '" + prefix + "' || chr(44032 + id::integer) WHERE id IN (" + ids + ")");
                assertThat(normal.closed).as("정상 Pong 연결은 느린 상대와 무관하게 유지").isEmpty();
                Thread.sleep(300);
            }
            String heartbeat = noPong.termination();
            assertThat(heartbeat).contains("HEARTBEAT_TIMEOUT");
            assertThat(noPong.pings).as("실제 nginx를 거친 서버 Ping").isPositive();
            System.out.println("nginx no-Pong termination=" + heartbeat + ", pings=" + noPong.pings);
            String slow = stalled.termination();
            assertThat(slow).as("느린 nginx 경유 연결도 종료됨").isNotBlank();
            assertThat(press().statusCode()).as("상대 연결 종료 뒤 정상 HTTP 저장 진행").isEqualTo(200);
            assertTotals(8);
            System.out.println("nginx transport fixture: no-Pong=" + heartbeat + ", stalled-reader=" + slow);
        } catch (Exception | AssertionError failure) {
            List<String> diagnostic = nginx.getLogs().lines()
                    .filter(line -> line.contains("/api/v1/press-race") || line.contains("timed out") || line.contains("upstream"))
                    .map(line -> line.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~-]+", "$1[redacted]"))
                    .toList();
            System.out.println("nginx transport failure log:\n"
                    + String.join("\n", diagnostic.subList(Math.max(0, diagnostic.size() - 40), diagnostic.size())));
            throw failure;
        }
    }

    private GenericContainer<?> startApp(String color, UUID generation) {
        GenericContainer<?> app = new GenericContainer<>(DockerImageName.parse(System.getenv("PHEEEEW_PRESS_OPERATIONS_IMAGE")))
                .withNetwork(network).withNetworkAliases("app-" + color)
                .withCreateContainerCmdModifier(command -> command.withName(prefix + "-app-" + color))
                .withExposedPorts(8080)
                .withEnv(Map.ofEntries(
                        Map.entry("SPRING_CONFIG_LOCATION", "classpath:/application.yml"),
                        Map.entry("SPRING_PROFILES_ACTIVE", "test"),
                        Map.entry("SPRING_DATASOURCE_URL", "jdbc:postgresql://fixture-db:5432/press_operations?sslmode=disable"),
                        Map.entry("SPRING_DATASOURCE_USERNAME", database.getUsername()),
                        Map.entry("SPRING_DATASOURCE_PASSWORD", database.getPassword()),
                        Map.entry("DB_URL", "jdbc:postgresql://fixture-db:5432/press_operations?sslmode=disable"),
                        Map.entry("DB_USERNAME", database.getUsername()), Map.entry("DB_PASSWORD", database.getPassword()),
                        Map.entry("JWT_PRIVATE_KEY_BASE64", Base64.getEncoder().encodeToString(signingKeys.getPrivate().getEncoded())),
                        Map.entry("JWT_PUBLIC_KEY_BASE64", Base64.getEncoder().encodeToString(signingKeys.getPublic().getEncoded())),
                        Map.entry("S3_BUCKET", "pheeeew-test"), Map.entry("S3_KEY_PREFIX", "pheeeew/test/"),
                        Map.entry("S3_REGION", "ap-northeast-2"), Map.entry("LOGGING_LEVEL_ROOT", "WARN"),
                        Map.entry("PHEEEEW_MAP_CACHE_MAINTENANCE_AUTOMATIC_START", "false"),
                        Map.entry("JAVA_TOOL_OPTIONS", "-Xmx384m"),
                        Map.entry("PHEEEEW_PRESS_RACE_SERVER_GENERATION", generation.toString())))
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8080).forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        app.start();
        return app;
    }

    private void createFixture() throws Exception {
        devicePublicId = UUID.randomUUID();
        deviceId = insertDevice(devicePublicId);
    }

    private long insertDevice(UUID publicId) throws Exception {
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                INSERT INTO devices(public_id, request_id, platform, created_at, updated_at)
                VALUES (?, ?, 'ANDROID', clock_timestamp(), clock_timestamp()) RETURNING id
                """)) {
            insert.setObject(1, publicId);
            insert.setObject(2, UUID.randomUUID());
            try (var result = insert.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private void createProxy() throws Exception {
        Path certificates = Files.createDirectories(fixture.resolve("certificates"));
        command(List.of("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                "-subj", "/CN=api-dev.pheeeew.com", "-addext", "subjectAltName=DNS:localhost,DNS:api-dev.pheeeew.com,IP:127.0.0.1",
                "-keyout", certificates.resolve("privkey.pem").toString(),
                "-out", certificates.resolve("fullchain.pem").toString()), fixture, Map.of());
        Files.copy(certificates.resolve("fullchain.pem"), certificates.resolve("origin.pem"));
        Files.copy(certificates.resolve("fullchain.pem"), certificates.resolve("origin-pull-ca.pem"));
        Files.copy(certificates.resolve("privkey.pem"), certificates.resolve("origin.key"));
        work = Files.createDirectories(fixture.resolve("operator"));
        Path upstream = Files.createDirectories(work.resolve("nginx/upstream"));
        Files.writeString(upstream.resolve("active.conf"), "set $upstream_app app-blue;\n");
        Path configuration = fixture.resolve("api-dev.conf");
        // 로컬 호스트명만 더하고 Upgrade·인증·timeout 설정은 저장소 원문 그대로 검증한다.
        Files.writeString(configuration, Files.readString(Path.of("docker/nginx/api-dev.conf"))
                .replace("server_name api-dev.pheeeew.com;", "server_name api-dev.pheeeew.com localhost;"));
        nginx = new GenericContainer<>(DockerImageName.parse("nginx:1.27-alpine"))
                .withNetwork(network).withExposedPorts(443)
                .withFileSystemBind(configuration.toString(), "/etc/nginx/conf.d/default.conf", BindMode.READ_ONLY)
                .withFileSystemBind(upstream.toString(), "/etc/nginx/upstream", BindMode.READ_ONLY)
                .withFileSystemBind(certificates.toString(), "/etc/ssl/cloudflare", BindMode.READ_ONLY)
                .withFileSystemBind(certificates.toString(), "/etc/letsencrypt/live/api-dev.pheeeew.com", BindMode.READ_ONLY)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
        nginx.start();
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        try (var certificate = Files.newInputStream(certificates.resolve("fullchain.pem"))) {
            trust.setCertificateEntry("fixture", CertificateFactory.getInstance("X.509").generateCertificate(certificate));
        }
        TrustManagerFactory manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        manager.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, manager.getTrustManagers(), null);
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).sslContext(context)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private void createOperator() throws Exception {
        Files.copy(Path.of("docker/press-generation.py"), work.resolve("press-generation.py"));
        if (Files.exists(Path.of("docker/press-operations.py"))) {
            Files.copy(Path.of("docker/press-operations.py"), work.resolve("press-operations.py"));
        }
        // 진단용 관찰만 추가한다. 호출 인자·SQL·타임아웃·예외 처리는 원래 도구 그대로다.
        Files.writeString(work.resolve("profile-handoff.py"), """
                import runpy
                import sys
                import time

                started = time.monotonic_ns()
                watched = {'check_instance', 'runtime_generation', 'probe_generation', 'probe_output',
                           'read_state', 'query', 'gate', 'set_gate', 'change', 'check', 'transition'}
                def profile(frame, event, arg):
                    if event not in ('call', 'return') or frame.f_code.co_name not in watched:
                        return
                    source = frame.f_code.co_filename.rsplit('/', 1)[-1]
                    if source not in ('press-generation.py', 'press-operations.py'):
                        return
                    print('HANDOFF_TIMING wall_ms=%d elapsed_ms=%.3f event=%s function=%s' % (
                        time.time_ns() // 1000000, (time.monotonic_ns() - started) / 1000000,
                        event, frame.f_code.co_name), file=sys.stderr, flush=True)

                sys.argv[0] = 'press-generation.py'
                sys.setprofile(profile)
                try:
                    runpy.run_path('press-generation.py', run_name='__main__')
                finally:
                    sys.setprofile(None)
                """);
        Path tools = Files.createDirectories(work.resolve("bin"));
        Path docker = tools.resolve("docker");
        Files.writeString(docker, """
                #!/bin/sh
                lose=no
                if [ -f "$PRESS_FIXTURE_WORK/lose-open-answer" ] && [ "$1" = exec ]; then
                  for value in "$@"; do case "$value" in 'OPEN '*) lose=yes;; esac; done
                fi
                if [ "$lose" = yes ]; then
                  "$PRESS_FIXTURE_DOCKER" "$@" >/dev/null || exit $?
                  rm "$PRESS_FIXTURE_WORK/lose-open-answer"
                  exit 1
                fi
                exec "$PRESS_FIXTURE_DOCKER" "$@"
                """);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
        Path psql = tools.resolve("psql");
        Files.writeString(psql, """
                #!/bin/sh
                set -eu
                query=$(mktemp)
                trap 'rm -f "$query"' EXIT
                cat > "$query"
                if [ -f "$PRESS_FIXTURE_WORK/lose-db-answer" ] && grep -q 'UPDATE press_race_control' "$query"; then
                  "$PRESS_FIXTURE_DOCKER" exec -i -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGOPTIONS \
                    "$PRESS_FIXTURE_DB" psql "$@" < "$query" >/dev/null
                  rm "$PRESS_FIXTURE_WORK/lose-db-answer"
                  exit 1
                fi
                "$PRESS_FIXTURE_DOCKER" exec -i -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGOPTIONS \
                  "$PRESS_FIXTURE_DB" psql "$@" < "$query"
                """);
        assertThat(psql.toFile().setExecutable(true)).isTrue();
    }

    private void prepareCandidate() throws Exception {
        operator("prepare", blueName(), "app-green", "--probe-port", proxyPort());
        candidateGeneration = UUID.fromString(json.readTree(Files.readString(work.resolve(".press-deploy.json")))
                .path("target_generation").stringValue());
        green = startApp("green", candidateGeneration);
        assertThat(gateMode(green)).isEqualTo("CLOSED");
        assertThat(scalar("SELECT active_server_generation::text FROM press_race_control"))
                .isEqualTo(BLUE_GENERATION.toString());
        operator("record-target", greenName());
    }

    private void pendingFixture(UUID requestId, String connectionGeneration, int count) throws Exception {
        String normalized = "ANGRY=" + count + ",DISCOURAGED=0,EXHAUSTED=0,FRUSTRATED=0,IRRITATED=0";
        try (Connection connection = connection(); var insert = connection.prepareStatement("""
                INSERT INTO press_inputs(device_id, request_id, normalized_counts, content_hash,
                    first_received_at, original_generation, status)
                VALUES (?, ?, ?, ?, clock_timestamp(), ?, 'PENDING')
                """)) {
            insert.setLong(1, deviceId);
            insert.setObject(2, requestId);
            insert.setString(3, normalized);
            insert.setString(4, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8))));
            insert.setObject(5, UUID.fromString(connectionGeneration));
            insert.executeUpdate();
        }
    }

    private void fixtureGate(GenericContainer<?> app, String mode) throws Exception {
        String before = gate(app);
        String generation = before.split(" ")[2].strip();
        String after = mode + " " + UUID.randomUUID() + " " + generation;
        var result = app.execInContainer("flock", "-x", "-w", "5", GATE + ".lock", "sh", "-eu", "-c", """
                [ "$(cat "$1")" = "$2" ]
                printf '%s\n' "$3" > "$1.new"
                sync -f "$1.new"
                mv "$1.new" "$1"
                sync -f /app
                """, "sh", GATE, before, after);
        assertThat(result.getExitCode()).as("격리 fixture의 admission CAS").isZero();
    }

    private String gate(GenericContainer<?> app) {
        try {
            var result = app.execInContainer("cat", GATE);
            assertThat(result.getExitCode()).isZero();
            return result.getStdout().strip();
        } catch (Exception exception) {
            throw new IllegalStateException("Fixture admission 파일 조회 실패", exception);
        }
    }

    private String gateMode(GenericContainer<?> app) {
        return gate(app).split(" ")[0];
    }

    private boolean appHealthy(GenericContainer<?> app) {
        try {
            return app.execInContainer("curl", "-q", "--fail", "--silent", "--max-time", "2",
                    "http://127.0.0.1:8080/actuator/health").getExitCode() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void pointAt(String slot) throws Exception {
        Files.writeString(work.resolve("nginx/upstream/active.conf"), "set $upstream_app " + slot + ";\n");
        assertThat(nginx.execInContainer("nginx", "-t").getExitCode()).isZero();
        assertThat(nginx.execInContainer("nginx", "-s", "reload").getExitCode()).isZero();
        await(() -> {
            try {
                return request("GET", "/api/v3/devices/nicknames/availability?nickname=probe", null, false)
                        .headers().firstValue("X-Upstream").orElse("").equals(slot);
            } catch (Exception ignored) {
                return false;
            }
        }, "실제 nginx의 새 라우팅");
    }

    private void operator(String... arguments) throws Exception {
        assertThat(operatorCode(arguments)).as("배포 도구 %s", arguments[0]).isZero();
    }

    private int operatorCode(String... arguments) {
        try {
            boolean timing = arguments[0].equals("handoff");
            List<String> command = new ArrayList<>(List.of("python3",
                    timing ? "profile-handoff.py" : "press-generation.py"));
            command.addAll(List.of(arguments));
            ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile());
            builder.environment().put("PATH", work.resolve("bin") + ":" + builder.environment().get("PATH"));
            builder.environment().put("CURL_CA_BUNDLE", fixture.resolve("certificates/fullchain.pem").toString());
            builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
            builder.environment().put("PRESS_FIXTURE_DOCKER", realDocker());
            builder.environment().put("PRESS_FIXTURE_DB", database.getContainerId());
            builder.environment().put("PRESS_FIXTURE_WORK", work.toString());
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(fixture.resolve("operator-error.log").toFile());
            Process process = builder.start();
            try {
                assertThat(process.waitFor(45, TimeUnit.SECONDS)).as("배포 도구의 로컬 실행 상한").isTrue();
                return process.exitValue();
            } finally {
                process.destroyForcibly();
                if (timing) {
                    for (String line : Files.readAllLines(fixture.resolve("operator-error.log"))) {
                        if (line.startsWith("HANDOFF_TIMING ")) {
                            System.out.println(line);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("격리 배포 도구 실행 실패", exception);
        }
    }

    private void clearJournal() throws Exception {
        Files.delete(work.resolve(".press-deploy.json"));
    }

    private void timing(long started, String event) {
        System.out.printf("HTTP_TIMING wall_ms=%d elapsed_ms=%.3f event=%s%n",
                System.currentTimeMillis(), (System.nanoTime() - started) / 1_000_000.0, event);
    }

    private UUID greenGeneration() {
        return candidateGeneration;
    }

    private HttpResponse<String> press() throws Exception {
        return request("POST", "/api/v2/emotions/presses", "{\"counts\":{\"ANGRY\":1}}", true);
    }

    private HttpResponse<String> request(String method, String path, String body, boolean authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://localhost:" + proxyPort() + path))
                .timeout(Duration.ofSeconds(8));
        if (authenticated) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private SocketListener connect(String path) {
        SocketListener listener = new SocketListener();
        listener.socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                .connectTimeout(Duration.ofSeconds(8)).buildAsync(URI.create("wss://localhost:" + proxyPort() + path), listener).join();
        sockets.add(listener.socket);
        return listener;
    }

    private JsonNode initialize(SocketListener listener, List<Map<String, Object>> pending, String type) throws Exception {
        listener.send(Map.of("v", 1, "type", "session.init", "view", "PRESS", "pending", pending));
        return listener.receive(type);
    }

    private Map<String, Object> pending(UUID requestId, String generation, int count) {
        return Map.of("requestId", requestId.toString(), "connectionGeneration", generation, "counts", Map.of("ANGRY", count));
    }

    private Map<String, Object> submit(UUID requestId, JsonNode ready, int count) {
        return Map.of("v", 1, "type", "press.submit", "requestId", requestId.toString(),
                "connectionGeneration", ready.path("connectionGeneration").stringValue(), "counts", Map.of("ANGRY", count));
    }

    private void assertRecovered(JsonNode ready, UUID applied, UUID pending, UUID absent, long total) {
        assertThat(ready.path("counts").path("ANGRY").longValue()).isEqualTo(total);
        Map<String, String> expected = Map.of(applied.toString(), "APPLIED", pending.toString(), "REJECTED", absent.toString(), "REJECTED");
        assertThat(ready.path("results").size()).isEqualTo(3);
        for (JsonNode result : ready.path("results")) {
            assertThat(result.path("status").stringValue()).isEqualTo(expected.get(result.path("requestId").stringValue()));
        }
    }

    private long total() {
        try {
            return Long.parseLong(scalar("SELECT COALESCE(SUM(press_count), 0)::text FROM device_daily_presses WHERE device_id = " + deviceId));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean savingWaitsForFixture() {
        try {
            return Integer.parseInt(scalar("SELECT count(*)::text FROM pg_stat_activity "
                    + "WHERE datname = 'press_operations' AND wait_event = 'advisory'")) > 0;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertTotals(long expected) throws Exception {
        assertThat(total()).isEqualTo(expected);
        assertThat(Long.parseLong(scalar("SELECT COALESCE(SUM(total), 0)::text FROM press_daily_scores WHERE device_id = " + deviceId)))
                .isEqualTo(expected);
    }

    private void update(String sql) throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private String token() throws Exception {
        return token(devicePublicId);
    }

    private String token(UUID subject) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encoder.encodeToString(json.writeValueAsString(Map.of("iss", "https://pheeeew.com",
                "sub", subject.toString(), "iat", Instant.now().getEpochSecond(), "exp", Instant.now().plusSeconds(900).getEpochSecond(),
                "use", "ACCESS")).getBytes(StandardCharsets.UTF_8));
        String signing = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signingKeys.getPrivate());
        signature.update(signing.getBytes(StandardCharsets.UTF_8));
        return signing + "." + encoder.encodeToString(signature.sign());
    }

    private RawPeer rawPeer(String path, String accessToken) throws Exception {
        SSLSocket socket = (SSLSocket) client.sslContext().getSocketFactory().createSocket();
        // 64KiB 초과 TCP 수신 창은 연결 전에 요청해야 한다. 그 뒤 축소해 느린 수신을 만든다.
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html#setReceiveBufferSize(int)
        socket.setReceiveBufferSize(262144);
        int beforeConnectBuffer = socket.getReceiveBufferSize();
        socket.connect(new java.net.InetSocketAddress("localhost", Integer.parseInt(proxyPort())), 5000);
        socket.setSoTimeout(8000);
        var parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
        socket.startHandshake();
        String key = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().substring(0, 16).getBytes(StandardCharsets.US_ASCII));
        String upgrade = "GET " + path + " HTTP/1.1\r\nHost: localhost:" + proxyPort()
                + "\r\nAuthorization: Bearer " + accessToken + "\r\nUpgrade: websocket\r\nConnection: Upgrade"
                + "\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + key + "\r\n\r\n";
        socket.getOutputStream().write(upgrade.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        while (response.size() < 16384 && !response.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int value = socket.getInputStream().read();
            assertThat(value).as("실제 TLS WebSocket Upgrade 응답").isNotNegative();
            response.write(value);
        }
        assertThat(response.toString(StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 101 ");
        socket.setReceiveBufferSize(512);
        return new RawPeer(socket, path, beforeConnectBuffer, socket.getReceiveBufferSize());
    }

    private void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private void command(List<String> command, Path directory, Map<String, String> environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().putAll(environment);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(45, TimeUnit.SECONDS)).as("격리 fixture 명령 상한").isTrue();
            assertThat(process.exitValue()).as(command.get(0)).isZero();
        } finally {
            process.destroyForcibly();
        }
    }

    private String blueName() {
        return prefix + "-app-blue";
    }

    private String greenName() {
        return prefix + "-app-green";
    }

    private String proxyPort() {
        return nginx.getMappedPort(443).toString();
    }

    private String realDocker() throws Exception {
        Process process = new ProcessBuilder("/bin/sh", "-c", "command -v docker").start();
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    }

    private class RawPeer implements AutoCloseable {

        private final SSLSocket socket;
        private final String path;
        private final int beforeConnectBuffer;
        private final int stalledBuffer;
        private int pings;
        private int drainBuffer;
        private long drainStarted;
        private long drainedBytes;
        private int drainedFrames;
        private int frameOpcode = -1;
        private int frameLength = -1;
        private int frameOffset;

        private RawPeer(SSLSocket socket, String path, int beforeConnectBuffer, int stalledBuffer) {
            this.socket = socket;
            this.path = path;
            this.beforeConnectBuffer = beforeConnectBuffer;
            this.stalledBuffer = stalledBuffer;
        }

        private void send(Map<String, Object> message) throws Exception {
            byte[] payload = json.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
            assertThat(payload.length).isLessThan(126);
            byte[] mask = new byte[4];
            new SecureRandom().nextBytes(mask);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeByte(0x81);
            output.writeByte(0x80 | payload.length);
            output.write(mask);
            for (int index = 0; index < payload.length; index++) {
                output.writeByte(payload[index] ^ mask[index % 4]);
            }
            output.flush();
        }

        private String termination() throws Exception {
            // 읽기를 재개하기 전 창을 넓힌다. 위의 50초 동안은 TLS/WS payload를 읽지 않았다.
            socket.setReceiveBufferSize(262144);
            drainBuffer = socket.getReceiveBufferSize();
            drainStarted = System.nanoTime();
            long deadline = drainStarted + TimeUnit.SECONDS.toNanos(30);
            InputStream input = socket.getInputStream();
            try {
                while (System.nanoTime() < deadline) {
                    frameLength = -1;
                    frameOffset = 0;
                    int first = read(input, 1, deadline)[0] & 0xff;
                    frameOpcode = first & 0x0f;
                    int length = read(input, 1, deadline)[0] & 0xff;
                    assertThat(length & 0x80).as("서버 프레임은 mask하지 않음: %s", diagnostics()).isZero();
                    length &= 0x7f;
                    if (length == 126) {
                        byte[] extended = read(input, 2, deadline);
                        length = ((extended[0] & 0xff) << 8) | (extended[1] & 0xff);
                    } else {
                        assertThat(length).as("시험용 서버 프레임 크기 상한: %s", diagnostics()).isLessThan(127);
                    }
                    frameLength = length;
                    byte[] payload = read(input, length, deadline, true);
                    drainedFrames++;
                    if (frameOpcode == 9) {
                        pings++;
                    }
                    if (frameOpcode == 8) {
                        return observed(payload.length <= 2 ? "CLOSE"
                                : new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8));
                    }
                }
            } catch (SocketTimeoutException timeout) {
                throw new AssertionError("느린 연결의 서버 종료를 관찰하지 못함: " + diagnostics(), timeout);
            } catch (EOFException eof) {
                return observed("TRANSPORT_EOF");
            } catch (IOException transportClosed) {
                return observed("TRANSPORT_CLOSED/" + transportClosed.getClass().getSimpleName());
            }
            throw new AssertionError("느린 연결의 종료 프레임 읽기 상한 초과: " + diagnostics());
        }

        private byte[] read(InputStream input, int length, long deadline) throws IOException {
            return read(input, length, deadline, false);
        }

        private byte[] read(InputStream input, int length, long deadline, boolean payload) throws IOException {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) {
                    throw new SocketTimeoutException("Buffered WS frame drain deadline");
                }
                socket.setSoTimeout((int) Math.max(1, Math.min(remaining, 15000)));
                try {
                    int read = input.read(bytes, offset, length - offset);
                    if (read < 0) {
                        throw new EOFException();
                    }
                    offset += read;
                    drainedBytes += read;
                    if (payload) {
                        frameOffset = offset;
                    }
                } catch (SocketTimeoutException timeout) {
                    if (System.nanoTime() >= deadline) {
                        throw timeout;
                    }
                }
            }
            return bytes;
        }

        private String observed(String result) {
            System.out.println("nginx raw peer termination=" + result + ", " + diagnostics());
            return result;
        }

        private String diagnostics() {
            return "path=" + path + ", recvRequested=262144/512/262144, recvActual=" + beforeConnectBuffer
                    + "/" + stalledBuffer + "/" + drainBuffer + ", drainedBytes=" + drainedBytes
                    + ", drainedFrames=" + drainedFrames + ", frameOpcode=" + frameOpcode + ", frameLength=" + frameLength
                    + ", frameOffset=" + frameOffset + ", drainElapsedMs="
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - drainStarted);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    private class SocketListener implements WebSocket.Listener {

        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> receivedTypes = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> closed = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                JsonNode message = json.readTree(partial.toString());
                receivedTypes.add(message.path("type").stringValue());
                messages.add(message);
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.add(statusCode + "/" + reason);
            return null;
        }

        private void send(Map<String, Object> message) throws Exception {
            socket.sendText(json.writeValueAsString(message), true).join();
        }

        private JsonNode receive(String type) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (System.nanoTime() < deadline) {
                JsonNode message = messages.poll(1, TimeUnit.SECONDS);
                if (message != null && message.path("type").stringValue().equals(type)) {
                    return message;
                }
            }
            throw new AssertionError("실제 nginx WS 응답 없음: " + type + ", close=" + closed.peek());
        }
    }
}
