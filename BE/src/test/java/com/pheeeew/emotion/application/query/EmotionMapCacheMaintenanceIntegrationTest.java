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
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Duration;
import java.util.List;
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

@org.springframework.test.context.TestPropertySource(properties = "pheeeew.map-cache.maintenance.automatic-start=true")
@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionMapCacheMaintenanceIntegrationTest {

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private com.pheeeew.report.application.EmotionReportService reports;
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

    private Device requester;
    private Long emotionId;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP").update();
        requester = devices.save(기본_기기_빌더().build());
        emotionId = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기본 기록").state(EmotionState.ANGRY).build()).getId();
        tasks.runDueTasks();
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM emotion_reports").update();
        emotions.deleteAllInBatch();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Autowired
    private org.springframework.context.ConfigurableApplicationContext context;
    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meters;

    @Test
    void 시작할_때_미리_생성하고_10분마다_DB와_보정한다() {
        // given
        context.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], context, Duration.ZERO));
        tasks.runDueTasks();
        double hits = hits();
        double pinHits = pinHits();
        assertThat(pins().items()).singleElement().satisfies(pin -> assertThat(pin.id()).isEqualTo(emotionId));
        assertThat(pinHits() - pinHits).isEqualTo(1);
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        assertThat(hits() - hits).isEqualTo(1);
        jdbc.sql("UPDATE emotions SET memo = NULL WHERE id = :id").param("id", emotionId).update();
        assertThat(regions().getFirst().summary().totalCount()).isEqualTo(1);
        assertThat(pins().items()).hasSize(1);

        // when
        clock.advance(Duration.ofMinutes(10));
        tasks.runDueTasks();
        hits = hits();
        pinHits = pinHits();

        // then
        assertThat(regions()).isEmpty();
        assertThat(hits() - hits).isEqualTo(1);
        assertThat(pins().items()).isEmpty();
        assertThat(pinHits() - pinHits).isEqualTo(1);
    }

    @Test
    void 신고_일괄_삭제_후_조회가_오기_전에_재생성한다() {
        // given
        regions();
        assertThat(pins().items()).hasSize(1);
        for (int index = 0; index < 5; index++) {
            var reporter = devices.save(기본_기기_빌더().build());
            reports.save(emotionId, reporter.getPublicId(), "신고");
        }

        // when
        assertThat(reports.deleteReportedOverThreshold()).isEqualTo(1);
        tasks.runDueTasks();
        double hits = hits();
        double pinHits = pinHits();

        // then
        assertThat(regions()).isEmpty();
        assertThat(hits() - hits).isEqualTo(1);
        assertThat(pins().items()).isEmpty();
        assertThat(pinHits() - pinHits).isEqualTo(1);
    }

    private double pinHits() {
        var counter = meters.find("pheeeew.map.cache.requests").tags("cache", "pins", "result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }

    private EmotionMapPageView pins() {
        return queries.findContentMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 39), requester.getPublicId(),
                null, null, null, null, null, null);
    }

    private double hits() {
        var counter = meters.find("pheeeew.map.cache.requests").tags("cache", "regions", "result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }

    private List<EmotionRegionMapItemView> regions() {
        return queries.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.SIDO,
                requester.getPublicId(), EmotionSearchFilter.from(null));
    }
}
