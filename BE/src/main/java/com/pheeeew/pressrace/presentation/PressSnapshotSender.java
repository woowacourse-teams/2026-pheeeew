package com.pheeeew.pressrace.presentation;

import com.pheeeew.pressrace.application.PressRankingPublisher.Snapshot;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 결과 FIFO와 최신 순위를 같은 송신 경계에서 보낸다. 공통 조회는 소켓 I/O를 기다리지 않는다. */
final class PressSnapshotSender {

    private final Object sendMonitor;
    private final Executor executor;
    private final Consumer<Snapshot> send;
    private final Consumer<RuntimeException> failed;
    private static final int MAX_RESULTS = 32;
    private static final int MAX_RESULT_BYTES = 64 * 1024;
    private static final int RESULT_BURST = 4;
    private final Queue<Control> results = new ArrayDeque<>();
    private int resultCount;
    private int resultBytes;
    private int consecutiveResults;
    private boolean pingPending;
    private Runnable ping;
    private boolean awaitingReady;
    private long readyRevision;
    private long sendingSince;
    private long lastReceived = System.nanoTime();
    private long lastPing = lastReceived;
    private Snapshot pending;
    private Snapshot lastSent;
    private long revision;
    private boolean baselinePending = true;
    private boolean running;
    private boolean closed;

    PressSnapshotSender(Object sendMonitor, Executor executor, Consumer<Snapshot> send, Consumer<RuntimeException> failed) {
        this.sendMonitor = sendMonitor;
        this.executor = executor;
        this.send = send;
        this.failed = failed;
    }

    synchronized void offer(Snapshot snapshot) {
        if (closed) {
            return;
        }
        if (snapshot.baseline()) {
            reset();
        }
        pending = snapshot;
        start();
    }

    void offerReady(Runnable write, int bytes) {
        Control control;
        synchronized (this) {
            reset();
            awaitingReady = true;
            readyRevision = revision;
            control = Control.of(write, bytes, readyRevision);
        }
        offerControl(control);
    }

    void offerResult(Runnable write, int bytes) {
        offerControl(Control.of(write, bytes, -1));
    }

    private void offerControl(Control control) {
        boolean overflow;
        synchronized (this) {
            if (closed) {
                return;
            }
            overflow = resultCount >= MAX_RESULTS || control.bytes > MAX_RESULT_BYTES - resultBytes;
            if (!overflow) {
                results.add(control);
                resultCount++;
                resultBytes += control.bytes;
                start();
            }
        }
        if (overflow) {
            fail(new IllegalStateException("RESULT_QUEUE_OVERFLOW"));
        }
    }

    synchronized void received(long now) {
        if (now - lastReceived > 0) {
            lastReceived = now;
        }
    }

    void maintain(long now, boolean canSend, Runnable sendPing) {
        String failure = null;
        synchronized (this) {
            if (closed) {
                return;
            }
            if (sendingSince != 0 && now - sendingSince >= TimeUnit.SECONDS.toNanos(2)) {
                failure = "SEND_TIMEOUT";
            } else if (now - lastReceived >= TimeUnit.SECONDS.toNanos(45)) {
                failure = "HEARTBEAT_TIMEOUT";
            } else if (canSend && now - lastPing >= TimeUnit.SECONDS.toNanos(15)) {
                lastPing = now;
                pingPending = true;
                ping = sendPing;
                start();
            }
            if (failure != null) {
                close();
            }
        }
        if (failure != null) {
            failed.accept(new IllegalStateException(failure));
        }
    }

    private void start() {
        if (!running) {
            running = true;
            executor.execute(this::drain);
        }
    }

    synchronized void reset() {
        revision++;
        pending = null;
        lastSent = null;
        baselinePending = true;
    }

    synchronized void close() {
        closed = true;
        pending = null;
        lastSent = null;
        results.clear();
        ping = null;
        pingPending = false;
        resultBytes = 0;
        resultCount = 0;
        revision++;
    }

    private void fail(RuntimeException exception) {
        synchronized (this) {
            if (closed) {
                return;
            }
            close();
        }
        failed.accept(exception);
    }

    private void drain() {
        try {
            while (true) {
                synchronized (sendMonitor) {
                    Snapshot next = null;
                    Control control = null;
                    Runnable heartbeat = null;
                    long selectedRevision;
                    synchronized (this) {
                        // 실제 송신 경계에서 최신 순위를 고른다. 결과가 계속 와도 네 개 뒤 순위 차례를 보장한다.
                        if (closed || (results.isEmpty() && (pending == null || awaitingReady) && !pingPending)) {
                            running = false;
                            return;
                        }
                        selectedRevision = revision;
                        if (pingPending) {
                            heartbeat = ping;
                            pingPending = false;
                            ping = null;
                        } else if (!results.isEmpty() && (pending == null || awaitingReady || consecutiveResults < RESULT_BURST)) {
                            control = results.remove();
                            consecutiveResults++;
                        } else {
                            next = pending.forDelivery(lastSent, baselinePending);
                            pending = null;
                            consecutiveResults = 0;
                            if (!next.baseline() && next.screen().equals(lastSent.screen())
                                    && !next.overtakes().hasChanges()) {
                                continue;
                            }
                        }
                        sendingSince = System.nanoTime();
                    }
                    try {
                        if (control != null) {
                            control.write.run();
                        } else if (heartbeat != null) {
                            heartbeat.run();
                        } else {
                            send.accept(next);
                        }
                    } finally {
                        synchronized (this) {
                            sendingSince = 0;
                        }
                    }
                    synchronized (this) {
                        if (closed) {
                            return;
                        }
                        if (control != null) {
                            resultCount--;
                            resultBytes -= control.bytes;
                            if (control.readyRevision >= 0 && control.readyRevision == readyRevision) {
                                awaitingReady = false;
                            }
                        } else if (next != null && selectedRevision == revision) {
                            lastSent = next;
                            baselinePending = false;
                        }
                    }
                }
            }
        } catch (RuntimeException exception) {
            fail(exception);
        }
    }

    private record Control(Runnable write, int bytes, long readyRevision) {
        private static Control of(Runnable write, int bytes, long readyRevision) {
            return new Control(write, bytes, readyRevision);
        }
    }
}
