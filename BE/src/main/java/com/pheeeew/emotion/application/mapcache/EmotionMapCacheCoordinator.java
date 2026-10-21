package com.pheeeew.emotion.application.mapcache;

import java.util.concurrent.locks.ReentrantLock;
import io.micrometer.core.instrument.MeterRegistry;
import com.pheeeew.emotion.infra.metrics.MapCacheLockMetrics;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Component;

@Component
public class EmotionMapCacheCoordinator {

    private final ReentrantLock writer = new ReentrantLock();
    private final Object state = new Object();
    private final Object ambientKey = new Object();
    private final ObjectProvider<EmotionMapCacheListener> listeners;
    private final ObjectProvider<MapCacheMaintenance> maintenance;
    private final MapCacheLockMetrics metrics;
    private long epoch;
    private int uncoordinated;

    public EmotionMapCacheCoordinator(ObjectProvider<EmotionMapCacheListener> listeners,
            ObjectProvider<MapCacheMaintenance> maintenance, MeterRegistry meters) {
        this.listeners = listeners;
        this.maintenance = maintenance;
        this.metrics = new MapCacheLockMetrics(meters);
    }

    public <T> T serialized(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            markAmbientTransaction();
            return action.get();
        }

        long waiting = metrics.startWait();
        writer.lock();
        metrics.recordWait(waiting);

        try {
            return action.get();
        } finally {
            writer.unlock();
        }
    }

    public <T> T trySerialized(Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || !writer.tryLock()) {
            return null;
        }

        try {
            return action.get();
        } finally {
            writer.unlock();
        }
    }

    /** action은 완성된 메모리 상태만 읽으며 DB 조회나 writer 대기를 수행하지 않는다. */
    public <T> T read(Function<Long, T> action) {
        synchronized (state) {
            return uncoordinated == 0 ? action.apply(epoch) : null;
        }
    }

    public boolean publish(long expectedEpoch, Runnable publication) {
        synchronized (state) {
            if (uncoordinated != 0 || epoch != expectedEpoch) {
                return false;
            }

            publication.run();
            return true;
        }
    }

    public void afterCommitChange(EmotionMapContribution before, EmotionMapContribution after) {
        afterCommit(() -> {
            if (read(epoch -> Boolean.TRUE) != null) {
                // 큰 후보 스냅샷을 만드는 동안 조회용 state monitor를 보유하지 않는다.
                // 각 리스너는 계산에 사용한 epoch로 publish해야 한다.
                listeners.orderedStream().forEach(listener -> listener.changed(before, after));
            }
        });
    }

    public void invalidateAfterCommit() {
        afterCommit(this::invalidate);
    }

    public void invalidate() {
        invalidateState();

        maintenance.ifAvailable(MapCacheMaintenance::requestRebuild);
    }

    void discard(Runnable invalidation) {
        synchronized (state) {
            invalidation.run();
        }
    }

    private void markAmbientTransaction() {
        if (TransactionSynchronizationManager.hasResource(ambientKey)) {
            return;
        }

        TransactionSynchronizationManager.bindResource(ambientKey, Boolean.TRUE);
        synchronized (state) {
            uncoordinated++;
            invalidateState();
        }

        maintenance.ifAvailable(MapCacheMaintenance::requestRebuild);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                synchronized (state) {
                    invalidateState();
                    uncoordinated--;
                }

                TransactionSynchronizationManager.unbindResourceIfPossible(ambientKey);
                maintenance.ifAvailable(MapCacheMaintenance::requestRebuild);
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("캐시 변경은 원본 트랜잭션 안에서 등록해야 합니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            private boolean processed;

            @Override
            public void afterCommit() {
                try {
                    action.run();
                    processed = true;
                } catch (RuntimeException failure) {
                    invalidate();
                    processed = true;
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_UNKNOWN || status == STATUS_COMMITTED && !processed) {
                    invalidate();
                }
            }
        });
    }

    private void invalidateState() {
        synchronized (state) {
            epoch++;
            listeners.orderedStream().forEach(listener -> {
                try {
                    listener.invalidate();
                } catch (RuntimeException ignored) {
                    // epoch도 함께 바뀌므로 실패한 엔트리 역시 이후 조회에서 재사용하지 않는다.
                }
            });
        }
    }

}
