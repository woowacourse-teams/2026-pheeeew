package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.repository.EmotionMapReadRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.infra.metrics.MapCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PinCandidateCache implements EmotionMapCacheListener {

    private static final int MAX_CANDIDATES = 100_000;
    private final BatchedSnapshot<Canonical, Snapshot> publication;
    private final EmotionMapCacheCoordinator coordinator;
    private final EmotionMapReadRepository repository;
    private final Clock clock;
    private final MapCacheMetrics metrics;
    private final TransactionTemplate reads;
    private final MapCacheUpdateScheduler updates;
    private volatile Long exceededEpoch;

    public PinCandidateCache(EmotionMapCacheCoordinator coordinator, EmotionMapReadRepository repository, Clock clock,
            MeterRegistry meters, PlatformTransactionManager transactions, MapCacheUpdateScheduler updates) {
        this.coordinator = coordinator;
        this.repository = repository;
        this.clock = clock;
        this.metrics = new MapCacheMetrics(meters, "pins");
        this.updates = updates;

        reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        publication = new BatchedSnapshot<>(coordinator, updates, Snapshot::from, meters, "pins");

        metrics.candidates(this, source -> {
            BatchedSnapshot.Current<Canonical> current = source.publication.canonical();
            return current == null ? 0 : current.value().candidates().size();
        });
    }

    public Page find(EmotionCursor cursor, boolean firstPage, Long deviceId) {
        BatchedSnapshot.Read<Snapshot> read = publication.read();
        if (!read.loadAllowed()) {
            metrics.request("fallback");
            return null;
        }

        Snapshot observed = read.snapshot();
        Snapshot snapshot = observed == null ? snapshot() : observed;

        if (snapshot == null || snapshot.completeThrough().isAfter(Instant.now(clock))
                || !firstPage && snapshot.completeThrough().isBefore(cursor.snapshotAt())) {
            metrics.request("fallback");
            return null;
        }

        metrics.request(observed == null ? "miss" : "hit");
        EmotionCursor selected = firstPage
                ? EmotionCursor.initialWithinBounds(cursor.bounds(), snapshot.completeThrough(), cursor.filter()) : cursor;

        EmotionMapBlocks blocked = repository.findBlockedTargets(deviceId);

        PinCandidates.Selection selection = metrics.measurePinSearch(
                () -> snapshot.ordered().select(selected, blocked, 201), PinCandidates.Selection::scanned);

        return Page.of(selected, selection.pins());
    }

    @Override
    public void changed(EmotionMapContribution before, EmotionMapContribution after) {
        BatchedSnapshot.Current<Canonical> expected = publication.canonical();
        if (expected == null) {
            return;
        }

        Canonical current = expected.value();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        PinCandidates next = current.candidates().applyChange(before, after, now, MAX_CANDIDATES);
        if (next == null) {
            coordinator.publish(current.epoch(), () -> {
                if (publication.canonical() == expected) {
                    exceed(current.epoch());
                }
            });
            return;
        }

        Canonical replacement = Canonical.of(current.epoch(), now, next);

        publication.change(expected, replacement, next.nextActivation(), before == null
                ? BatchedSnapshot.Publication.DEFERRED : BatchedSnapshot.Publication.IMMEDIATE);
    }

    @Override
    public void invalidate() {
        publication.invalidate();
        exceededEpoch = null;
    }

    @Override
    public boolean rebuild() {
        if (!updates.available()) {
            return false;
        }

        Boolean rebuilt = coordinator.trySerialized(() -> {
            Long epoch = coordinator.read(value -> value);
            if (epoch == null) {
                return false;
            }

            try {
                Snapshot loaded = reads.execute(status -> measuredLoad(epoch));
                boolean published = coordinator.publish(epoch, () -> {
                    if (loaded == null) {
                        exceed(epoch);
                    } else {
                        exceededEpoch = null;
                        install(loaded);
                    }
                });
                if (published) {
                    metrics.rebuilt();
                }

                return published;
            } catch (RuntimeException failure) {
                coordinator.read(currentEpoch -> {
                    invalidate();
                    return Boolean.TRUE;
                });
                metrics.failed();
                return false;
            }
        });

        return Boolean.TRUE.equals(rebuilt);
    }

    private Snapshot snapshot() {
        BatchedSnapshot.Read<Snapshot> read = publication.read();
        if (!read.loadAllowed() || read.snapshot() != null) {
            return read.snapshot();
        }

        Long observed = coordinator.read(epoch -> epoch);
        if (observed == null || observed.equals(exceededEpoch)) {
            return null;
        }

        try {
            return coordinator.trySerialized(() -> {
                BatchedSnapshot.Read<Snapshot> latest = publication.read();
                if (!latest.loadAllowed()) {
                    return null;
                }

                Snapshot existing = latest.snapshot();
                if (existing != null) {
                    return existing;
                }

                Long epoch = coordinator.read(value -> value);
                if (epoch == null || epoch.equals(exceededEpoch)) {
                    return null;
                }

                Snapshot loaded = reads.execute(status -> measuredLoad(epoch));
                if (loaded == null) {
                    coordinator.publish(epoch, () -> exceed(epoch));
                    return null;
                }

                if (!install(loaded)) {
                    return null;
                }

                metrics.rebuilt();

                return loaded;
            });
        } catch (RuntimeException failure) {
            coordinator.read(epoch -> {
                invalidate();
                return Boolean.TRUE;
            });
            metrics.failed();
            return null;
        }
    }

    private void exceed(long epoch) {
        publication.invalidate();
        exceededEpoch = epoch;
    }

    private Snapshot measuredLoad(long epoch) {
        return metrics.measureRebuild(() -> load(epoch));
    }

    private boolean install(Snapshot snapshot) {
        return publication.install(snapshot.epoch(), Canonical.from(snapshot), snapshot, snapshot.nextActivation());
    }

    private Snapshot load(long epoch) {
        Instant at = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<EmotionMapPin> rows = repository.findPins(at, MAX_CANDIDATES + 1);

        if (rows.size() > MAX_CANDIDATES) {
            return null;
        }

        Instant nextActivation = repository.findNextPinActivation(at);

        return Snapshot.of(epoch, at, PinCandidates.of(rows, nextActivation));
    }

    public record Page(EmotionCursor cursor, List<EmotionMapPin> pins) {

        private static Page of(EmotionCursor cursor, List<EmotionMapPin> pins) {
            return new Page(cursor, pins);
        }
    }

    private record Snapshot(long epoch, Instant completeThrough, PinCandidates candidates, PinCandidates.Ordered ordered) {

        private static Snapshot of(long epoch, Instant completeThrough, PinCandidates candidates) {
            return from(Canonical.of(epoch, completeThrough, candidates));
        }

        private static Snapshot from(Canonical source) {
            return new Snapshot(source.epoch(), source.completeThrough(), source.candidates(), source.candidates().ordered());
        }

        private Instant nextActivation() {
            return candidates.nextActivation();
        }
    }

    private record Canonical(long epoch, Instant completeThrough, PinCandidates candidates) {

        private static Canonical of(long epoch, Instant completeThrough, PinCandidates candidates) {
            return new Canonical(epoch, completeThrough, candidates);
        }

        private static Canonical from(Snapshot snapshot) {
            return of(snapshot.epoch(), snapshot.completeThrough(), snapshot.candidates());
        }
    }
}
