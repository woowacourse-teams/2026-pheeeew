package com.pheeeew.pressrace.presentation;

import static com.pheeeew.pressrace.fixture.PressRankingFixture.OBSERVER_ID;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.순위;
import static com.pheeeew.pressrace.fixture.PressRankingFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.application.PressRankingPublisher;
import com.pheeeew.pressrace.application.PressRankingReader;
import com.pheeeew.pressrace.domain.PressInputResult;
import com.pheeeew.pressrace.domain.PressRanking;
import com.pheeeew.pressrace.domain.PressSessionResult;
import java.time.Instant;
import java.time.LocalDate;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PressRaceWebSocketHandlerTest {

    @Test
    void 입력_송신_잠금을_기다린_중간_추월은_JSON으로_내보내지_않는다() throws Exception {
        // given: DB·인증은 실제 WS 통합 테스트로 검증하고 여기서는 소켓 송신 경합을 제어한다.
        try (var protocol = new Protocol()) {
            var socket = protocol.open("observer");
            protocol.publish(10, 11);
            assertOvertakes(socket.snapshot(), 0, 0);

            // when
            synchronized (socket.session) {
                protocol.publish(12, 11);
                protocol.publish(12, 13);
            }
            JsonNode latest = socket.snapshot();

            // then
            assertThat(latest.path("snapshotVersion").stringValue()).isEqualTo("3");
            assertThat(latest.path("me").path("score").longValue()).isEqualTo(12);
            assertOvertakes(latest, 0, 0);
            assertThat(socket.snapshots.poll(200, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    @Test
    void 실제_송신이_진행중이면_성공한_JSON_이후_상태를_새_비교_기준으로_쓴다() throws Exception {
        // given
        try (var protocol = new Protocol()) {
            var socket = protocol.open("observer");
            protocol.publish(10, 11);
            socket.snapshot();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            socket.beforeSend.set(message -> {
                if (message.path("snapshotVersion").stringValue().equals("2")) {
                    entered.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }
            });

            // when
            try {
                protocol.publish(12, 11);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                protocol.publish(12, 13);
            } finally {
                release.countDown();
            }

            // then
            JsonNode passed = socket.snapshot();
            JsonNode passedBy = socket.snapshot();
            assertThat(passed.path("snapshotVersion").stringValue()).isEqualTo("2");
            assertOvertakes(passed, 1, 0);
            assertThat(passedBy.path("snapshotVersion").stringValue()).isEqualTo("3");
            assertOvertakes(passedBy, 0, 1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void JSON_송신_실패나_미송신은_연결을_닫고_새_연결에서는_무알림_기준으로_시작한다(boolean writeFailure) throws Exception {
        // given
        try (var protocol = new Protocol()) {
            var previous = protocol.open("previous");
            protocol.publish(10, 11);
            previous.snapshot();
            if (writeFailure) {
                previous.fail.set(true);
            } else {
                previous.open.set(false);
            }

            // when
            protocol.publish(12, 11);
            CloseStatus closed = previous.closed.poll(5, TimeUnit.SECONDS);

            // then
            assertThat(closed).isNotNull();
            assertThat(closed.getCode()).isEqualTo(1008);
            assertThat(closed.getReason()).isEqualTo("SNAPSHOT_SEND_FAILED");
            assertThat(previous.snapshots).isEmpty();

            // when: 전송에 실패한 알림은 새 연결에 재생하지 않는다.
            protocol.handler.afterConnectionClosed(previous.session, closed);
            var current = protocol.open("current");
            protocol.publish(12, 13);
            JsonNode baseline = current.snapshot();

            // then
            assertThat(baseline.path("baseline").booleanValue()).isTrue();
            assertOvertakes(baseline, 0, 0);
            protocol.publish(14, 13);
            assertOvertakes(current.snapshot(), 1, 0);
        }
    }

    @Test
    void 같은_stripe의_느린_관찰자_송신이_다른_기기의_ACK를_막지_않는다() throws Exception {
        // given: 실제 인증/DB 저장은 통합 테스트로, 여기서는 블로킹 송신의 격리만 검증한다.
        UUID observerId = new UUID(0, 1);
        UUID writerId = new UUID(0, 129);
        assertThat(Math.floorMod(observerId.hashCode(), 128)).isEqualTo(Math.floorMod(writerId.hashCode(), 128));
        PressRaceService service = mock(PressRaceService.class);
        PressRankingReader reader = mock(PressRankingReader.class);
        when(service.serverGeneration()).thenReturn(UUID.randomUUID());
        when(service.initialize(any(), any())).thenReturn(PressSessionResult.of(Map.of(), List.of(), LocalDate.of(2026, 10, 26)));
        when(reader.read()).thenReturn(PressRanking.of(LocalDate.of(2026, 10, 26), Instant.now(), List.of()));
        PressRankingPublisher publisher = new PressRankingPublisher(reader);
        PressRaceWebSocketHandler handler = new PressRaceWebSocketHandler(service, publisher);
        WebSocketSession observer = session("observer", observerId);
        WebSocketSession writer = session("writer", writerId);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> writerMessages = new CopyOnWriteArrayList<>();
        var virtualSender = new java.util.concurrent.atomic.AtomicReference<Boolean>();
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            if (message.getPayload().contains("state.snapshot")) {
                virtualSender.set(Thread.currentThread().isVirtual());
                blocked.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(observer).sendMessage(any());
        doAnswer(invocation -> {
            writerMessages.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(writer).sendMessage(any());
        UUID requestId = UUID.randomUUID();

        // when
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                handler.afterConnectionEstablished(observer);
                handler.afterConnectionEstablished(writer);
                handler.handleTextMessage(observer, new TextMessage("{\"v\":1,\"type\":\"session.init\",\"view\":\"PRESS\",\"pending\":[]}"));
                publisher.refresh();
                assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
                when(service.status(any(), any())).thenReturn(List.of(PressInputResult.of(requestId, "APPLIED", null,
                        LocalDate.of(2026, 10, 26), Map.of(EmotionState.ANGRY, 1))));
                var ack = executor.submit(() -> {
                    handler.handleTextMessage(writer, new TextMessage("{\"v\":1,\"type\":\"press.status\",\"requestIds\":[\"" + requestId + "\"]}"));
                    return null;
                });
                // 입력 서비스의 기존 반환 경계 뒤 결과 송신이 다른 기기 I/O와 독립인지 확인한다.
                ack.get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                handler.stopSnapshotExecutor();

            }
        }

        // then
        assertThat(virtualSender.get()).as("Java 21에서 블로킹 송신은 플랫폼 스레드 사용").isFalse();
        assertThat(writerMessages).anySatisfy(message -> assertThat(message).contains("input.result", "APPLIED"));
    }

    private void assertOvertakes(JsonNode snapshot, int passed, int passedBy) {
        assertThat(snapshot.path("overtakes").path("passed").intValue(-1)).isEqualTo(passed);
        assertThat(snapshot.path("overtakes").path("passedBy").intValue(-1)).isEqualTo(passedBy);
    }

    /** 저장 서비스는 Controller 경계에서 대체하고 실제 handler·publisher·sender의 JSON을 관찰한다. */
    private final class Protocol implements AutoCloseable {

        private final PressRaceService service = mock(PressRaceService.class);
        private final PressRankingReader reader = mock(PressRankingReader.class);
        private final PressRankingPublisher publisher = new PressRankingPublisher(reader);
        private final PressRaceWebSocketHandler handler = new PressRaceWebSocketHandler(service, publisher);

        private Protocol() {
            when(service.serverGeneration()).thenReturn(UUID.randomUUID());
            when(service.initialize(any(), any())).thenReturn(PressSessionResult.of(Map.of(), List.of(), LocalDate.of(2026, 10, 26)));
        }

        @Override
        public void close() {
            handler.stopSnapshotExecutor();
        }

        private ProtocolSocket open(String id) throws Exception {
            var socket = new ProtocolSocket(id);
            handler.afterConnectionEstablished(socket.session);
            handler.handleTextMessage(socket.session, new TextMessage("{\"v\":1,\"type\":\"session.init\",\"view\":\"PRESS\",\"pending\":[]}"));
            return socket;
        }

        private void publish(long me, long opponent) {
            when(reader.read()).thenReturn(순위(List.of(참가자(0, me), 참가자(1, opponent))));
            publisher.refresh();
        }
    }

    private final class ProtocolSocket {

        private final WebSocketSession session;
        private final BlockingQueue<JsonNode> snapshots = new LinkedBlockingQueue<>();
        private final BlockingQueue<CloseStatus> closed = new LinkedBlockingQueue<>();
        private final AtomicBoolean fail = new AtomicBoolean();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicReference<Consumer<JsonNode>> beforeSend = new AtomicReference<>(message -> {});

        private ProtocolSocket(String id) throws IOException {
            session = session(id, OBSERVER_ID);
            when(session.isOpen()).thenAnswer(invocation -> open.get());
            JsonMapper json = JsonMapper.builder().build();
            doAnswer(invocation -> {
                JsonNode message = json.readTree(((TextMessage) invocation.getArgument(0)).getPayload());
                if (message.path("type").stringValue().equals("state.snapshot")) {
                    beforeSend.get().accept(message);
                    if (fail.get()) {
                        throw new IOException("제어된 소켓 송신 실패");
                    }
                    snapshots.add(message);
                }
                return null;
            }).when(session).sendMessage(any());
            doAnswer(invocation -> {
                open.set(false);
                closed.add(invocation.getArgument(0));
                return null;
            }).when(session).close(any());
        }

        private JsonNode snapshot() throws InterruptedException {
            JsonNode message = snapshots.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("서버 WS 송신 JSON").isNotNull();
            return message;
        }
    }

    private WebSocketSession session(String id, UUID deviceId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(deviceId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        when(session.getPrincipal()).thenReturn(new JwtAuthenticationToken(jwt));
        return session;
    }
}
