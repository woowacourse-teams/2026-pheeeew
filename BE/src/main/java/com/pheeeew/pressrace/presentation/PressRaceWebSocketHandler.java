package com.pheeeew.pressrace.presentation;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.application.PressRankingPublisher;
import com.pheeeew.pressrace.domain.PendingPress;
import com.pheeeew.pressrace.domain.PressConnection;
import com.pheeeew.pressrace.domain.PressInputResult;
import com.pheeeew.pressrace.domain.PressSessionResult;
import com.pheeeew.pressrace.domain.PressView;
import com.pheeeew.pressrace.exception.PressRaceException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
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
        synchronized (connectionLocks[Math.floorMod(deviceId.hashCode(), connectionLocks.length)]) {
            try {
                requireUnexpired(connection);
                service.connect(connection);
                connections.put(session.getId(), connection);
                WebSocketSession previous = currentSessions.put(deviceId, session);
                if (previous != null && previous != session && previous.isOpen()) {
                    previous.close(CloseStatus.POLICY_VIOLATION.withReason("CONNECTION_REPLACED"));
                }
            } catch (PressRaceException exception) {
                rejectConnection(session, connection, exception);
            } catch (InvalidMessage exception) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason(exception.code));
            }
        }
    }

    private void rejectConnection(WebSocketSession session, PressConnection connection, PressRaceException exception)
            throws IOException {
        synchronized (session) {
            if (connection.expiresAt() == null || !Instant.now().isBefore(connection.expiresAt())) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("AUTH_EXPIRED"));
                return;
            }
            Map<String, Object> response = envelope(connection, "protocol.error");
            response.put("code", exception.code());
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json.writeValueAsString(response)));
                }
            } finally {
                session.close(CloseStatus.POLICY_VIOLATION.withReason(exception.code()));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        PressConnection connection = connections.get(session.getId());
        if (connection == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            requireUnexpired(connection);
            if (message.getPayload().getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                session.close(CloseStatus.TOO_BIG_TO_PROCESS);
                return;
            }
            if (!allowMessage(connection.devicePublicId())) {
                sendError(session, connection, "MESSAGE_RATE_LIMITED");
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            JsonNode root;
            try {
                root = json.readTree(message.getPayload());
            } catch (RuntimeException exception) {
                sendError(session, connection, "INVALID_JSON");
                session.close(CloseStatus.BAD_DATA);
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
            sendError(session, connection, exception.code);
            if (exception.code.equals("AUTH_EXPIRED")) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (PressRaceException exception) {
            sendError(session, connection, exception.code(), exception.requestId());
            if (Set.of("AUTH_EXPIRED", "SERVER_REPLACED", "CONNECTION_REPLACED").contains(exception.code())) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (RuntimeException exception) {
            sendError(session, connection, "TEMPORARILY_UNAVAILABLE");
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        session.close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        synchronized (session) {
            rankingPublisher.unsubscribe(session.getId());
            PressSnapshotSender sender = snapshotSenders.remove(session.getId());
            if (sender != null) {
                sender.close();
            }
            PressConnection connection = connections.remove(session.getId());
            if (connection != null) {
                currentSessions.remove(connection.devicePublicId(), session);
            }
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
        PressSessionResult result = service.initialize(connection, inputs);
        Map<String, Object> response = envelope(connection, "session.ready");
        response.put("counts", result.counts());
        response.put("results", result.results().stream().map(this::resultPayload).toList());
        response.put("date", result.date().toString());
        synchronized (session) {
            rankingPublisher.unsubscribe(session.getId());
            PressSnapshotSender oldSender = snapshotSenders.remove(session.getId());
            if (oldSender != null) {
                oldSender.close();
            }
            send(session, connection, response);
            if (!session.isOpen() || !connections.containsKey(session.getId())) {
                return;
            }
            PressSnapshotSender sender = new PressSnapshotSender(session, snapshotExecutor,
                    snapshot -> sendSnapshot(session, connection, snapshot),
                    failure -> stopSnapshots(session,
                            failure instanceof InvalidMessage invalid ? invalid.code : "SNAPSHOT_SEND_FAILED"));
            snapshotSenders.put(session.getId(), sender);
            rankingPublisher.subscribe(session.getId(), connection.devicePublicId(), PressView.valueOf(view),
                    sender::offer, code -> {
                        sender.close();
                        snapshotExecutor.execute(() -> stopSnapshots(session, code));
                    });
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
        PressSnapshotSender sender = snapshotSenders.get(session.getId());
        // 세션 송신 → publisher 순서다. publisher에서는 세션 I/O 잠금을 잡지 않는다.
        synchronized (session) {
            if (sender == null || !rankingPublisher.changeView(session.getId(), view, sender::reset)) {
                throw new InvalidMessage("SESSION_NOT_READY");
            }
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
        try {
            if (!send(session, connection, response)) {
                throw new InvalidMessage("SNAPSHOT_SEND_FAILED");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void stopSnapshots(WebSocketSession session, String code) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(code));
        } catch (IOException ignored) {
            // 이미 실패한 연결은 종료 콜백에서 정리한다.
        }
    }

    @PreDestroy
    void stopSnapshotExecutor() {
        snapshotSenders.values().forEach(PressSnapshotSender::close);
        snapshotExecutor.shutdownNow();
    }

    private void submit(WebSocketSession session, PressConnection connection, JsonNode root) throws IOException {
        fields(root, Set.of("v", "type", "requestId", "connectionGeneration", "counts"));
        if (!uuid(root, "connectionGeneration").equals(connection.connectionGeneration())) {
            throw new InvalidMessage("CONNECTION_REPLACED");
        }
        sendResult(session, connection, service.submit(connection, uuid(root, "requestId"), counts(root)));
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
            session.close(CloseStatus.POLICY_VIOLATION.withReason("AUTH_EXPIRED"));
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

    private boolean send(WebSocketSession session, PressConnection connection, Map<String, Object> response) throws IOException {
        synchronized (session) {
            requireUnexpired(connection);
            WebSocketSession current = currentSessions.get(connection.devicePublicId());
            if (current != session) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("CONNECTION_REPLACED"));
                return false;
            }
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json.writeValueAsString(response)));
                return true;
            }
            return false;
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
