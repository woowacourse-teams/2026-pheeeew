package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.application.mapcache.EmotionMapCacheCoordinator;
import com.pheeeew.emotion.application.mapcache.RegionSummaryCache;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import io.micrometer.core.instrument.MeterRegistry;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionRegionBatchCacheIntegrationTest {

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private MapCacheTestTime.ManualScheduledExecutor tasks;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private EmotionMapCacheCoordinator coordinator;
    @Autowired
    private RegionSummaryCache regionCache;

    private Device requester;
    private Long emotionId;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP").update();
        requester = devices.save(기본_기기_빌더().build());
        emotionId = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기본 기록").state(EmotionState.ANGRY).build()).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM emotion_reports").update();
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Test
    void 최초_재생성과_캐시_재사용은_각각_miss와_hit만_한_번_기록한다() {
        // given / when
        var first = regions();

        // then
        assertThat(first.getFirst().summary().totalCount()).isEqualTo(1);
        assertRequests(0, 1, 0);

        // when
        var second = regions();

        // then
        assertThat(second).isEqualTo(first);
        assertRequests(1, 1, 0);
    }

    @Test
    void 쓰기_잠금_경합으로_SQL을_선택하면_fallback만_한_번_기록한다() throws Exception {
        // given
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> coordinator.serialized(() -> {
            entered.countDown();
            await(release);
            return null;
        }));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            var result = regions();

            // then
            assertThat(result.getFirst().summary().totalCount()).isEqualTo(1);
            assertRequests(0, 0, 1);
        } finally {
            release.countDown();
            writer.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void 캐시_재생성_트랜잭션이_실패해도_SQL로_응답하고_fallback만_한_번_기록한다() {
        // given: 캐시 전용 트랜잭션의 시작 실패를 주입하고 SQL 대체 트랜잭션은 유지한다.
        TransactionTemplate cacheReads = (TransactionTemplate) ReflectionTestUtils.getField(regionCache, "reads");
        cacheReads.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);

        // when
        var result = regions();

        // then
        assertThat(result.getFirst().summary().totalCount()).isEqualTo(1);
        assertRequests(0, 0, 1);
        assertThat(meters.get("pheeeew.map.cache.failures").tag("cache", "regions").counter().count())
                .isEqualTo(1);
    }

    @Test
    void 등록은_DB에_커밋하고_조회용_집계는_첫_변경부터_1초_후_게시한다() {
        // given
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);

        // when
        register();

        // then
        assertThat(queries.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.SIDO,
                requester.getPublicId(), EmotionSearchFilter.of(null, null, null, EmotionSearchScope.MY, null))
                .getFirst().summary().totalCount()).isEqualTo(2);
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(1));
        tasks.runDueTasks();
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(2);
    }

    @Test
    void 게시_작업이_지연되면_첫_등록부터_3초에_SQL로_응답한다() {
        // given
        regions();
        register();
        double rebuilt = meters.get("pheeeew.map.cache.rebuilds").tag("cache", "regions").counter().count();

        // when
        clock.advance(Duration.ofSeconds(3));

        // then
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(2);
        assertThat(meters.get("pheeeew.map.cache.rebuilds").tag("cache", "regions").counter().count())
                .isEqualTo(rebuilt);
        assertRequests(0, 1, 1);
    }

    @Test
    void 실행기가_종료되면_기존_캐시도_사용하거나_재생성하지_않고_SQL로_응답한다() {
        // given
        regions();
        regions();
        double hits = meters.get("pheeeew.map.cache.requests").tags("cache", "regions", "result", "hit").counter().count();
        double rebuilt = meters.get("pheeeew.map.cache.rebuilds").tag("cache", "regions").counter().count();

        // when
        tasks.shutdownNow();
        regions();
        regions();

        // then
        assertThat(meters.get("pheeeew.map.cache.requests").tags("cache", "regions", "result", "hit").counter().count())
                .isEqualTo(hits);
        assertThat(meters.get("pheeeew.map.cache.rebuilds").tag("cache", "regions").counter().count())
                .isEqualTo(rebuilt);
        assertRequests(1, 1, 2);
    }

    @Test
    void 등록이_계속되어도_첫_등록의_게시_시각을_미루지_않는다() {
        // given
        regions();
        register();
        clock.advance(Duration.ofMillis(600));
        register();
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);

        // when
        clock.advance(Duration.ofMillis(400));
        tasks.runDueTasks();

        // then
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(3);
    }

    @Test
    void 삭제는_등록_대기분까지_즉시_게시하고_과거_작업이_삭제를_되돌리지_않는다() {
        // given
        regions();
        register();

        // when
        commands.delete(emotionId, requester.getPublicId());

        // then
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        assertThat(regions().getFirst().summary().representativeState()).isEqualTo(EmotionState.DISCOURAGED);
        clock.advance(Duration.ofSeconds(1));
        tasks.forcePreviouslySubmittedTasks();
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        assertThat(regions().getFirst().summary().representativeState()).isEqualTo(EmotionState.DISCOURAGED);
    }

    @Test
    void 수정은_등록_대기분을_포함한_최신_집계를_즉시_게시한다() {
        // given
        regions();
        register();

        // when
        commands.update(emotionId, requester.getPublicId(), EmotionState.ANGRY, null, null, false, null);

        // then
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        assertThat(regions().getFirst().summary().representativeState()).isEqualTo(EmotionState.DISCOURAGED);
    }

    @Test
    void 작업_예약이_거부되어도_DB_등록은_성공하고_오래된_집계를_반환하지_않는다() {
        // given
        regions();
        tasks.rejectNewTasks();

        // when
        register();

        // then
        assertThat(meters.get("pheeeew.map.cache.failures").tag("cache", "regions").counter().count()).isEqualTo(1);
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(2);
    }

    @Test
    void 따뜻한_집계에_추가된_미래_감정도_시각이_도달하면_포함한다() {
        // given: 감사 시각과 조회 시계의 차이로 미래 생성시각이 유입되는 상황이다.
        regions();
        commands.delete(emotionId, requester.getPublicId());
        clock.advance(Duration.ofSeconds(-120));
        register();
        clock.advance(Duration.ofSeconds(1));
        tasks.runDueTasks();
        assertThat(regions()).isEmpty();

        // when
        clock.advance(Duration.ofSeconds(119));

        // then
        assertThat(regions()).singleElement().satisfies(item ->
                assertThat(item.summary().totalCount()).isEqualTo(1));
    }

    private void assertRequests(double hits, double misses, double fallbacks) {
        assertThat(requests("hit")).isEqualTo(hits);
        assertThat(requests("miss")).isEqualTo(misses);
        assertThat(requests("fallback")).isEqualTo(fallbacks);
    }

    private double requests(String result) {
        var counter = meters.find("pheeeew.map.cache.requests")
                .tags("cache", "regions", "result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("쓰기 잠금 해제 대기 시간이 지났습니다.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void register() {
        commands.save(UUID.randomUUID(), EmotionState.DISCOURAGED, 127, 38, 0,
                "새 기록", null, null, requester.getPublicId());
    }

    private List<EmotionRegionMapItemView> regions() {
        return queries.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.SIDO,
                requester.getPublicId(), EmotionSearchFilter.from(null));
    }
}
