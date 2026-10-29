package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.repository.EmotionMapReadRepository;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.emotion.infra.metrics.MapCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

@Service
public class RegionSummaryCache implements EmotionMapCacheListener {

    private final BatchedSnapshot<Canonical, Snapshot> publication;
    private final EmotionMapCacheCoordinator coordinator;
    private final EmotionMapReadRepository repository;
    private final Clock clock;
    private final MapCacheMetrics metrics;
    private final TransactionTemplate reads;
    private final MapCacheUpdateScheduler updates;

    public RegionSummaryCache(EmotionMapCacheCoordinator coordinator, EmotionMapReadRepository repository, Clock clock,
            MeterRegistry meters, PlatformTransactionManager transactions, MapCacheUpdateScheduler updates) {
        this.coordinator = coordinator;
        this.repository = repository;
        this.clock = clock;
        this.metrics = new MapCacheMetrics(meters, "regions");
        this.updates = updates;

        this.reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        publication = new BatchedSnapshot<>(coordinator, updates,
                value -> snapshot(value.epoch(), value.aggregate()), meters, "regions");
    }

    public Map<String, RegionEmotionSummary> summaries(RegionLevel level) {
        BatchedSnapshot.Read<Snapshot> read = publication.read();
        if (!read.loadAllowed()) {
            metrics.request("fallback");
            return null;
        }

        Snapshot hit = read.snapshot();
        if (hit != null) {
            metrics.request("hit");
            return hit.summaries().get(level);
        }

        return loadSummaries(level);
    }

    @Override
    public void changed(EmotionMapContribution before, EmotionMapContribution after) {
        BatchedSnapshot.Current<Canonical> expected = publication.canonical();
        if (expected == null) {
            return;
        }

        Canonical current = expected.value();
        RegionAggregate next = current.aggregate().applyChange(before, after, Instant.now(clock));
        Canonical replacement = Canonical.of(current.epoch(), next);

        publication.change(expected, replacement, next.nextActivation(), before == null && after != null
                ? BatchedSnapshot.Publication.DEFERRED : BatchedSnapshot.Publication.IMMEDIATE);
    }

    @Override
    public void invalidate() {
        publication.invalidate();
    }

    @Override
    public boolean rebuild() {
        return coordinator.serialized(() -> {
            if (!updates.available()) {
                return false;
            }

            Long epoch = coordinator.read(value -> value);
            if (epoch == null) {
                return false;
            }

            try {
                Snapshot loaded = loadSnapshot(epoch);
                boolean published = loaded != null && install(loaded);
                if (published) {
                    metrics.rebuilt();
                }

                return published;
            } catch (RuntimeException failure) {
                coordinator.publish(epoch, this::invalidate);
                metrics.failed();
                return false;
            }
        });
    }

    private Map<String, RegionEmotionSummary> loadSummaries(RegionLevel level) {
        Long attemptedEpoch = coordinator.read(value -> value);

        try {
            Map<String, RegionEmotionSummary> result = coordinator.trySerialized(() -> findOrLoadSummaries(level));

            if (result == null) {
                metrics.request("fallback");
            }

            return result;
        } catch (RuntimeException failure) {
            if (attemptedEpoch != null) {
                coordinator.publish(attemptedEpoch, this::invalidate);
            }

            metrics.failed();
            metrics.request("fallback");
            return null;
        }
    }

    private Snapshot loadSnapshot(long epoch) {
        return metrics.measureRebuild(() -> reads.execute(status -> load(epoch)));
    }

    private boolean install(Snapshot snapshot) {
        Canonical canonical = Canonical.of(snapshot.epoch(), snapshot.aggregate());

        return publication.install(snapshot.epoch(), canonical, snapshot, snapshot.aggregate().nextActivation());
    }

    /** writer 안에서 상태를 다시 확인한 뒤 필요한 기준선만 생성한다. */
    private Map<String, RegionEmotionSummary> findOrLoadSummaries(RegionLevel level) {
        BatchedSnapshot.Read<Snapshot> latest = publication.read();
        if (!latest.loadAllowed()) {
            return null;
        }

        Snapshot existing = latest.snapshot();
        if (existing != null) {
            metrics.request("hit");
            return existing.summaries().get(level);
        }

        Long epoch = coordinator.read(value -> value);
        if (epoch == null) {
            return null;
        }

        Snapshot loaded = loadSnapshot(epoch);
        if (loaded == null || !install(loaded)) {
            return null;
        }

        metrics.rebuilt();
        metrics.request("miss");

        return loaded.summaries().get(level);
    }

    private Snapshot load(long epoch) {
        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<RegionEmotionStateCount> rows = repository.findRegionStateCounts(snapshotAt);

        return snapshot(epoch, RegionAggregate.from(rows));
    }

    private Snapshot snapshot(long epoch, RegionAggregate aggregate) {
        return Snapshot.of(epoch, aggregate, aggregate.summaries());
    }

    private record Canonical(long epoch, RegionAggregate aggregate) {

        private static Canonical of(long epoch, RegionAggregate aggregate) {
            return new Canonical(epoch, aggregate);
        }
    }

    private record Snapshot(long epoch, RegionAggregate aggregate,
                            Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries) {

        private static Snapshot of(long epoch, RegionAggregate aggregate,
                Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries) {
            return new Snapshot(epoch, aggregate, summaries);
        }
    }
}
