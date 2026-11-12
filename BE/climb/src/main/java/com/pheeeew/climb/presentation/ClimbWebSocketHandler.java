package com.pheeeew.climb.presentation;

import com.pheeeew.climb.application.ClimbBoard;
import com.pheeeew.climb.application.ClimbGuestService;
import com.pheeeew.climb.application.ClimbRankingService;
import com.pheeeew.climb.domain.ClimbGuest;
import com.pheeeew.climb.domain.ClimbMap;
import com.pheeeew.climb.domain.ClimbRankEntry;
import com.pheeeew.climb.domain.ClimbRanking;
import com.pheeeew.climb.domain.ClimbRankView;
import com.pheeeew.climb.domain.ClimbRun;
import com.pheeeew.climb.domain.ClimbRunRecord;
import com.pheeeew.climb.infra.ClimbRunRepository;
import com.pheeeew.climb.presentation.ClimbResponses.GuestResponse;
import com.pheeeew.climb.presentation.ClimbResponses.MyRank;
import com.pheeeew.climb.presentation.ClimbResponses.RankItem;
import com.pheeeew.climb.presentation.ClimbResponses.RunResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * /api/v1/climb/ws. 브라우저가 높이를 보고하고, 서버는 1초마다 바뀐 순위를 보낸다.
 * 오류는 error { code } 를 보낸 뒤 닫는다. 검사에 걸린 보고는 오류 없이 버린다.
 */
