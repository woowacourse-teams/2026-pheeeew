package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.report.application.EmotionReportService;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionMapPeriodCacheIntegrationTest {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private EmotionReportService reports;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private MapCacheTestTime.ManualScheduledExecutor tasks;

    private Device requester;
    private LocalDate start;
    private LocalDate end;
    private Long firstDay;
    private Long lastDay;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP")
                .update();
        requester = devices.save(기본_기기_빌더().build());
        end = LocalDate.ofInstant(clock.instant(), KOREA).minusDays(1);
        start = end.minusMonths(1);

        firstDay = saveAt(start.atStartOfDay(KOREA).toInstant(), EmotionState.ANGRY);
        lastDay = saveAt(end.plusDays(1).atStartOfDay(KOREA).toInstant().minusNanos(1_000), EmotionState.EXHAUSTED);
        saveAt(start.atStartOfDay(KOREA).toInstant().minusNanos(1_000), EmotionState.ANGRY);
        saveAt(end.plusDays(1).atStartOfDay(KOREA).toInstant(), EmotionState.ANGRY);
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
    void 기간_지역_조회는_KST_날짜_경계로_캐시에서_집계한다() {
        // given
        double cacheRequests = cacheRequests("regions");

        // when
        var result = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());

        // then
        assertThat(result).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(2));
        assertThat(cacheRequests("regions") - cacheRequests).isEqualTo(1);
    }

    @Test
    void 같은_기간을_다시_조회하면_집계_결과를_재사용하고_삭제_후에는_새로_계산한다() {
        // given
        var first = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());
        double reused = periodResultHits();

        // when
        var second = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());
        commands.delete(firstDay, requester.getPublicId());
        var deleted = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());

        // then
        assertThat(second).isEqualTo(first);
        assertThat(periodResultHits() - reused).isEqualTo(1);
        assertThat(deleted).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(1));
    }

    @Test
    void 등록은_배치_게시_후_오늘이_포함된_기간_요약과_핀에_반영된다() {
        // given
        LocalDate today = LocalDate.ofInstant(clock.instant(), KOREA);
        var untilToday = EmotionSearchFilter.of(start, today, null, null, null);
        var before = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), untilToday);
        var pinsBefore = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, today, null, null, null, 10);
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "오늘 등록", null, null, requester.getPublicId());

        // when
        clock.advance(Duration.ofSeconds(1));
        tasks.runDueTasks();
        var after = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), untilToday);
        var pins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, today, null, null, null, 10);

        // then
        assertThat(before).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(3));
        assertThat(after).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(4));
        assertThat(pinsBefore.items()).hasSize(3);
        assertThat(pins.items()).hasSize(4);
    }

    @Test
    void 내용을_제거하면_기간_요약과_핀에서_즉시_제외한다() {
        // given
        queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());
        queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end, null, null, null, 10);

        // when
        commands.update(firstDay, requester.getPublicId(), EmotionState.ANGRY, null, null, false, null);
        var regions = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());
        var pins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end, null, null, null, 10);

        // then
        assertThat(regions).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(1));
        assertThat(pins.items()).extracting(item -> item.id()).containsExactly(lastDay);
    }

    @Test
    void 신고_일괄_삭제가_커밋되면_이전_기간_결과를_재사용하지_않는다() {
        // given
        queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());
        queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end, null, null, null, 10);
        for (int index = 0; index < 5; index++) {
            var reporter = devices.save(기본_기기_빌더().build());
            reports.save(firstDay, reporter.getPublicId(), "신고");
        }

        // when
        int deleted = reports.deleteReportedOverThreshold();
        var regions = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), period());
        var pins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end, null, null, null, 10);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(regions).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(1));
        assertThat(pins.items()).extracting(item -> item.id()).containsExactly(lastDay);
    }

    @Test
    void 기간_핀_조회는_캐시_후보에서_기간_밖의_핀을_제외한다() {
        // given
        double cacheRequests = cacheRequests("pins");

        // when
        var page = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end, null, null, null, 10);

        // then
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(lastDay, firstDay);
        assertThat(page.hasNext()).isFalse();
        assertThat(cacheRequests("pins") - cacheRequests).isEqualTo(1);
    }

    @Test
    void 내용_필터의_기간_조회도_캐시에서_응답한다() {
        // given
        double regions = cacheRequests("regions");
        double pins = cacheRequests("pins");
        var memo = EmotionSearchFilter.of(start, end, EmotionContentFilter.MEMO, null, null);

        // when
        var regionResult = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), memo);
        var pinResult = queries.findContentMapWithinBoundsV4(
                BOUNDS, requester.getPublicId(), start, end, EmotionContentFilter.MEMO, null, null, 10);

        // then
        assertThat(regionResult).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(2));
        assertThat(pinResult.items()).extracting(item -> item.id()).containsExactly(lastDay, firstDay);
        assertThat(cacheRequests("regions") - regions).isEqualTo(1);
        assertThat(cacheRequests("pins") - pins).isEqualTo(1);
    }

    private EmotionSearchFilter period() {
        return EmotionSearchFilter.of(start, end, null, null, null);
    }

    private Long saveAt(Instant createdAt, EmotionState state) {
        Long id = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기간 기록").state(state).build()).getId();
        jdbc.sql("UPDATE emotions SET created_at = ? WHERE id = ?").params(Timestamp.from(createdAt), id).update();

        return id;
    }

    private double cacheRequests(String cache) {
        return meters.find("pheeeew.map.cache.requests").tag("cache", cache).counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    private double periodResultHits() {
        FunctionCounter counter = meters.find("cache.gets").tag("cache", "map_region_periods").tag("result", "hit")
                .functionCounter();
        return counter == null ? 0 : counter.count();
    }
}
