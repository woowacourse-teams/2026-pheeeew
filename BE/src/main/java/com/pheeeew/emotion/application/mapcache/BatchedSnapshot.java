package com.pheeeew.emotion.application.mapcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pheeeew.emotion.infra.metrics.MapCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

/** 두 지도 캐시가 공유하는 게시 규칙. 계산과 렌더링은 writer 안에서 state monitor 밖에서 수행한다. */
final class BatchedSnapshot<C, S> {

    private final Cache<String, Published<S>> snapshots = Caffeine.newBuilder().maximumSize(1).build();
    private final EmotionMapCacheCoordinator coordinator;
    private final MapCacheUpdateScheduler updates;
    private final Function<C, S> render;
    private final MapCacheMetrics metrics;
    private Current<C> canonical;
    private Pending pending;

    BatchedSnapshot(EmotionMapCacheCoordinator coordinator, MapCacheUpdateScheduler updates,
            Function<C, S> render, MeterRegistry meters, String name) {
        this.coordinator = coordinator;
        this.updates = updates;
        this.render = render;
        this.metrics = new MapCacheMetrics(meters, name);

        metrics.pendingAge(this, source -> {
            Double age = coordinator.read(epoch -> source.pending == null ? 0.0 : Math.max(0,
                    Duration.between(source.pending.firstDirty, updates.now()).toNanos() / 1_000_000_000.0));

            return age == null ? 0.0 : age;
        });
    }

    Read<S> read() {
        Read<S> result = coordinator.read(epoch -> {
            if (!updates.available() || pending != null
                    && !updates.now().isBefore(pending.firstDirty.plusSeconds(3))) {
                clear();
                return Read.of(null, false);
            }

            Published<S> published = snapshots.getIfPresent("snapshot");

            return Read.of(published != null && published.epoch() == epoch && active(published.nextActivation())
                    ? published.value() : null, true);
        });

        return result == null ? Read.of(null, false) : result;
    }

    Current<C> canonical() {
        return coordinator.read(epoch -> canonical != null && canonical.epoch == epoch
                && active(canonical.nextActivation) ? canonical : null);
    }

    boolean install(long expectedEpoch, C value, S snapshot, Instant nextActivation) {
        return coordinator.publish(expectedEpoch, () -> {
            canonical = new Current<>(expectedEpoch, value, nextActivation);
            cancelPending();
            snapshots.put("snapshot", Published.of(expectedEpoch, snapshot, nextActivation));
        });
    }

    void change(Current<C> expected, C next, Instant nextActivation, Publication publication) {
        Current<C> replacement = new Current<>(expected.epoch, next, nextActivation);

        if (publication == Publication.IMMEDIATE) {
            S rendered = render.apply(next);

            coordinator.publish(expected.epoch, () -> {
                if (canonical == expected) {
                    canonical = replacement;
                    cancelPending();
                    snapshots.put("snapshot", Published.of(expected.epoch, rendered, nextActivation));
                }
            });
            return;
        }

        Pending first = new Pending(expected.epoch, updates.now());
        coordinator.publish(expected.epoch, () -> {
            if (canonical == expected) {
                canonical = replacement;
                if (pending == null) {
                    pending = first;
                }
            }
        });

        if (coordinator.read(epoch -> pending == first) == Boolean.TRUE) {
            schedule(first);
        }
    }

    void invalidate() {
        coordinator.discard(this::clear);
    }

    private boolean active(Instant nextActivation) {
        return nextActivation == null || updates.now().isBefore(nextActivation);
    }

    private void schedule(Pending first) {
        ScheduledFuture<?> future = updates.schedule(() -> flush(first),
                Duration.between(updates.now(), first.firstDirty.plusSeconds(1)));
        first.future = future;

        if (future == null) {
            fail(first);
        } else if (coordinator.read(epoch -> pending == first) != Boolean.TRUE) {
            future.cancel(false);
        }
    }

    private void flush(Pending expected) {
        try {
            coordinator.serialized(() -> {
                Current<C> source = coordinator.read(epoch -> epoch == expected.epoch && pending == expected
                        && canonical != null && active(canonical.nextActivation) ? canonical : null);

                if (source != null) {
                    S rendered = render.apply(source.value);

                    coordinator.publish(expected.epoch, () -> {
                        if (pending == expected && canonical == source) {
                            snapshots.put("snapshot", Published.of(expected.epoch, rendered, source.nextActivation));
                            cancelPending();
                        }
                    });
                }

                return null;
            });
        } catch (RuntimeException failure) {
            fail(expected);
        }
    }

    private void fail(Pending expected) {
        coordinator.publish(expected.epoch, () -> {
            if (pending == expected) {
                clear();
                metrics.failed();
            }
        });
    }

    private void clear() {
        canonical = null;
        cancelPending();
        snapshots.invalidateAll();
    }

    private void cancelPending() {
        Pending previous = pending;
        pending = null;

        if (previous != null && previous.future != null) {
            previous.future.cancel(false);
        }
    }

    enum Publication {
        DEFERRED, IMMEDIATE
    }

    record Read<S>(S snapshot, boolean loadAllowed) {

        private static <S> Read<S> of(S snapshot, boolean loadAllowed) {
            return new Read<>(snapshot, loadAllowed);
        }
    }

    static final class Current<C> {

        private final long epoch;
        private final C value;
        private final Instant nextActivation;

        private Current(long epoch, C value, Instant nextActivation) {
            this.epoch = epoch;
            this.value = value;
            this.nextActivation = nextActivation;
        }

        C value() {
            return value;
        }
    }

    private record Published<S>(long epoch, S value, Instant nextActivation) {

        private static <S> Published<S> of(long epoch, S value, Instant nextActivation) {
            return new Published<>(epoch, value, nextActivation);
        }
    }

    private static final class Pending {

        private final long epoch;
        private final Instant firstDirty;
        private volatile ScheduledFuture<?> future;

        private Pending(long epoch, Instant firstDirty) {
            this.epoch = epoch;
            this.firstDirty = firstDirty;
        }
    }
}
