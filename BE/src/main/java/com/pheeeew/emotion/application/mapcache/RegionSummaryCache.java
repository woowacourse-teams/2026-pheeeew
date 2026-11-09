package com.pheeeew.emotion.application.mapcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.repository.EmotionMapReadRepository;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionStateCount;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.emotion.infra.metrics.MapCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

@Service
public class RegionSummaryCache implements EmotionMapCacheListener {

    /** 내용 종류(ALL·MEMO·AUDIO)마다 기존 64개를 둔다. */
    private static final int MAX_PERIOD_RESULTS = 64 * 3;
    private final BatchedSnapshot<Canonical, Snapshot> publication;
    private final EmotionMapCacheCoordinator coordinator;
    private final EmotionMapReadRepository repository;
    private final Clock clock;
    private final MapCacheMetrics metrics;
    private final TransactionTemplate reads;
    private final MapCacheUpdateScheduler updates;
    private final PinCandidateCache pins;
    private final AtomicLong versions = new AtomicLong();
    /** 기간 요약 결과. 키에 스냅샷 버전이 있어 집계가 바뀌면 이전 결과를 다시 쓰지 않는다. */
    private final Cache<PeriodKey, Map<RegionLevel, Map<String, RegionEmotionSummary>>> periods = Caffeine.newBuilder()
            .maximumSize(MAX_PERIOD_RESULTS).recordStats().build();

    public RegionSummaryCache(EmotionMapCacheCoordinator coordinator, EmotionMapReadRepository repository, Clock clock,
            MeterRegistry meters, PlatformTransactionManager transactions, MapCacheUpdateScheduler updates,
            PinCandidateCache pins) {
        this.coordinator = coordinator;
        this.repository = repository;
        this.clock = clock;
        this.metrics = new MapCacheMetrics(meters, "regions");
        this.updates = updates;
        this.pins = pins;

        this.reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        publication = new BatchedSnapshot<>(coordinator, updates,
                value -> snapshot(value.epoch(), value.aggregate()), meters, "regions");
        CaffeineCacheMetrics.monitor(meters, periods, "map_region_periods");
    }

    /**
     * 기간이 없으면 전체 기간, 있으면 두 날짜를 포함하는 KST 기간의 요약을 반환한다. 캐시를 쓸 수 없으면 null이다.
     * MY는 기기별 집계를 보관하지 않고 핀 후보에서 계산한다.
     */
    public Map<String, RegionEmotionSummary> summaries(RegionLevel level, EmotionSearchFilter filter, Long deviceId) {
        if (filter.scope() == EmotionSearchScope.MY) {
            PinCandidateCache.DeviceRegions mine = pins.regionSummaries(deviceId, filter);
            metrics.request(mine == null ? "fallback" : mine.loaded() ? "miss" : "hit");
            return mine == null ? null : mine.summaries().get(level);
        }

        EmotionContentFilter contentType = filter.contentType();
        LocalDate startDate = filter.startDate();
        LocalDate endDate = filter.endDate();
        BatchedSnapshot.Read<Snapshot> read = publication.read();
        if (!read.loadAllowed()) {
            metrics.request("fallback");
            return null;
        }

        Snapshot hit = read.snapshot();
        if (hit != null) {
            metrics.request("hit");
            return summariesOf(hit, contentType, startDate, endDate).get(level);
        }

        Snapshot loaded = loadOrFallback();

        return loaded == null ? null : summariesOf(loaded, contentType, startDate, endDate).get(level);
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
        periods.invalidateAll();
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

    private Map<RegionLevel, Map<String, RegionEmotionSummary>> summariesOf(Snapshot snapshot,
            EmotionContentFilter contentType, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && contentType == EmotionContentFilter.ALL) {
            return snapshot.summaries();
        }

        return periods.get(PeriodKey.of(snapshot.version(), contentType, startDate, endDate),
                key -> key.startDate() == null ? snapshot.aggregate().summaries(key.contentType())
                        : snapshot.aggregate().summaries(key.contentType(), key.startDate(), key.endDate()));
    }

    private Snapshot loadOrFallback() {
        Long attemptedEpoch = coordinator.read(value -> value);

        try {
            Snapshot result = coordinator.trySerialized(this::findOrLoadSnapshot);

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
    private Snapshot findOrLoadSnapshot() {
        BatchedSnapshot.Read<Snapshot> latest = publication.read();
        if (!latest.loadAllowed()) {
            return null;
        }

        Snapshot existing = latest.snapshot();
        if (existing != null) {
            metrics.request("hit");
            return existing;
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

        return loaded;
    }

    private Snapshot load(long epoch) {
        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        List<RegionEmotionStateCount> rows = repository.findRegionStateCounts(snapshotAt);

        return snapshot(epoch, RegionAggregate.from(rows));
    }

    private Snapshot snapshot(long epoch, RegionAggregate aggregate) {
        return Snapshot.of(epoch, versions.incrementAndGet(), aggregate, aggregate.summaries(EmotionContentFilter.ALL));
    }

    private record Canonical(long epoch, RegionAggregate aggregate) {

        private static Canonical of(long epoch, RegionAggregate aggregate) {
            return new Canonical(epoch, aggregate);
        }
    }

    private record Snapshot(long epoch, long version, RegionAggregate aggregate,
                            Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries) {

        private static Snapshot of(long epoch, long version, RegionAggregate aggregate,
                Map<RegionLevel, Map<String, RegionEmotionSummary>> summaries) {
            return new Snapshot(epoch, version, aggregate, summaries);
        }
    }

    /** startDate가 null이면 전체 기간이다. */
    private record PeriodKey(long version, EmotionContentFilter contentType, LocalDate startDate, LocalDate endDate) {

        private static PeriodKey of(long version, EmotionContentFilter contentType, LocalDate startDate,
                LocalDate endDate) {
            return new PeriodKey(version, contentType, startDate, endDate);
        }
    }
}
