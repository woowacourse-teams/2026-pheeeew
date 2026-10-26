package com.pheeeew.pressrace.presentation;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.domain.PressConnection;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import com.pheeeew.pressrace.application.PressRankingPublisher;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({SharedPostgisTestConfiguration.class, SharedJwtTestConfiguration.class,
        PressTransportWebSocketIntegrationTest.SmallSocketBuffers.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2"
})
class PressTransportWebSocketIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private AccessTokenIssuer tokens;
    @Autowired
    private PressRaceService service;
    @Autowired
    private PressRankingPublisher publisher;
    @MockitoSpyBean
    private PressRaceWebSocketHandler handler;

    private final List<Device> createdDevices = new ArrayList<>();
    private final List<WebSocket> sockets = new ArrayList<>();
    private final AtomicReference<WebSocketSession> slowSession = new AtomicReference<>();
    private final BlockingQueue<CloseStatus> slowClosed = new LinkedBlockingQueue<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpClient client;
    private Socket rawSocket;

    @BeforeEach
    void setUp() throws Exception {
        client = HttpClient.newHttpClient();
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                    activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date WHERE id = TRUE
                """).param("generation", service.serverGeneration()).update();
        // 실제 인증/handler/소켓을 유지하고 콜백에서 실제 세션과 종료 결과만 관찰한다.
        doAnswer(invocation -> {
            invocation.callRealMethod();
            slowSession.compareAndSet(null, invocation.getArgument(0));
            return null;
        }).when(handler).afterConnectionEstablished(any());
        doAnswer(invocation -> {
            WebSocketSession session = invocation.getArgument(0);
            CloseStatus status = invocation.getArgument(1);
            invocation.callRealMethod();
            if (session == slowSession.get()) {
                slowClosed.add(status);
            }
            return null;
        }).when(handler).afterConnectionClosed(any(), any());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rawSocket != null) {
            rawSocket.close();
        }
        sockets.forEach(WebSocket::abort);
        client.close();
        for (Device device : createdDevices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", device.getId()).update();
            devices.deleteById(device.getId());
        }
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE,
                    activation_date = NULL WHERE id = TRUE
                """).update();
    }

    @Test
    void 읽지_않는_실제_TCP_상대의_블로킹_송신을_종료하고_다른_인증_연결은_진행한다() throws Exception {
        // given: 정상 범위의 전체 순위가 작은 TCP 수신 창을 채우도록 한다. 운영 성능 시험은 아니다.
        for (int index = 0; index < 100; index++) {
            Device participant = createDevice();
            jdbc.sql("""
                    INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id)
                    VALUES (:device, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, :total,
                            clock_timestamp(), :publicId)
                    """).param("device", participant.getId()).param("total", 1000 + index)
                    .param("publicId", UUID.randomUUID()).update();
        }
        Device slow = createDevice();
        rawSocket = openNonReadingSocket(tokens.issue(slow.getPublicId()).accessToken());
        sendMaskedText(rawSocket, "{\"v\":1,\"type\":\"session.init\",\"view\":\"ALL\",\"pending\":[]}");

        // when: 정상 write의 일시적 TCP 지연 뒤에도 계속 변경을 게시해 실제 backpressure를 만든다.
        AtomicReference<Thread> blockedWriter = new AtomicReference<>();
        long changingDeviceId = createdDevices.getFirst().getId();
        try (var producer = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            long producerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            var producing = producer.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted() && System.nanoTime() < producerDeadline) {
                        jdbc.sql("UPDATE press_daily_scores SET total = total + 1 WHERE device_id = :device")
                                .param("device", changingDeviceId).update();
                        publisher.refresh();
                        Thread candidate = blockedSocketWriter();
                        if (candidate != null) {
                            blockedWriter.set(candidate);
                        }
                        TimeUnit.MILLISECONDS.sleep(20);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                await(() -> blockedWriter.get() != null, Duration.ofSeconds(8));
                WebSocketSession transport = slowSession.get();
                assertThat(transport).isInstanceOf(StandardWebSocketSession.class);
                assertThat(transport.isOpen()).isTrue();
                long blockedAt = System.nanoTime();

                // 정상 기기는 느린 기기의 송신/종료 잠금을 기다리지 않는다.
                Device healthy = createDevice();
                BlockingQueue<JsonNode> healthyMessages = new LinkedBlockingQueue<>();
                WebSocket healthySocket = connect(tokens.issue(healthy.getPublicId()).accessToken(), healthyMessages);
                healthySocket.sendText("{\"v\":1,\"type\":\"session.init\",\"view\":\"PRESS\",\"pending\":[]}", true).join();
                JsonNode ready = healthyMessages.poll(2, TimeUnit.SECONDS);
                assertThat(ready).isNotNull();
                assertThat(ready.path("type").stringValue()).isEqualTo("session.ready");

                // then: 설정 존재가 아니라 실제 소켓 종료와 블로킹 writer 해제를 확인한다.
                CloseStatus closed = slowClosed.poll(5, TimeUnit.SECONDS);
                assertThat(closed).as("nativeOpen=%s, writerState=%s, writerStack=%s", transport.isOpen(),
                        blockedWriter.get().getState(), java.util.Arrays.toString(blockedWriter.get().getStackTrace())).isNotNull();
                assertThat(closed.getCode()).isNotEqualTo(1000);
                await(() -> !transport.isOpen() && !isSocketWrite(blockedWriter.get().getStackTrace()), Duration.ofSeconds(3));
                assertThat(Duration.ofNanos(System.nanoTime() - blockedAt)).isLessThan(Duration.ofSeconds(6));
            } finally {
                producing.cancel(true);
            }
        }

        // 같은 기기의 새 연결도 복구 정산을 마치고 새 입력을 받을 수 있다.
        BlockingQueue<JsonNode> recoveredMessages = new LinkedBlockingQueue<>();
        WebSocket recovered = connect(tokens.issue(slow.getPublicId()).accessToken(), recoveredMessages);
        recovered.sendText("{\"v\":1,\"type\":\"session.init\",\"view\":\"PRESS\",\"pending\":[]}", true).join();
        JsonNode recoveredReady = recoveredMessages.poll(2, TimeUnit.SECONDS);
        assertThat(recoveredReady).isNotNull();
        assertThat(recoveredReady.path("type").stringValue()).isEqualTo("session.ready");
        recovered.sendText("{\"v\":1,\"type\":\"press.submit\",\"requestId\":\"" + UUID.randomUUID()
                + "\",\"connectionGeneration\":\"" + recoveredReady.path("connectionGeneration").stringValue()
                + "\",\"counts\":{\"ANGRY\":1}}", true).join();
        JsonNode result;
        do {
            result = recoveredMessages.poll(2, TimeUnit.SECONDS);
            assertThat(result).isNotNull();
        } while (!result.path("type").stringValue().equals("input.result"));
        assertThat(result.path("status").stringValue()).isEqualTo("APPLIED");
    }

    @Test
    void 실제_결과_큐_상한_초과는_연결을_닫고_재연결에서_확정_결과를_중복_가산_없이_복구한다() throws Exception {
        // given: 이미 커밋됐지만 응답을 확인하지 못한 입력이 있는 인증 기기를 준비한다.
        Device owner = createDevice();
        PressConnection oldConnection = PressConnection.of(owner.getPublicId(), service.serverGeneration(),
                UUID.randomUUID(), Instant.now().plusSeconds(600));
        service.connect(oldConnection);
        service.initialize(oldConnection, List.of());
        UUID appliedId = UUID.randomUUID();
        assertThat(service.submit(oldConnection, appliedId, Map.of(EmotionState.ANGRY, 1)).status()).isEqualTo("APPLIED");
        for (int index = 0; index < 100; index++) {
            Device participant = createDevice();
            jdbc.sql("""
                    INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id)
                    VALUES (:device, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, :total,
                            clock_timestamp(), :publicId)
                    """).param("device", participant.getId()).param("total", 1000 + index)
                    .param("publicId", UUID.randomUUID()).update();
        }
        rawSocket = openNonReadingSocket(tokens.issue(owner.getPublicId()).accessToken());
        sendMaskedText(rawSocket, "{\"v\":1,\"type\":\"session.init\",\"view\":\"ALL\",\"pending\":[]}");
        AtomicReference<Thread> blockedWriter = new AtomicReference<>();
        long changingDeviceId = createdDevices.get(1).getId();

        // when: 실제 TCP 송신이 밀린 동안 두 개의 정상 결과 조회로 32개 결과 FIFO를 넘긴다.
        try (var producer = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            var producing = producer.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted() && System.nanoTime() < deadline) {
                        jdbc.sql("UPDATE press_daily_scores SET total = total + 1 WHERE device_id = :device")
                                .param("device", changingDeviceId).update();
                        publisher.refresh();
                        Thread candidate = blockedSocketWriter();
                        if (candidate != null) {
                            blockedWriter.set(candidate);
                        }
                        TimeUnit.MILLISECONDS.sleep(20);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                await(() -> blockedWriter.get() != null, Duration.ofSeconds(8));
                List<UUID> firstIds = new ArrayList<>();
                firstIds.add(appliedId);
                for (int index = 1; index < 20; index++) {
                    firstIds.add(UUID.randomUUID());
                }
                List<UUID> secondIds = java.util.stream.IntStream.range(0, 20).mapToObj(index -> UUID.randomUUID()).toList();
                sendMaskedText(rawSocket, status(firstIds));
                sendMaskedText(rawSocket, status(secondIds));

                // then: 단순 송신 시간 초과와 구분해 실제 큐 초과 종료를 확인한다.
                CloseStatus closed = slowClosed.poll(5, TimeUnit.SECONDS);
                assertThat(closed).isNotNull();
                assertThat(closed.getCode()).isEqualTo(1008);
                assertThat(closed.getReason()).isEqualTo("RESULT_QUEUE_OVERFLOW");
                await(() -> !slowSession.get().isOpen() && !isSocketWrite(blockedWriter.get().getStackTrace()),
                        Duration.ofSeconds(3));
            } finally {
                producing.cancel(true);
            }
        }

        // 같은 입력 ID의 최종 결과는 큐에서 유실돼도 PostgreSQL에 남아 있다.
        BlockingQueue<JsonNode> recoveredMessages = new LinkedBlockingQueue<>();
        WebSocket recovered = connect(tokens.issue(owner.getPublicId()).accessToken(), recoveredMessages);
        recovered.sendText("{\"v\":1,\"type\":\"session.init\",\"view\":\"PRESS\",\"pending\":[]}", true).join();
        JsonNode ready = receive(recoveredMessages, "session.ready");
        assertThat(ready.path("counts").path("ANGRY").longValue()).isEqualTo(1);
        recovered.sendText(status(List.of(appliedId)), true).join();
        JsonNode originalResult = receive(recoveredMessages, "input.result");
        assertThat(originalResult.path("requestId").stringValue()).isEqualTo(appliedId.toString());
        assertThat(originalResult.path("status").stringValue()).isEqualTo("APPLIED");
        assertThat(originalResult.path("appliedCounts").path("ANGRY").intValue()).isEqualTo(1);
        recovered.sendText("{\"v\":1,\"type\":\"press.submit\",\"requestId\":\"" + appliedId
                + "\",\"connectionGeneration\":\"" + ready.path("connectionGeneration").stringValue()
                + "\",\"counts\":{\"ANGRY\":1}}", true).join();
        assertThat(receive(recoveredMessages, "input.result")).isEqualTo(originalResult);
        assertThat(jdbc.sql("SELECT SUM(press_count) FROM device_daily_presses WHERE device_id = :device")
                .param("device", owner.getId()).query(Long.class).single()).isEqualTo(1);
    }

    private String status(List<UUID> ids) {
        return "{\"v\":1,\"type\":\"press.status\",\"requestIds\":" + json.writeValueAsString(ids) + "}";
    }

    private JsonNode receive(BlockingQueue<JsonNode> messages, String type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode result = messages.poll(100, TimeUnit.MILLISECONDS);
            if (result != null && result.path("type").stringValue().equals(type)) {
                return result;
            }
        }
        throw new AssertionError("수신하지 못한 WS 메시지: " + type);
    }

    @Test
    void 실제_Ping을_받아도_Pong을_보내지_않으면_마지막_수신_45초에_종료한다() throws Exception {
        // given: 초기화 전 실제 인증 소켓도 연결 확인 대상이다. 읽기만 하고 Pong은 보내지 않는다.
        Device device = createDevice();
        rawSocket = openNonReadingSocket(tokens.issue(device.getPublicId()).accessToken());
        rawSocket.setSoTimeout(20_000);
        long connectedAt = System.nanoTime();

        // when: 서버 송신은 정상 수신 시각을 연장하지 않는다.
        RawControlFrame firstPing = readControlFrame(rawSocket);
        assertThat(firstPing.opcode()).isEqualTo(9);
        assertThat(Duration.ofNanos(System.nanoTime() - connectedAt)).isBetween(Duration.ofSeconds(14), Duration.ofSeconds(20));
        RawControlFrame secondPing = readControlFrame(rawSocket);
        assertThat(secondPing.opcode()).isEqualTo(9);
        assertThat(Duration.ofNanos(System.nanoTime() - connectedAt)).isBetween(Duration.ofSeconds(28), Duration.ofSeconds(36));
        RawControlFrame closed = readControlFrame(rawSocket);

        // then: 송신 설정이 아닌 실제 제어 프레임과 종료 사유를 확인한다.
        assertThat(closed.opcode()).isEqualTo(8);
        assertThat(closed.payload().length).isGreaterThanOrEqualTo(2);
        int code = ((closed.payload()[0] & 0xff) << 8) | (closed.payload()[1] & 0xff);
        assertThat(code).isEqualTo(1008);
        assertThat(new String(closed.payload(), 2, closed.payload().length - 2, StandardCharsets.UTF_8))
                .isEqualTo("HEARTBEAT_TIMEOUT");
        assertThat(Duration.ofNanos(System.nanoTime() - connectedAt)).isBetween(Duration.ofSeconds(44), Duration.ofSeconds(50));
    }

    private RawControlFrame readControlFrame(Socket socket) throws Exception {
        int first = socket.getInputStream().read();
        int second = socket.getInputStream().read();
        assertThat(first).isNotEqualTo(-1);
        assertThat(second).isBetween(0, 125);
        assertThat(first & 0x80).isEqualTo(0x80);
        byte[] payload = socket.getInputStream().readNBytes(second);
        assertThat(payload.length).isEqualTo(second);
        return RawControlFrame.of(first & 0x0f, payload);
    }

    private record RawControlFrame(int opcode, byte[] payload) {
        private static RawControlFrame of(int opcode, byte[] payload) {
            return new RawControlFrame(opcode, payload);
        }
    }

    private Device createDevice() {
        Device device = devices.save(기본_기기_빌더().build());
        createdDevices.add(device);
        return device;
    }

    private Socket openNonReadingSocket(String token) throws Exception {
        Socket socket = new Socket();
        socket.setReceiveBufferSize(1024);
        socket.setSoTimeout(5000);
        socket.connect(new InetSocketAddress("127.0.0.1", port));
        String key = Base64.getEncoder().encodeToString(new byte[16]);
        String upgrade = "GET /api/v1/press-race HTTP/1.1\r\nHost: 127.0.0.1:" + port
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
                + "\r\nSec-WebSocket-Version: 13\r\nAuthorization: Bearer " + token + "\r\n\r\n";
        socket.getOutputStream().write(upgrade.getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        while (!response.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int value = socket.getInputStream().read();
            assertThat(value).isNotEqualTo(-1);
            response.write(value);
            assertThat(response.size()).isLessThanOrEqualTo(4096);
        }
        assertThat(response.toString(StandardCharsets.US_ASCII)).startsWith("HTTP/1.1 101");
        // 이후 수신을 요청하지 않아 애플리케이션 Listener의 flow control과 TCP 읽기 중단을 구분한다.
        return socket;
    }

    private void sendMaskedText(Socket socket, String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        assertThat(bytes.length).isLessThan(65_536);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81);
        if (bytes.length < 126) {
            frame.write(0x80 | bytes.length);
        } else {
            frame.write(0x80 | 126);
            frame.write(bytes.length >>> 8);
            frame.write(bytes.length & 0xff);
        }
        byte[] mask = {11, 22, 33, 44};
        frame.write(mask);
        for (int index = 0; index < bytes.length; index++) {
            frame.write(bytes[index] ^ mask[index % 4]);
        }
        socket.getOutputStream().write(frame.toByteArray());
        socket.getOutputStream().flush();
    }

    private Thread blockedSocketWriter() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(entry -> entry.getKey().getName().startsWith("press-snapshot-"))
                .filter(entry -> entry.getKey().getState() == Thread.State.WAITING
                        || entry.getKey().getState() == Thread.State.TIMED_WAITING)
                .filter(entry -> isSocketWrite(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private boolean isSocketWrite(StackTraceElement[] stack) {
        return java.util.Arrays.stream(stack).anyMatch(frame -> frame.getClassName()
                .startsWith("org.apache.tomcat.websocket.WsRemoteEndpointImpl"))
                && java.util.Arrays.stream(stack).anyMatch(frame -> frame.getClassName()
                .startsWith("org.apache.tomcat.util.net."));
    }

    private WebSocket connect(String token, BlockingQueue<JsonNode> messages) throws Exception {
        WebSocket socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/api/v1/press-race"), new WebSocket.Listener() {
                    private final StringBuilder partial = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        partial.append(data);
                        if (last) {
                            messages.add(json.readTree(partial.toString()));
                            partial.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
        sockets.add(socket);
        return socket;
    }

    private void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean satisfied = condition.getAsBoolean();
        while (!satisfied && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
            satisfied = condition.getAsBoolean();
        }
        assertThat(satisfied).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SmallSocketBuffers {

        @Bean
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> smallWriteBuffer() {
            return factory -> factory.addConnectorCustomizers(connector ->
                    connector.setProperty("socket.txBufSize", "1024"));
        }
    }
}
