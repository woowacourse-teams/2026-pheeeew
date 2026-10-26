package com.pheeeew.pressrace.presentation;

import com.pheeeew.pressrace.application.PressRankingPublisher.Snapshot;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 연결당 송신 대기는 최신 하나. 공통 조회는 느린 소켓의 I/O를 기다리지 않는다. */
final class PressSnapshotSender {

    private final Object sendMonitor;
    private final Executor executor;
    private final Consumer<Snapshot> send;
    private final Consumer<RuntimeException> failed;
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
        revision++;
    }

    private void drain() {
        try {
            while (true) {
                synchronized (sendMonitor) {
                    Snapshot next;
                    long selectedRevision;
                    synchronized (this) {
                        // 입력 결과 송신 잠금을 기다리는 동안에도 대기는 최신 하나로 교체한다.
                        if (closed || pending == null) {
                            running = false;
                            return;
                        }
                        next = pending.forDelivery(lastSent, baselinePending);
                        pending = null;
                        selectedRevision = revision;
                        if (!next.baseline() && next.screen().equals(lastSent.screen())
                                && !next.overtakes().hasChanges()) {
                            continue;
                        }
                    }
                    send.accept(next);
                    synchronized (this) {
                        if (selectedRevision == revision) {
                            lastSent = next;
                            baselinePending = false;
                        }
                    }
                }
            }
        } catch (RuntimeException exception) {
            close();
            failed.accept(exception);
        }
    }
}
