package com.pheeeew.pressrace.presentation;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PressRaceWebSocketHandlerTest {

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
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            if (message.getPayload().contains("state.snapshot")) {
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
        assertThat(writerMessages).anySatisfy(message -> assertThat(message).contains("input.result", "APPLIED"));
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
