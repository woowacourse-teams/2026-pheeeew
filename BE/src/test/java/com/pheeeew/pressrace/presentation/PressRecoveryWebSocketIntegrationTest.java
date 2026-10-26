package com.pheeeew.pressrace.presentation;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.auth.infra.jwt.JwtTokenEncoder;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.domain.PressConnection;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({SharedPostgisTestConfiguration.class, SharedJwtTestConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2"
})
class PressRecoveryWebSocketIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private AccessTokenIssuer tokens;
    @Autowired
    private JwtTokenEncoder jwtEncoder;
    @Autowired
    private PressRaceService service;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<WebSocket> sockets = new ArrayList<>();
    private HttpClient client;
    private Device device;
    private String token;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        device = devices.save(기본_기기_빌더().build());
        token = tokens.issue(device.getPublicId()).accessToken();
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                    activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date WHERE id = TRUE
                """).param("generation", service.serverGeneration()).update();
    }

    @AfterEach
    void tearDown() {
        sockets.forEach(WebSocket::abort);
        client.close();
        jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", device.getId()).update();
        devices.deleteById(device.getId());
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE,
                    activation_date = NULL WHERE id = TRUE
                """).update();
    }

    @Test
    void 실제_재연결은_목록_밖_PENDING과_미접수_ID도_거절하고_늦은_입력을_가산하지_않는다() throws Exception {
        // given: 영속 접수 후 적용 전에 끊긴 입력과 DB에 도착하지 않은 입력을 구분한다.
        SocketListener previous = connect(token);
        JsonNode previousReady = initialize(previous, List.of());
        PressConnection oldConnection = connection(previousReady);
        UUID orphan = UUID.randomUUID();
        UUID unreceived = UUID.randomUUID();
        assertThat(service.receive(oldConnection, orphan, Map.of(EmotionState.ANGRY, 3)).status()).isEqualTo("PENDING");

        // when: 앱 목록에는 미접수 입력만 있어도 이전 세대 PENDING 전체를 정산한다.
        SocketListener current = connect(token);
        JsonNode ready = initialize(current, List.of(pending(unreceived, oldConnection.connectionGeneration(), 2)));
        current.socket.sendText(status(orphan, unreceived), true).join();
        JsonNode orphanResult = current.receive();
        JsonNode unreceivedResult = current.receive();
        current.socket.sendText(submit(unreceived, ready, 2), true).join();
        JsonNode late = current.receive();

        // then
        assertThat(previous.closeReasons.poll(5, TimeUnit.SECONDS)).isEqualTo("CONNECTION_REPLACED");
        assertThat(ready.path("results").size()).isEqualTo(1);
        assertRejected(ready.path("results").get(0), unreceived);
        assertRejected(orphanResult, orphan);
        assertRejected(unreceivedResult, unreceived);
        assertThat(late).isEqualTo(unreceivedResult);
        assertThatThrownBy(() -> service.apply(oldConnection, orphan, Map.of(EmotionState.ANGRY, 3)))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONNECTION_REPLACED"));
        assertThat(total()).isZero();
        Map<String, Object> tombstone = jdbc.sql("SELECT * FROM press_inputs WHERE device_id = :device AND request_id = :request")
                .param("device", device.getId()).param("request", unreceived).query().singleRow();
        assertThat(tombstone.get("first_received_at")).isNull();
        assertThat(tombstone.get("original_generation")).isNull();
    }

    @Test
    void 커밋_응답을_읽지_못한_앱은_같은_ID로_재연결하여_성공과_점수를_한_번만_복구한다() throws Exception {
        // given
        SocketListener previous = connect(token);
        JsonNode previousReady = initialize(previous, List.of());
        UUID requestId = UUID.randomUUID();
        previous.socket.sendText(submit(requestId, previousReady, 7), true).join();
        await(() -> total() == 7);
        previous.socket.abort();

        // when: 앱에서 성공 응답을 처리하지 못한 상태를 복구 목록으로 전달한다.
        SocketListener current = connect(token);
        JsonNode ready = initialize(current, List.of(pending(requestId,
                UUID.fromString(previousReady.path("connectionGeneration").stringValue()), 7)));
        current.socket.sendText(status(requestId), true).join();
        JsonNode result = current.receive();
        current.socket.sendText(submit(requestId, ready, 7), true).join();
        JsonNode duplicate = current.receive();

        // then
        assertThat(ready.path("counts").path("ANGRY").longValue()).isEqualTo(7);
        assertThat(ready.path("results").get(0).path("status").stringValue()).isEqualTo("APPLIED");
        assertThat(result.path("status").stringValue()).isEqualTo("APPLIED");
        assertThat(result.path("appliedCounts").path("ANGRY").intValue()).isEqualTo(7);
        assertThat(duplicate).isEqualTo(result);
        assertThat(total()).isEqualTo(7);
    }

    @Test
    void 전날_성공의_늦은_결과는_오늘_초기화와_새_입력의_날짜와_점수를_되돌리지_않는다() throws Exception {
        // given: 실제 자정 경과 대신 기존 성공 기록을 전날로 옮겨 날짜 경계를 준비한다.
        SocketListener previous = connect(token);
        JsonNode previousReady = initialize(previous, List.of());
        UUID oldRequest = UUID.randomUUID();
        previous.socket.sendText(submit(oldRequest, previousReady, 7), true).join();
        assertThat(previous.receive().path("status").stringValue()).isEqualTo("APPLIED");
        LocalDate today = jdbc.sql("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date")
                .query(LocalDate.class).single();
        LocalDate yesterday = today.minusDays(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.sql("UPDATE press_inputs SET applied_date = :date WHERE device_id = :device AND request_id = :request")
                    .param("date", yesterday).param("device", device.getId()).param("request", oldRequest).update();
            jdbc.sql("UPDATE device_daily_presses SET press_date = :date WHERE device_id = :device")
                    .param("date", yesterday).param("device", device.getId()).update();
            jdbc.sql("UPDATE press_daily_scores SET press_date = :date WHERE device_id = :device")
                    .param("date", yesterday).param("device", device.getId()).update();
        });

        // when
        SocketListener current = connect(token);
        JsonNode ready = initialize(current, List.of(pending(oldRequest,
                UUID.fromString(previousReady.path("connectionGeneration").stringValue()), 7)));
        JsonNode baseline = current.snapshot();
        current.socket.sendText(status(oldRequest), true).join();
        JsonNode oldResult = current.receive();
        current.socket.sendText(submit(UUID.randomUUID(), ready, 2), true).join();
        JsonNode newResult = current.receive();
        JsonNode updated;
        do {
            updated = current.snapshot();
        } while (updated.path("me").path("score").longValue() != 2);

        // then
        assertThat(ready.path("date").stringValue()).isEqualTo(today.toString());
        assertThat(ready.path("counts").path("ANGRY").longValue()).isZero();
        assertThat(ready.path("results").get(0).path("appliedDate").stringValue()).isEqualTo(yesterday.toString());
        assertThat(oldResult.path("appliedDate").stringValue()).isEqualTo(yesterday.toString());
        assertThat(oldResult.path("appliedCounts").path("ANGRY").intValue()).isEqualTo(7);
        assertThat(baseline.path("baseline").booleanValue()).isTrue();
        assertThat(baseline.path("me").path("participating").booleanValue()).isFalse();
        assertThat(newResult.path("appliedDate").stringValue()).isEqualTo(today.toString());
        assertThat(updated.path("date").stringValue()).isEqualTo(today.toString());
        assertThat(updated.path("me").path("score").longValue()).isEqualTo(2);
        assertThat(total()).isEqualTo(9);
    }

    @Test
    void 복구_상한이_지난_PENDING_조회는_실패_확정이_아니며_새_연결이_최종_정산한다() throws Exception {
        // given: 클라이언트 20초 정책을 서버 TTL로 오해하지 않도록 접수 나이만 준비한다.
        SocketListener previous = connect(token);
        JsonNode previousReady = initialize(previous, List.of());
        UUID requestId = UUID.randomUUID();
        assertThat(service.receive(connection(previousReady), requestId, Map.of(EmotionState.ANGRY, 3)).status())
                .isEqualTo("PENDING");
        jdbc.sql("""
                UPDATE press_inputs SET first_received_at = clock_timestamp() - INTERVAL '21 seconds'
                WHERE device_id = :device AND request_id = :request
                """).param("device", device.getId()).param("request", requestId).update();

        // when
        previous.socket.sendText(status(requestId), true).join();
        JsonNode unresolved = previous.receive();
        SocketListener current = connect(token);
        JsonNode ready = initialize(current, List.of(pending(requestId,
                UUID.fromString(previousReady.path("connectionGeneration").stringValue()), 3)));

        // then
        assertThat(unresolved.path("status").stringValue()).isEqualTo("PENDING");
        assertThat(unresolved.has("appliedCounts")).isFalse();
        assertRejected(ready.path("results").get(0), requestId);
        assertThat(total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 21})
    void 실제_초기화는_미확인_20묶음까지만_원자적으로_정산한다(int pendingCount) throws Exception {
        // given
        SocketListener socket = connect(token);
        UUID oldGeneration = UUID.randomUUID();
        List<Map<String, Object>> pending = IntStream.range(0, pendingCount)
                .mapToObj(index -> pending(UUID.randomUUID(), oldGeneration, 1)).toList();

        // when
        socket.socket.sendText(json.writeValueAsString(Map.of("v", 1, "type", "session.init", "view", "PRESS",
                "pending", pending)), true).join();
        JsonNode response = socket.receive();

        // then
        if (pendingCount == 20) {
            assertThat(response.path("type").stringValue()).isEqualTo("session.ready");
            assertThat(response.path("results").size()).isEqualTo(20);
            for (JsonNode result : response.path("results")) {
                assertThat(result.path("status").stringValue()).isEqualTo("REJECTED");
                assertThat(result.path("code").stringValue()).isEqualTo("NOT_APPLIED_ON_RECOVERY");
            }
        } else {
            assertThat(response.path("type").stringValue()).isEqualTo("protocol.error");
            assertThat(response.path("code").stringValue()).isEqualTo("INVALID_INPUT");
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM press_inputs WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single()).isEqualTo(pendingCount == 20 ? 20 : 0);
        assertThat(total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 새_입력이나_화면_변경이_없어도_만료된_인증의_연결을_닫는다(boolean initialized) throws Exception {
        // given: 화면 변화 없이도 서버가 만료를 감지해야 한다.
        String shortToken = jwtEncoder.encodeAccessToken(device.getPublicId().toString(), Duration.ofSeconds(4));
        SocketListener socket = connect(shortToken);
        if (initialized) {
            initialize(socket, List.of());
            socket.snapshot();
        }

        // when
        String reason = socket.closeReasons.poll(8, TimeUnit.SECONDS);

        // then
        assertThat(reason).isEqualTo("AUTH_EXPIRED");
        assertThat(total()).isZero();
    }

    @Test
    void 새_WS는_구_WS의_진행_저장_커밋을_기다리고_확정_성공을_복구한다() throws Exception {
        // given: 실제 DB 쓰기 트랜잭션을 advisory gate에서 멈춘다.
        SocketListener previous = connect(token);
        JsonNode previousReady = initialize(previous, List.of());
        UUID requestId = UUID.randomUUID();
        long lockKey = UUID.randomUUID().getMostSignificantBits();
        jdbc.sql("""
                CREATE FUNCTION pause_press_recovery_ws_test() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN PERFORM pg_advisory_xact_lock(%d); RETURN NEW; END $$
                """.formatted(lockKey)).update();
        jdbc.sql("""
                CREATE TRIGGER pause_press_recovery_ws_test AFTER INSERT ON device_daily_presses
                FOR EACH ROW WHEN (NEW.device_id = %d) EXECUTE FUNCTION pause_press_recovery_ws_test()
                """.formatted(device.getId())).update();
        try (Connection gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try (var statement = gate.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
            }
            previous.socket.sendText(submit(requestId, previousReady, 4), true).join();
            awaitLock("device_daily_presses", "advisory");

            // when: Upgrade는 완료돼도 새 연결 세대는 진행 중 구 쓰기 뒤에 확정된다.
            SocketListener current = connect(token);
            current.socket.sendText(json.writeValueAsString(Map.of("v", 1, "type", "session.init", "view", "PRESS",
                    "pending", List.of(pending(requestId,
                            UUID.fromString(previousReady.path("connectionGeneration").stringValue()), 4)))), true).join();
            awaitLock("press_device_controls", null);
            assertThat(current.messages).isEmpty();
            gate.commit();
            JsonNode ready = current.receive();

            // then
            assertThat(ready.path("type").stringValue()).isEqualTo("session.ready");
            assertThat(ready.path("counts").path("ANGRY").longValue()).isEqualTo(4);
            assertThat(ready.path("results").get(0).path("status").stringValue()).isEqualTo("APPLIED");
            assertThat(total()).isEqualTo(4);
        } finally {
            jdbc.sql("DROP TRIGGER pause_press_recovery_ws_test ON device_daily_presses").update();
            jdbc.sql("DROP FUNCTION pause_press_recovery_ws_test()").update();
        }
    }

    private void assertRejected(JsonNode result, UUID requestId) {
        assertThat(result.path("requestId").stringValue()).isEqualTo(requestId.toString());
        assertThat(result.path("status").stringValue()).isEqualTo("REJECTED");
        assertThat(result.path("code").stringValue()).isEqualTo("NOT_APPLIED_ON_RECOVERY");
        assertThat(result.has("appliedDate")).isFalse();
        assertThat(result.has("appliedCounts")).isFalse();
    }

    private PressConnection connection(JsonNode ready) {
        return PressConnection.of(device.getPublicId(), UUID.fromString(ready.path("serverGeneration").stringValue()),
                UUID.fromString(ready.path("connectionGeneration").stringValue()), Instant.now().plusSeconds(600));
    }

    private Map<String, Object> pending(UUID requestId, UUID generation, int count) {
        return Map.of("requestId", requestId.toString(), "connectionGeneration", generation.toString(),
                "counts", Map.of("ANGRY", count));
    }

    private String submit(UUID requestId, JsonNode ready, int count) {
        return json.writeValueAsString(Map.of("v", 1, "type", "press.submit", "requestId", requestId.toString(),
                "connectionGeneration", ready.path("connectionGeneration").stringValue(), "counts", Map.of("ANGRY", count)));
    }

    private String status(UUID... requestIds) {
        return json.writeValueAsString(Map.of("v", 1, "type", "press.status", "requestIds", requestIds));
    }

    private long total() {
        return jdbc.sql("SELECT COALESCE(SUM(press_count), 0) FROM device_daily_presses WHERE device_id = :device")
                .param("device", device.getId()).query(Long.class).single();
    }

    private void awaitLock(String table, String event) throws InterruptedException {
        await(() -> jdbc.sql("""
                SELECT COUNT(*) FROM pg_stat_activity
                WHERE datname = current_database() AND state = 'active' AND wait_event_type = 'Lock'
                  AND query ILIKE :table AND (:event IS NULL OR wait_event = :event)
                """).param("table", "%" + table + "%").param("event", event, java.sql.Types.VARCHAR)
                .query(Long.class).single() > 0);
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("기대한 DB 커밋/잠금 상태에 도달하지 않음");
    }

    private SocketListener connect(String accessToken) throws Exception {
        SocketListener listener = new SocketListener();
        listener.socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + accessToken)
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/press-race"), listener)
                .get(10, TimeUnit.SECONDS);
        sockets.add(listener.socket);
        return listener;
    }

    private JsonNode initialize(SocketListener listener, List<Map<String, Object>> pending) throws Exception {
        listener.socket.sendText(json.writeValueAsString(Map.of("v", 1, "type", "session.init", "view", "PRESS",
                "pending", pending)), true).join();
        JsonNode ready = listener.receive();
        assertThat(ready.path("type").stringValue()).isEqualTo("session.ready");
        return ready;
    }

    private final class SocketListener implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> snapshots = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> closeReasons = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();
        private WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                String message = fragments.toString();
                if (json.readTree(message).path("type").stringValue().equals("state.snapshot")) {
                    snapshots.add(message);
                } else {
                    messages.add(message);
                }
                fragments.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeReasons.add(reason);
            return null;
        }

        private JsonNode receive() throws InterruptedException {
            String message = messages.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("인증된 실제 WS 응답").isNotNull();
            return json.readTree(message);
        }

        private JsonNode snapshot() throws InterruptedException {
            String message = snapshots.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("인증된 실제 WS 기준 상태").isNotNull();
            return json.readTree(message);
        }
    }
}