public class ClimbWebSocketHandler extends AbstractWebSocketHandler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ClimbWebSocketHandler.class);
    private static final Set<String> CHARACTERS = Set.of("defeated", "tired", "frustrated", "annoyed", "angry");
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;
    private static final int TOP_SIZE = 10;
    private static final Duration SAVE_INTERVAL = Duration.ofSeconds(10);
    private static final long PING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final long PONG_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final ClimbRankingService ranking;
    private final ClimbRunRepository runs;
    private final ClimbGuestService guests;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, ClimbConnection> connections = new ConcurrentHashMap<>();
    private final Map<UUID, ClimbConnection> current = new ConcurrentHashMap<>();
    private final Object[] guestLocks = new Object[128];
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("climb-ticker").daemon().factory());
    private volatile boolean running;

    public ClimbWebSocketHandler(ClimbRankingService ranking, ClimbRunRepository runs, ClimbGuestService guests) {
        this.ranking = ranking;
        this.runs = runs;
        this.guests = guests;
        for (int index = 0; index < guestLocks.length; index++) {
            guestLocks[index] = new Object();
        }
    }

    @Override
    public void start() {
        running = true;
        ticker.scheduleWithFixedDelay(() -> safely(this::tick), 1, 1, TimeUnit.SECONDS);
        ticker.scheduleWithFixedDelay(() -> safely(guests::deleteInactive), 1, 24 * 60, TimeUnit.MINUTES);
    }

    /** 배포로 서버가 내려갈 때 진행을 저장하고 SERVER_DRAINING 으로 닫는다. 브라우저는 다시 연결한다. */
    @Override
    public void stop() {
        running = false;
        ticker.shutdownNow();
        current.values().forEach(connection -> {
            synchronized (lock(connection.guest().id())) {
                save(connection);
                fail(connection, "SERVER_DRAINING", CloseStatus.SERVICE_RESTARTED);
            }
        });
        senders.shutdown();
        try {
            senders.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!(session.getPrincipal() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof ClimbGuest guest)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
        if (session instanceof StandardWebSocketSession standard) {
            // 송신 시간 제한. 느린 연결 하나가 송신 스레드를 오래 잡지 않게 한다.
            Map<String, Object> properties = standard.getNativeSession().getUserProperties();
            properties.put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 2000L);
            properties.put("org.apache.tomcat.websocket.SESSION_CLOSE_TIMEOUT", 2000L);
        }
        ClimbConnection connection = new ClimbConnection(session, guest, senders);
        if (!running) {
            fail(connection, "SERVER_DRAINING", CloseStatus.SERVICE_RESTARTED);
            return;
        }
        connections.put(session.getId(), connection);
        synchronized (lock(guest.id())) {
            ClimbConnection previous = current.put(guest.id(), connection);
            if (previous != null) {
                save(previous);
                fail(previous, "CONNECTION_REPLACED", CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClimbConnection connection = connections.get(session.getId());
        if (connection == null || connection.closing()) {
            return;
        }
        long now = System.nanoTime();
        connection.received(now);
        if (!connection.allowMessage(now)) {
            fail(connection, "MESSAGE_RATE_LIMITED", CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            JsonNode root = json.readTree(message.getPayload());
            synchronized (lock(connection.guest().id())) {
                if (current.get(connection.guest().id()) != connection) {
                    return;
                }
                handle(connection, root, Instant.now());
            }
        } catch (InvalidMessage invalid) {
            fail(connection, invalid.code, CloseStatus.POLICY_VIOLATION);
        } catch (RuntimeException exception) {
            if (exception instanceof tools.jackson.core.JacksonException) {
                fail(connection, "INVALID_MESSAGE", CloseStatus.POLICY_VIOLATION);
                return;
            }
            log.warn("등반 메시지 처리 실패", exception);
            fail(connection, "TEMPORARILY_UNAVAILABLE", CloseStatus.SERVER_ERROR);
        }
    }

    private void handle(ClimbConnection connection, JsonNode root, Instant now) {
        String type = text(root, "t");
        if ("hello".equals(type)) {
            hello(connection, root, now);
            return;
        }
        if (!connection.helloed()) {
            throw new InvalidMessage("HELLO_REQUIRED");
        }
        ClimbRun run = connection.run();
        switch (type) {
            case "pos" -> {
                boolean accepted = run != null && run.pos(integer(root, "h"), integer(root, "best"),
                        integer(root, "falls"), integer(root, "time"), integer(root, "x"), now);
                if (accepted) {
                    publish(connection, run);
                    if (connection.saveDue(now, SAVE_INTERVAL)) {
                        runs.save(run.record());
                    }
                }
            }
            case "zone" -> {
                int zone = integer(root, "zone");
                if (run != null && run.zone(zone, integer(root, "time"), now)) {
                    runs.save(run.record());
                    publish(connection, run);
                    announce(connection, "zone", zone);
                }
            }
            case "finish" -> {
                if (run != null && run.finish(integer(root, "time"), integer(root, "falls"), now)) {
                    runs.save(run.record());
                    publish(connection, run);
                    if (!run.suspect()) {
                        announce(connection, "finish", null);
                    }
                }
            }
            case "restart" -> {
                if (run != null) {
                    runs.save(run.record());
                    ClimbRun restarted = ClimbRun.start(UUID.randomUUID(), connection.guest().id(), run.map(),
                            run.record().character(), now);
                    restarted.attach(now);
                    runs.save(restarted.record());
                    connection.replaceRun(restarted);
                    publish(connection, restarted);
                }
            }
            default -> throw new InvalidMessage("INVALID_MESSAGE");
        }
    }

    private void hello(ClimbConnection connection, JsonNode root, Instant now) {
        if (connection.helloed()) {
            throw new InvalidMessage("INVALID_MESSAGE");
        }
        JsonNode version = root.get("v");
        if (version == null || !version.isIntegralNumber() || version.intValue() != 1) {
            throw new InvalidMessage("UNSUPPORTED_VERSION");
        }
        String mapVersion = text(root, "map");
        String character = text(root, "character");
        if (mapVersion.isEmpty() || mapVersion.length() > 16 || !CHARACTERS.contains(character)) {
            throw new InvalidMessage("INVALID_MESSAGE");
        }
        ClimbGuest guest = connection.guest();
        Optional<ClimbMap> map = ranking.map(mapVersion);
        ClimbRun run = null;
        RunResponse resumed = null;
        // 서버가 모르는 맵 버전은 연결은 두되 순위에 넣지 않는다.
        if (map.isPresent()) {
            Optional<ClimbRunRecord> saved = runs.findLatestUnfinished(guest.id(), mapVersion);
            if (saved.isPresent()) {
                run = ClimbRun.restore(saved.get(), map.get());
                run.changeCharacter(character);
                resumed = RunResponse.from(run.record());
            } else {
                run = ClimbRun.start(UUID.randomUUID(), guest.id(), map.get(), character, now);
                runs.save(run.record());
            }
            run.attach(now);
        }
        connection.hello(mapVersion, run);
        if (run != null) {
            publish(connection, run);
        }
        Map<String, Object> welcome = message("welcome");
        welcome.put("me", GuestResponse.from(guest));
        welcome.put("run", resumed);
        welcome.put("online", ranking.onlineCount());
        welcome.put("serverTime", now.toEpochMilli());
        connection.offer(new TextMessage(json.writeValueAsString(welcome)));
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        ClimbConnection connection = connections.get(session.getId());
        if (connection != null) {
            connection.received(System.nanoTime());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ClimbConnection connection = connections.get(session.getId());
        if (connection != null) {
            fail(connection, "INVALID_MESSAGE", CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(CloseStatus.SERVER_ERROR.withReason("TRANSPORT_ERROR"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ClimbConnection connection = connections.remove(session.getId());
        if (connection == null) {
            return;
        }
        UUID guestId = connection.guest().id();
        synchronized (lock(guestId)) {
            if (current.remove(guestId, connection)) {
                if (connection.mapVersion() != null) {
                    ranking.leave(connection.mapVersion(), guestId);
                }
                save(connection);
            }
        }
    }

    void tick() {
        long now = System.nanoTime();
        Instant instant = Instant.now();
        connections.values().forEach(connection -> {
            if (now - connection.lastReceived() >= PONG_TIMEOUT_NANOS) {
                fail(connection, "HEARTBEAT_TIMEOUT", CloseStatus.POLICY_VIOLATION);
            } else if (connection.pingDue(now, PING_INTERVAL_NANOS)) {
                connection.offer(new PingMessage(ByteBuffer.allocate(0)));
            }
        });
        int online = ranking.onlineCount();
        Map<String, List<ClimbConnection>> byMap = current.values().stream()
                .filter(ClimbConnection::helloed)
                .collect(Collectors.groupingBy(ClimbConnection::mapVersion));
        byMap.forEach((mapVersion, members) -> {
            // 상위 순위는 맵마다 한 번만 만들고, 접속자마다 내 순위만 붙인다.
            List<ClimbRankEntry> sorted = ranking.board(mapVersion, ClimbBoard.ONLINE, instant);
            members.forEach(connection -> {
                ClimbRankView view = ClimbRanking.view(sorted, connection.guest().id(), TOP_SIZE);
                Map<String, Object> rank = message("rank");
                rank.put("top", RankItem.from(view.top()));
                rank.put("me", MyRank.from(view.me()));
                rank.put("near", RankItem.from(view.near()));
                rank.put("online", online);
                connection.offerRank(json.writeValueAsString(rank));
            });
        });
    }

    private void publish(ClimbConnection connection, ClimbRun run) {
        ClimbGuest guest = connection.guest();
        ranking.record(connection.mapVersion(), run.entry(guest.tag(), guest.nickname()), run.suspect());
    }

    private void announce(ClimbConnection source, String kind, Integer zone) {
        Map<String, Object> event = message("event");
        event.put("kind", kind);
        event.put("nickname", source.guest().nickname());
        event.put("tag", source.guest().tag());
        event.put("zone", zone);
        String text = json.writeValueAsString(event);
        current.values().stream()
                .filter(connection -> source.mapVersion().equals(connection.mapVersion()))
                .forEach(connection -> connection.offer(new TextMessage(text)));
    }

    private void save(ClimbConnection connection) {
        ClimbRun run = connection.run();
        if (run != null) {
            runs.save(run.record());
        }
    }

    private void fail(ClimbConnection connection, String code, CloseStatus status) {
        Map<String, Object> error = message("error");
        error.put("code", code);
        connection.closeAfter(new TextMessage(json.writeValueAsString(error)), status.withReason(code));
    }

    private static Map<String, Object> message(String type) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", type);
        return message;
    }

    private Object lock(UUID guestId) {
        return guestLocks[Math.floorMod(guestId.hashCode(), guestLocks.length)];
    }

    private static void safely(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            log.warn("등반 주기 작업 실패", exception);
        }
    }

    private static String text(JsonNode root, String name) {
        JsonNode value = root == null ? null : root.get(name);
        if (value == null || !value.isString()) {
            throw new InvalidMessage("INVALID_MESSAGE");
        }
        return value.stringValue();
    }

    private static int integer(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidMessage("INVALID_MESSAGE");
        }
        return value.intValue();
    }

    private static final class InvalidMessage extends RuntimeException {

        private final String code;

        private InvalidMessage(String code) {
            super(code, null, false, false);
            this.code = code;
        }
    }
}
