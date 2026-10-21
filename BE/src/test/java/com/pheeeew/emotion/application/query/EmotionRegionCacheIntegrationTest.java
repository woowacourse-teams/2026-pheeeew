package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.report.application.EmotionReportService;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionRegionCacheIntegrationTest {

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private EmotionReportService reports;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private MapCacheTestTime.ManualScheduledExecutor tasks;

    private Device requester;
    private Long emotionId;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP")
                .update();
        requester = devices.save(기본_기기_빌더().build());
        emotionId = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기본 기록").state(EmotionState.ANGRY).build()).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM emotion_reports").update();
        emotions.deleteAllInBatch();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Test
    void 기본_전국_지역_조회는_완성된_캐시를_재사용한다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        var first = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
        double hits = cacheHits();

        // when
        var second = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));

        // then
        assertThat(second).isEqualTo(first).singleElement().satisfies(item ->
                assertThat(item.summary().totalCount()).isEqualTo(1));
        assertThat(cacheHits() - hits).isEqualTo(1);
    }

    @Test
    void 일반_삭제가_성공하면_기존_스냅샷의_기여분만_제거한다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
        double hits = cacheHits();

        // when
        commands.delete(emotionId, requester.getPublicId());
        var result = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));

        // then
        assertThat(result).isEmpty();
        assertThat(cacheHits() - hits).isEqualTo(1);
    }

    @Test
    void 등록이_커밋되면_기여분을_더하고_등록_재시도는_다시_더하지_않는다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        queries.findRegionMap(bounds, RegionLevel.SIGUNGU, requester.getPublicId(), EmotionSearchFilter.from(null));
        UUID requestId = UUID.randomUUID();

        // when
        commands.save(requestId, EmotionState.DISCOURAGED, 127, 38, 0, "새 기록", null, null, requester.getPublicId());
        commands.save(requestId, EmotionState.DISCOURAGED, 127, 38, 0, "새 기록", null, null, requester.getPublicId());
        clock.advance(Duration.ofSeconds(1));
        tasks.runDueTasks();
        var result = queries.findRegionMap(bounds, RegionLevel.SIGUNGU, requester.getPublicId(), EmotionSearchFilter.from(null));

        // then
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.summary().totalCount()).isEqualTo(2);
            assertThat(item.summary().representativeState()).isEqualTo(EmotionState.ANGRY);
        });
    }

    @Test
    void 수정이_성공하면_이전_상태를_빼고_새_상태의_대표_감정으로_교체한다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));

        // when
        commands.update(emotionId, requester.getPublicId(), EmotionState.DISCOURAGED, "바뀐 기록", null, false, null);
        var result = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));

        // then
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.summary().totalCount()).isEqualTo(1);
            assertThat(item.summary().representativeState()).isEqualTo(EmotionState.DISCOURAGED);
        });
    }

    @Test
    void 신고_일괄_삭제가_커밋되면_이전_전국_집계를_재사용하지_않는다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
        for (int index = 0; index < 5; index++) {
            var reporter = devices.save(기본_기기_빌더().build());
            reports.save(emotionId, reporter.getPublicId(), "신고");
        }

        // when
        int deleted = reports.deleteReportedOverThreshold();
        var result = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(result).isEmpty();
    }

    @Test
    void 외부_트랜잭션의_미커밋_수정은_캐시에_게시하지_않고_롤백을_보존한다() {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
        double hits = cacheHits();

        // when
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            commands.update(emotionId, requester.getPublicId(), EmotionState.DISCOURAGED, "롤백될 기록", null, false, null);
            var own = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
            assertThat(own.getFirst().summary().representativeState()).isEqualTo(EmotionState.DISCOURAGED);
            var other = CompletableFuture.supplyAsync(() -> queries.findRegionMap(
                    bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null)))
                    .orTimeout(5, TimeUnit.SECONDS).join();
            assertThat(other.getFirst().summary().representativeState()).isEqualTo(EmotionState.ANGRY);
            assertThat(cacheHits()).isEqualTo(hits);
            status.setRollbackOnly();
        });

        // then
        var restored = queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
        assertThat(restored.getFirst().summary().representativeState()).isEqualTo(EmotionState.ANGRY);
    }

    @Test
    void 신고_일괄_삭제_전에_읽은_외부_수정은_삭제된_감정을_되살리지_않는다() throws Exception {
        // given
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);
        queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null));
        for (int index = 0; index < 5; index++) {
            var reporter = devices.save(기본_기기_빌더().build());
            reports.save(emotionId, reporter.getPublicId(), "신고");
        }
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);
        CompletableFuture<Void> pendingUpdate = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    commands.update(emotionId, requester.getPublicId(), EmotionState.DISCOURAGED, "뒤늦은 수정", null, false, null);
                    updated.countDown();
                    await(deleted);
                }));

        // when
        assertThat(updated.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(reports.deleteReportedOverThreshold()).isEqualTo(1);
        } finally {
            deleted.countDown();
        }

        // then
        assertThatThrownBy(() -> pendingUpdate.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(OptimisticLockingFailureException.class);
        assertThat(queries.findRegionMap(bounds, RegionLevel.SIDO, requester.getPublicId(), EmotionSearchFilter.from(null))).isEmpty();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 검증 경계 대기 시간이 지났습니다.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private double cacheHits() {
        Counter counter = meters.find("pheeeew.map.cache.requests").tag("cache", "regions").tag("result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }
}
