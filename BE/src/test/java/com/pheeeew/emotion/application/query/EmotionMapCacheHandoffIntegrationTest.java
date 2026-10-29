package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.TestPropertySource;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.mapcache.MapCacheMaintenance;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(properties = "pheeeew.emotion-map.max-page-size=1000")
@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionMapCacheHandoffIntegrationTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private MapCacheMaintenance maintenance;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private MapCacheTestTime.ManualScheduledExecutor tasks;

    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private EmotionBlockRepository emotionBlocks;

    private Device requester;
    private Long originalId;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP").update();
        requester = devices.save(기본_기기_빌더().build());
        originalId = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기존 기록")
                .state(EmotionState.ANGRY).build()).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM emotion_reports").update();
        jdbc.sql("DELETE FROM emotion_blocks").update();
        jdbc.sql("DELETE FROM device_blocks").update();
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Test
    void DB_기준선_조회_중_등록과_삭제가_대기하고_완료_후_두_캐시에_정확히_인계된다() throws Exception {
        // given
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        assertThat(pins().items()).singleElement().satisfies(pin -> assertThat(pin.id()).isEqualTo(originalId));
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture<Void> rebuilding;
            CompletableFuture<Void> deleting;
            CompletableFuture<Emotion> registering;
            try (Connection locker = dataSource.getConnection()) {
                locker.setAutoCommit(false);
                try {
                    int lockerPid;
                    try (var statement = locker.createStatement()) {
                        statement.execute("LOCK TABLE emotions IN ACCESS EXCLUSIVE MODE");
                        try (var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                            result.next();
                            lockerPid = result.getInt(1);
                        }
                    }
                    maintenance.start();
                    rebuilding = CompletableFuture.runAsync(tasks::runDueTasks, workers);
                    awaitBaselineSelectBlockedBy(lockerPid);

                    // when
                    CountDownLatch writesStarted = new CountDownLatch(2);
                    deleting = CompletableFuture.runAsync(() -> {
                        writesStarted.countDown();
                        commands.delete(originalId, requester.getPublicId());
                    }, workers);
                    registering = CompletableFuture.supplyAsync(() -> {
                        writesStarted.countDown();
                        return commands.save(UUID.randomUUID(), EmotionState.DISCOURAGED, 127, 38, 0,
                                "기준선 이후 등록", null, null, requester.getPublicId());
                    }, workers);
                    assertThat(writesStarted.await(5, TimeUnit.SECONDS)).isTrue();

                    // then: DB 기준선이 막혀 있어도 이미 게시한 집계 조회는 진행한다.
                    double regionHits = hits("regions");
                    var warm = CompletableFuture.supplyAsync(this::regions, workers).get(5, TimeUnit.SECONDS);
                    assertThat(warm.getFirst().summary().totalCount()).isEqualTo(1);
                    assertThat(hits("regions") - regionHits).isEqualTo(1);
                    assertThat(rebuilding).isNotDone();
                    assertThat(deleting).isNotDone();
                    assertThat(registering).isNotDone();
                } finally {
                    locker.rollback();
                }
            }
            rebuilding.get(5, TimeUnit.SECONDS);
            deleting.get(5, TimeUnit.SECONDS);
            Emotion added = registering.get(5, TimeUnit.SECONDS);
            clock.advance(Duration.ofSeconds(1));
            tasks.runDueTasks();
            double regionHits = hits("regions");
            double pinHits = hits("pins");

            assertThat(regions()).singleElement().satisfies(region -> {
                assertThat(region.summary().totalCount()).isEqualTo(1);
                assertThat(region.summary().representativeState()).isEqualTo(EmotionState.DISCOURAGED);
            });
            assertThat(pins().items()).singleElement().satisfies(pin -> assertThat(pin.id()).isEqualTo(added.getId()));
            assertThat(hits("regions") - regionHits).isEqualTo(1);
            assertThat(hits("pins") - pinHits).isEqualTo(1);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,1", "true,1", "false,200", "true,200", "false,400", "true,400", "false,1000", "true,1000"})
    void v4_핀은_캐시와_SQL을_오가며_개수를_바꿔도_커서와_차단을_유지한다(boolean cacheFirst, int pageSize) {
        // given
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < pageSize + 1; index++) {
            expected.add(emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("페이지 기록")
                    .state(EmotionState.ANGRY).build()).getId());
        }
        if (cacheFirst) {
            maintenance.requestRebuild();
            tasks.runDueTasks();
        }
        double before = hits("pins");

        // when: 첫 페이지와 다음 페이지의 조회 원천을 바꾼다.
        var first = cacheFirst
                ? queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), null, null, null, null, null, pageSize)
                : new TransactionTemplate(transactions).execute(status ->
                        queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), null, null, null, null, null, pageSize));
        assertThat(hits("pins") - before).isEqualTo(cacheFirst ? 1 : 0);
        emotionBlocks.save(EmotionBlock.builder().blockerDeviceId(requester.getId()).emotionId(originalId).build());
        if (cacheFirst) {
            tasks.shutdown();
        } else {
            maintenance.requestRebuild();
            tasks.runDueTasks();
        }
        before = hits("pins");
        double fallbackBefore = pinFallbacks();
        var next = queries.findContentMapWithinBoundsV4(null, requester.getPublicId(), null, null, null, null, first.nextCursor(), 1);

        // then: 오래된 originalId는 새 차단으로 제외하고 나머지는 누락·중복 없이 이어진다.
        assertThat(hits("pins") - before).isEqualTo(cacheFirst ? 0 : 1);
        assertThat(pinFallbacks() - fallbackBefore).isEqualTo(cacheFirst ? 1 : 0);
        assertThat(first.items()).hasSize(pageSize);
        assertThat(first.hasNext()).isTrue();
        assertThat(next.items()).hasSize(1);
        assertThat(EmotionCursorCodec.decodeV4WithinBounds(first.nextCursor()).groupIds()).isEmpty();
        assertThat(Stream.concat(first.items().stream(), next.items().stream()).map(EmotionMapItemView::id).toList())
                .containsExactlyElementsOf(expected.reversed());
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 요청_개수와_일치하면_마지막_페이지이고_생략과_기존_API는_200개를_유지한다(boolean sql) {
        // given
        for (int index = 0; index < 399; index++) {
            emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("경계 기록")
                    .state(EmotionState.ANGRY).build());
        }
        maintenance.requestRebuild();
        tasks.runDueTasks();
        Runnable assertions = () -> {
            // when
            var exact = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(),
                    null, null, null, null, null, 400);
            var omitted = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(),
                    null, null, null, null, null);
            var legacyContent = pins();
            var legacy = queries.findMapWithinBounds(BOUNDS, requester.getPublicId(), null, null);

            // then
            assertThat(exact.items()).hasSize(400);
            assertThat(exact.hasNext()).isFalse();
            assertThat(exact.nextCursor()).isNull();
            for (var page : List.of(omitted, legacyContent, legacy)) {
                assertThat(page.items()).hasSize(200);
                assertThat(page.hasNext()).isTrue();
                assertThat(page.nextCursor()).isNotNull();
            }
            var next = queries.findContentMapWithinBoundsV4(null, requester.getPublicId(),
                    null, null, null, null, omitted.nextCursor(), null);
            assertThat(next.items()).hasSize(200);
            assertThat(next.hasNext()).isFalse();
            assertThat(next.nextCursor()).isNull();
            assertThat(Stream.concat(omitted.items().stream(), next.items().stream())
                    .map(EmotionMapItemView::id).distinct().count()).isEqualTo(400);
        };

        // when / then
        if (sql) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> assertions.run());
        } else {
            assertions.run();
        }
    }

    private void awaitBaselineSelectBlockedBy(int lockerPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            long blocked = jdbc.sql("""
                    SELECT count(*) FROM pg_stat_activity activity
                    WHERE activity.wait_event_type = 'Lock'
                      AND :locker = ANY(pg_blocking_pids(activity.pid))
                      AND lower(activity.query) LIKE '%from emotions%'
                    """).param("locker", lockerPid).query(Long.class).single();
            if (blocked > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("5초 안에 기준선 SELECT의 실제 PostgreSQL 테이블 잠금 대기를 확인하지 못했습니다.");
    }

    private double pinFallbacks() {
        var counter = meters.find("pheeeew.map.cache.requests").tags("cache", "pins", "result", "fallback").counter();
        return counter == null ? 0 : counter.count();
    }

    private double hits(String cache) {
        var counter = meters.find("pheeeew.map.cache.requests").tags("cache", cache, "result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }

    private List<EmotionRegionMapItemView> regions() {
        return queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
    }

    private EmotionMapPageView pins() {
        return queries.findContentMapWithinBounds(BOUNDS, requester.getPublicId(), null, null, null, null, null, null);
    }
}
