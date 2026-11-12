package com.pheeeew.climb.presentation;

import com.pheeeew.climb.domain.ClimbGuest;
import com.pheeeew.climb.domain.ClimbRun;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 웹소켓 연결 하나. 송신은 연결마다 한 줄로 세워 실행기에서 보낸다.
 * 순위는 최신 하나만 남기고, 나머지 메시지(welcome, event, error, Ping)는 순서대로 보낸다.
 */
final class ClimbConnection {

    private static final int MAX_QUEUED = 32;
    private static final int MESSAGES_PER_SECOND = 10;

    private final WebSocketSession session;
    private final ClimbGuest guest;
    private final Executor executor;
    private final Queue<WebSocketMessage<?>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicReference<TextMessage> latestRank = new AtomicReference<>();
    private final AtomicBoolean sending = new AtomicBoolean();
    private volatile CloseStatus closeAfterSend;
    private volatile boolean helloed;
    private volatile String mapVersion;
    private volatile ClimbRun run;
    private volatile String lastRank;
    private volatile long lastReceived = System.nanoTime();
    private volatile long lastPing = lastReceived;
    private Instant lastSavedAt = Instant.EPOCH;
    private double tokens = MESSAGES_PER_SECOND;
    private long tokensAt = lastReceived;

    ClimbConnection(WebSocketSession session, ClimbGuest guest, Executor executor) {
        this.session = session;
        this.guest = guest;
        this.executor = executor;
    }

    ClimbGuest guest() {
        return guest;
    }

    boolean helloed() {
        return helloed;
    }

    String mapVersion() {
        return mapVersion;
    }

    ClimbRun run() {
        return run;
    }

    void hello(String mapVersion, ClimbRun run) {
        this.mapVersion = mapVersion;
        this.run = run;
        this.helloed = true;
    }

    void replaceRun(ClimbRun run) {
        this.run = run;
    }

    boolean closing() {
        return closeAfterSend != null;
    }

    /** 마지막 저장 뒤 interval 이 지났으면 true 를 돌려주고 저장 시각을 now 로 옮긴다. */
    boolean saveDue(Instant now, Duration interval) {
        if (lastSavedAt.plus(interval).isAfter(now)) {
            return false;
        }
        lastSavedAt = now;
        return true;
    }

    void received(long now) {
        lastReceived = now;
    }

    long lastReceived() {
        return lastReceived;
    }

    boolean pingDue(long now, long intervalNanos) {
        if (now - lastPing < intervalNanos) {
            return false;
        }
        lastPing = now;
        return true;
    }

    /** 손님당 초당 10개. 연결이 손님마다 하나라 연결 단위로 센다. */
    synchronized boolean allowMessage(long now) {
        tokens = Math.min(MESSAGES_PER_SECOND, tokens + (now - tokensAt) / 1_000_000_000.0 * MESSAGES_PER_SECOND);
        tokensAt = now;
        if (tokens < 1) {
            return false;
        }
        tokens--;
        return true;
    }

    void offerRank(String text) {
        if (text.equals(lastRank) || closing()) {
            return;
        }
        lastRank = text;
        latestRank.set(new TextMessage(text));
        schedule();
    }

    void offer(WebSocketMessage<?> message) {
        if (closing()) {
            return;
        }
        if (queued.incrementAndGet() > MAX_QUEUED) {
            close(CloseStatus.POLICY_VIOLATION.withReason("SEND_QUEUE_OVERFLOW"));
            return;
        }
        queue.add(message);
        schedule();
    }

    /** 대기 중인 메시지를 보낸 뒤 닫는다. 그 뒤로 들어온 메시지는 보내지 않는다. */
    void closeAfter(TextMessage last, CloseStatus status) {
        if (closing()) {
            return;
        }
        queue.add(last);
        queued.incrementAndGet();
        closeAfterSend = status;
        latestRank.set(null);
        schedule();
    }

    void close(CloseStatus status) {
        closeAfterSend = status;
        queue.clear();
        latestRank.set(null);
        schedule();
    }

    private void schedule() {
        if (sending.compareAndSet(false, true)) {
            executor.execute(this::drain);
        }
    }

    private void drain() {
        try {
            while (true) {
                WebSocketMessage<?> next = queue.poll();
                if (next != null) {
                    queued.decrementAndGet();
                } else if (closeAfterSend == null) {
                    next = latestRank.getAndSet(null);
                }
                if (next == null) {
                    if (closeAfterSend != null) {
                        session.close(closeAfterSend);
                        return;
                    }
                    sending.set(false);
                    if ((queue.isEmpty() && latestRank.get() == null) || !sending.compareAndSet(false, true)) {
                        return;
                    }
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(next);
                }
            }
        } catch (Exception failure) {
            // 송신 시간 제한(2초)을 넘었거나 연결이 깨졌다. 다시 연결하면 순위가 돌아온다.
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("SEND_FAILED"));
            } catch (Exception ignored) {
                // 이미 닫힌 연결이다.
            }
        }
    }
}
