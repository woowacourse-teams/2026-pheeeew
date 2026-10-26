package com.pheeeew.pressrace.presentation;

import com.pheeeew.common.logging.ScheduledTaskErrorHandler;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.application.PressRankingPublisher;
import com.pheeeew.pressrace.domain.PendingPress;
import com.pheeeew.pressrace.domain.PressConnection;
import com.pheeeew.pressrace.domain.PressInputResult;
import com.pheeeew.pressrace.domain.PressSessionResult;
import com.pheeeew.pressrace.domain.PressView;
import com.pheeeew.pressrace.exception.PressRaceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PressRaceWebSocketHandler extends AbstractWebSocketHandler {

    private static final int MAX_MESSAGE_BYTES = 16 * 1024;
    private static final int MAX_DEVICES = 100_000;
    private final PressRaceService service;
    private final PressRankingPublisher rankingPublisher;
    private final Map<String, PressSnapshotSender> snapshotSenders = new ConcurrentHashMap<>();
    private final ExecutorService snapshotExecutor = Executors.newCachedThreadPool(
            Thread.ofPlatform().name("press-snapshot-", 0).daemon().factory());
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("press-connection-watchdog").daemon().factory());
    private final Set<String> closing = ConcurrentHashMap.newKeySet();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final Map<String, PressConnection> connections = new ConcurrentHashMap<>();
    private final Map<UUID, WebSocketSession> currentSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MessageBucket> messageBuckets = new HashMap<>();
    private final Object[] connectionLocks = new Object[128];

    public PressRaceWebSocketHandler(PressRaceService service, PressRankingPublisher rankingPublisher) {
        this.service = service;
        this.rankingPublisher = rankingPublisher;
        for (int index = 0; index < connectionLocks.length; index++) {
            connectionLocks[index] = new Object();
        }
    }

    @PostConstruct
    void startMaintenance() {
        maintenance.scheduleWithFixedDelay(new DelegatingErrorHandlingRunnable(
                () -> maintain(Instant.now(), System.nanoTime()), new ScheduledTaskErrorHandler()),
                100, 100, TimeUnit.MILLISECONDS);
    }

    void maintain(Instant now, long monotonicNow) {
        sessions.forEach((id, session) -> {
            PressConnection connection = connections.get(id);
            PressSnapshotSender sender = snapshotSenders.get(id);
            if (connection == null || sender == null) {
                return;
            }
            if (connection.expiresAt() == null || !now.isBefore(connection.expiresAt())) {
                terminate(session, CloseStatus.POLICY_VIOLATION.withReason("AUTH_EXPIRED"));
            } else {
                sender.maintain(monotonicNow, currentSessions.get(connection.devicePublicId()) == session,
                        () -> write(session, connection, new PingMessage(ByteBuffer.allocate(0))));
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!(session.getPrincipal() instanceof JwtAuthenticationToken authentication)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        UUID deviceId = UUID.fromString(authentication.getToken().getSubject());
        PressConnection connection = PressConnection.of(deviceId, service.serverGeneration(), UUID.randomUUID(),
                authentication.getToken().getExpiresAt());
        session.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
        if (session instanceof StandardWebSocketSession standard) {
            jakarta.websocket.Session nativeSession = standard.getNativeSession();
            // Basic 송신 제한과 메시지 전체 watchdog은 서로 보완한다. close도 감시 스레드에서 실행하지 않는다.
            nativeSession.getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 2000L);
            nativeSession.getUserProperties().put("org.apache.tomcat.websocket.SESSION_CLOSE_TIMEOUT", 2000L);
            nativeSession.getUserProperties().put("org.apache.tomcat.websocket.ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT", 50L);
        }
        PressSnapshotSender sender = new PressSnapshotSender(session, snapshotExecutor,
                snapshot -> sendSnapshot(session, connection, snapshot),
                failure -> terminate(session, CloseStatus.POLICY_VIOLATION.withReason(failureCode(failure))));
        // 거절 연결도 결과/제어 송신과 같은 제한을 사용한다. 현재 기기 연결은 DB 승인 뒤에만 교체한다.
        connections.put(session.getId(), connection);
        snapshotSenders.put(session.getId(), sender);
        sessions.put(session.getId(), session);
        synchronized (connectionLocks[Math.floorMod(deviceId.hashCode(), connectionLocks.length)]) {
            try {
                requireUnexpired(connection);
                service.connect(connection);
                WebSocketSession previous = currentSessions.put(deviceId, session);
                if (previous != null && previous != session) {
                    terminate(previous, CloseStatus.POLICY_VIOLATION.withReason("CONNECTION_REPLACED"));
                }
                // DB 교체가 커밋되는 동안 새 소켓이 종료됐어도 구 소켓 자격을 되살리지 않는다.
                if (!sessions.containsKey(session.getId())) {
                    currentSessions.remove(deviceId, session);
                }
            } catch (PressRaceException exception) {
                rejectConnection(session, connection, exception);
            } catch (InvalidMessage exception) {
                terminate(session, CloseStatus.POLICY_VIOLATION.withReason(exception.code));
            }
        }
    }

    private String failureCode(RuntimeException failure) {
        if (failure instanceof InvalidMessage invalid) {
            return invalid.code;
        }
        if (Set.of("RESULT_QUEUE_OVERFLOW", "SEND_TIMEOUT", "HEARTBEAT_TIMEOUT").contains(
                failure.getMessage() == null ? "" : failure.getMessage())) {
            return failure.getMessage();
        }
        return "SNAPSHOT_SEND_FAILED";
    }

    private void rejectConnection(WebSocketSession session, PressConnection connection, PressRaceException exception) {
        if (connection.expiresAt() == null || !Instant.now().isBefore(connection.expiresAt())) {
            terminate(session, CloseStatus.POLICY_VIOLATION.withReason("AUTH_EXPIRED"));
            return;
        }
        Map<String, Object> response = envelope(connection, "protocol.error");
        response.put("code", exception.code());
        TextMessage message = new TextMessage(json.writeValueAsString(response));
        closing.add(session.getId());
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (sender != null) {
            sender.offerResult(() -> {
                try {
                    requireUnexpired(connection);
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                } finally {
                    terminate(session, CloseStatus.POLICY_VIOLATION.withReason(exception.code()));
                }
            }, message.getPayload().getBytes(StandardCharsets.UTF_8).length);
        }
        if (!sessions.containsKey(session.getId())) {
            closing.remove(session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        PressConnection connection = connections.get(session.getId());
        if (connection == null || closing.contains(session.getId())) {
            return;
        }
        try {
            requireUnexpired(connection);
            if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                terminate(session, CloseStatus.TOO_BIG_TO_PROCESS);
                return;
            }
            if (!allowMessage(connection.devicePublicId())) {
                sendErrorAndClose(session, connection, "MESSAGE_RATE_LIMITED", CloseStatus.POLICY_VIOLATION);
                return;
            }
            JsonNode root;
            try {
                root = json.readTree(message.getPayload());
            } catch (RuntimeException exception) {
                sendErrorAndClose(session, connection, "INVALID_JSON", CloseStatus.BAD_DATA);
                return;
            }
            if (root == null || !root.isObject()) {
                throw new InvalidMessage("INVALID_INPUT");
            }
            if (!root.path("v").isIntegralNumber() || !root.path("v").canConvertToInt() || root.path("v").intValue() != 1) {
                throw new InvalidMessage("UNSUPPORTED_VERSION");
            }
            switch (text(root, "type")) {
                case "session.init" -> initialize(session, connection, root);
                case "press.submit" -> submit(session, connection, root);
                case "press.status" -> status(session, connection, root);
                case "view.change" -> changeView(session, root);
                default -> throw new InvalidMessage("UNSUPPORTED_TYPE");
            }
        } catch (InvalidMessage exception) {
            if ("AUTH_EXPIRED".equals(exception.code)) {
                terminate(session, CloseStatus.POLICY_VIOLATION.withReason(exception.code));
            } else {
                sendError(session, connection, exception.code);
            }
        } catch (PressRaceException exception) {
            if (Set.of("AUTH_EXPIRED", "SERVER_REPLACED", "CONNECTION_REPLACED").contains(exception.code())) {
                terminate(session, CloseStatus.POLICY_VIOLATION.withReason(exception.code()));
            } else {
                sendError(session, connection, exception.code(), exception.requestId());
            }
        } catch (RuntimeException exception) {
            sendError(session, connection, "TEMPORARILY_UNAVAILABLE");
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        PressConnection connection = connections.get(session.getId());
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (connection != null && sender != null) {
            if (connection.expiresAt() == null || !Instant.now().isBefore(connection.expiresAt())) {
                terminate(session, CloseStatus.POLICY_VIOLATION.withReason("AUTH_EXPIRED"));
            } else {
                sender.received(System.nanoTime());
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        terminate(session, CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        terminate(session, CloseStatus.SERVER_ERROR.withReason("TRANSPORT_ERROR"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        release(session);
    }

    private boolean release(WebSocketSession session) {
        if (!sessions.remove(session.getId(), session)) {
            return false;
        }
        closing.remove(session.getId());
        PressSnapshotSender sender = snapshotSenders.remove(session.getId());
        if (sender != null) {
            sender.close();
        }
        rankingPublisher.unsubscribe(session.getId());
        PressConnection connection = connections.remove(session.getId());
        if (connection != null) {
            currentSessions.remove(connection.devicePublicId(), session);
        }
        return true;
    }

    private void terminate(WebSocketSession session, CloseStatus status) {
        // 송신 잠금과 무관하게 자원 참조를 해제한다. close 프레임도 블로킹될 수 있다.
        if (release(session)) {
            snapshotExecutor.execute(() -> {
                try {
                    session.close(status);
                } catch (IOException ignored) {
                    // 전송 실패 연결은 이미 등록 해제했다.
                }
            });
        }
    }

    private void initialize(WebSocketSession session, PressConnection connection, JsonNode root) throws IOException {
        fields(root, Set.of("v", "type", "view", "pending"));
        String view = text(root, "view");
        if (!Set.of("PRESS", "ALL").contains(view)) {
            throw new InvalidMessage("INVALID_INPUT");
        }
        JsonNode pending = array(root, "pending");
        List<PendingPress> inputs = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (JsonNode input : pending) {
            fields(input, Set.of("requestId", "connectionGeneration", "counts"));
            UUID requestId = uuid(input, "requestId");
            if (!ids.add(requestId)) {
                throw new InvalidMessage("INVALID_INPUT");
            }
            inputs.add(PendingPress.of(requestId, uuid(input, "connectionGeneration"), counts(input)));
        }
        received(session);
        PressSessionResult result = service.initialize(connection, inputs);
        Map<String, Object> response = envelope(connection, "session.ready");
        response.put("counts", result.counts());
        response.put("results", result.results().stream().map(this::resultPayload).toList());
        response.put("date", result.date().toString());
        rankingPublisher.unsubscribe(session.getId());
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (sender == null) {
            return;
        }
        TextMessage ready = new TextMessage(json.writeValueAsString(response));
        sender.offerReady(() -> write(session, connection, ready), ready.getPayload().getBytes(StandardCharsets.UTF_8).length);
        // 종료와 등록이 경합해도 종료된 소켓의 구독을 남기지 않는다.
        if (!sessions.containsKey(session.getId())) {
            return;
        }
        rankingPublisher.subscribe(session.getId(), connection.devicePublicId(), PressView.valueOf(view),
                sender::offer, code -> snapshotExecutor.execute(() -> stopSnapshots(session, code)));
        if (!sessions.containsKey(session.getId())) {
            rankingPublisher.unsubscribe(session.getId());
        }
    }

    private void changeView(WebSocketSession session, JsonNode root) {
        fields(root, Set.of("v", "type", "view"));
        PressView view;
        try {
            view = PressView.valueOf(text(root, "view"));
        } catch (IllegalArgumentException exception) {
            throw new InvalidMessage("INVALID_INPUT");
        }
        received(session);
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (sender == null || !rankingPublisher.changeView(session.getId(), view, sender::reset)) {
            throw new InvalidMessage("SESSION_NOT_READY");
        }
    }

    private void sendSnapshot(WebSocketSession session, PressConnection connection, PressRankingPublisher.Snapshot snapshot) {
        Map<String, Object> response = envelope(connection, "state.snapshot");
        response.put("date", snapshot.screen().date().toString());
        response.put("snapshotVersion", snapshot.snapshotVersion());
        response.put("generatedAt", snapshot.generatedAt().toString());
        response.put("activeCount", snapshot.screen().activeCount());
        response.put("view", snapshot.screen().view());
        response.put("me", snapshot.screen().me());
        response.put("nearby", snapshot.screen().nearby());
        response.put("top100", snapshot.screen().top100());
        response.put("baseline", snapshot.baseline());
        response.put("overtakes", snapshot.overtakes());
        write(session, connection, new TextMessage(json.writeValueAsString(response)));
    }

    private void stopSnapshots(WebSocketSession session, String code) {
        terminate(session, CloseStatus.POLICY_VIOLATION.withReason(code));
    }

    @PreDestroy
    void stopSnapshotExecutor() {
        maintenance.shutdownNow();
        sessions.values().forEach(session -> terminate(session, CloseStatus.GOING_AWAY));
        snapshotExecutor.shutdown();
    }

    private void submit(WebSocketSession session, PressConnection connection, JsonNode root) throws IOException {
        fields(root, Set.of("v", "type", "requestId", "connectionGeneration", "counts"));
        if (!uuid(root, "connectionGeneration").equals(connection.connectionGeneration())) {
            throw new InvalidMessage("CONNECTION_REPLACED");
        }
        UUID requestId = uuid(root, "requestId");
        Map<EmotionState, Integer> inputCounts = counts(root);
        received(session);
        sendResult(session, connection, service.submit(connection, requestId, inputCounts));
    }

    private void status(WebSocketSession session, PressConnection connection, JsonNode root) throws IOException {
        fields(root, Set.of("v", "type", "requestIds"));
        List<UUID> requestIds = new ArrayList<>();
        for (JsonNode id : array(root, "requestIds")) {
            if (!id.isString()) {
                throw new InvalidMessage("INVALID_INPUT");
            }
            requestIds.add(parseUuid(id.stringValue()));
        }
        if (new HashSet<>(requestIds).size() != requestIds.size()) {
            throw new InvalidMessage("INVALID_INPUT");
        }
        received(session);
        for (PressInputResult result : service.status(connection, requestIds)) {
            sendResult(session, connection, result);
        }
    }

    private void sendResult(WebSocketSession session, PressConnection connection, PressInputResult result) throws IOException {
        Map<String, Object> response = envelope(connection, "input.result");
        response.putAll(resultPayload(result));
        send(session, connection, response);
    }

    private Map<String, Object> resultPayload(PressInputResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", result.requestId().toString());
        response.put("status", result.status());
        if (result.code() != null) {
            response.put("code", result.code());
        }
        if (result.appliedDate() != null) {
            response.put("appliedDate", result.appliedDate().toString());
            response.put("appliedCounts", result.appliedCounts());
        }
        return response;
    }

    private void sendError(WebSocketSession session, PressConnection connection, String code) throws IOException {
        sendError(session, connection, code, null);
    }

    private void sendError(WebSocketSession session, PressConnection connection, String code, UUID requestId) throws IOException {
        if (connection.expiresAt() == null || !Instant.now().isBefore(connection.expiresAt())) {
            terminate(session, CloseStatus.POLICY_VIOLATION.withReason("AUTH_EXPIRED"));
            return;
        }
        Map<String, Object> response = envelope(connection, "protocol.error");
        response.put("code", code);
        if (requestId != null) {
            response.put("requestId", requestId.toString());
        }
        send(session, connection, response);
    }

    private Map<String, Object> envelope(PressConnection connection, String type) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("v", 1);
        response.put("type", type);
        response.put("serverGeneration", connection.serverGeneration().toString());
        response.put("connectionGeneration", connection.connectionGeneration().toString());
        return response;
    }

    private void sendErrorAndClose(WebSocketSession session, PressConnection connection, String code, CloseStatus status) {
        closing.add(session.getId());
        rankingPublisher.unsubscribe(session.getId());
        Map<String, Object> response = envelope(connection, "protocol.error");
        response.put("code", code);
        TextMessage message = new TextMessage(json.writeValueAsString(response));
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (sender != null) {
            sender.offerResult(() -> {
                write(session, connection, message);
                terminate(session, status.withReason(code));
            }, message.getPayload().getBytes(StandardCharsets.UTF_8).length);
        }
        if (!sessions.containsKey(session.getId())) {
            closing.remove(session.getId());
        }
    }

    private void send(WebSocketSession session, PressConnection connection, Map<String, Object> response) {
        requireUnexpired(connection);
        TextMessage message = new TextMessage(json.writeValueAsString(response));
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (sender != null) {
            sender.offerResult(() -> write(session, connection, message), message.getPayload().getBytes(StandardCharsets.UTF_8).length);
        }
    }

    private void write(WebSocketSession session, PressConnection connection, WebSocketMessage<?> message) {
        requireUnexpired(connection);
        if (currentSessions.get(connection.devicePublicId()) != session) {
            throw new InvalidMessage("CONNECTION_REPLACED");
        }
        if (!session.isOpen() || !connections.containsKey(session.getId())) {
            throw new InvalidMessage("SNAPSHOT_SEND_FAILED");
        }
        try {
            session.sendMessage(message);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void received(WebSocketSession session) {
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        if (sender != null) {
            // 정상 명령 검증 뒤 DB/기기 잠금을 기다리기 전에 기록한다.
            sender.received(System.nanoTime());
        }
    }

    private void requireUnexpired(PressConnection connection) {
        if (connection.expiresAt() == null || !Instant.now().isBefore(connection.expiresAt())) {
            throw new InvalidMessage("AUTH_EXPIRED");
        }
    }

    private synchronized boolean allowMessage(UUID deviceId) {
        long now = System.nanoTime();
        MessageBucket bucket = messageBuckets.get(deviceId);
        if (bucket == null) {
            if (messageBuckets.size() >= MAX_DEVICES) {
                messageBuckets.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 60_000_000_000L);
            }
            if (messageBuckets.size() >= MAX_DEVICES) {
                return false;
            }
            bucket = new MessageBucket(now);
            messageBuckets.put(deviceId, bucket);
        }
        bucket.tokens = Math.min(40, bucket.tokens + (now - bucket.lastSeen) / 1_000_000_000.0 * 20);
        bucket.lastSeen = now;
        if (bucket.tokens < 1) {
            return false;
        }
        bucket.tokens--;
        return true;
    }

    private void fields(JsonNode node, Set<String> allowed) {
        if (!node.isObject() || !allowed.containsAll(node.propertyNames())) {
            throw new InvalidMessage("INVALID_INPUT");
        }
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isString()) {
            throw new InvalidMessage("INVALID_INPUT");
        }
        return value.stringValue();
    }

    private UUID uuid(JsonNode node, String name) {
        return parseUuid(text(node, name));
    }

    private UUID parseUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw new InvalidMessage("INVALID_INPUT");
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new InvalidMessage("INVALID_INPUT");
        }
    }

    private JsonNode array(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray() || value.size() > 20) {
            throw new InvalidMessage("INVALID_INPUT");
        }
        return value;
    }

    private Map<EmotionState, Integer> counts(JsonNode node) {
        JsonNode values = node.get("counts");
        if (values == null || !values.isObject()) {
            throw new InvalidMessage("INVALID_INPUT");
        }
        Map<EmotionState, Integer> counts = new EnumMap<>(EmotionState.class);
        for (String name : values.propertyNames()) {
            JsonNode count = values.get(name);
            if (!count.isIntegralNumber() || !count.canConvertToInt()) {
                throw new InvalidMessage("INVALID_INPUT");
            }
            try {
                counts.put(EmotionState.valueOf(name), count.intValue());
            } catch (IllegalArgumentException exception) {
                throw new InvalidMessage("INVALID_INPUT");
            }
        }
        return counts;
    }

    private static class MessageBucket {

        private double tokens = 40;
        private long lastSeen;

        private MessageBucket(long now) {
            lastSeen = now;
        }
    }

    private static class InvalidMessage extends RuntimeException {

        private final String code;

        private InvalidMessage(String code) {
            this.code = code;
        }
    }
}
