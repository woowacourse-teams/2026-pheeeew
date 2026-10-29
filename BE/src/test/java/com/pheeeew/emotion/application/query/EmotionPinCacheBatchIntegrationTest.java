package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import java.util.UUID;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
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

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionPinCacheBatchIntegrationTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

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

    private Device requester;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP").update();
        requester = devices.save(기본_기기_빌더().build());
        emotion = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기본 핀").state(EmotionState.ANGRY).build());
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM emotion_blocks").update();
        jdbc.sql("DELETE FROM device_blocks").update();
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private MapCacheTestTime.ManualScheduledExecutor executor;

    @Test
    void 등록은_첫_변경부터_1초_동안_모아_공개한다() {
        // given
        page(null);

        // when
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "첫 등록", null, null, requester.getPublicId());
        assertThat(page(null).items()).hasSize(1);
        clock.advance(java.time.Duration.ofMillis(900));
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "둘째 등록", null, null, requester.getPublicId());
        executor.runDueTasks();
        assertThat(page(null).items()).hasSize(1);
        clock.advance(java.time.Duration.ofMillis(100));
        executor.runDueTasks();

        // then
        assertThat(page(null).items()).hasSize(3);
    }

    @Test
    void 공개가_3초_지연되면_SQL로_응답하고_다음_요청은_완성된_기준선을_복구한다() {
        // given
        page(null);
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "지연 등록", null, null, requester.getPublicId());
        clock.advance(java.time.Duration.ofSeconds(3));

        // when
        EmotionMapPageView fallback = page(null);
        double misses = meters.find("pheeeew.map.cache.requests").tag("cache", "pins").tag("result", "miss").counter().count();
        EmotionMapPageView recovered = page(null);

        // then
        assertThat(fallback.items()).hasSize(2);
        assertThat(recovered.items()).hasSize(2);
        assertThat(meters.find("pheeeew.map.cache.requests").tag("cache", "pins").tag("result", "miss").counter().count() - misses)
                .isEqualTo(1);
    }

    @Test
    void 등록_공개를_기다리는_동안_삭제하면_대기중인_등록도_함께_공개하고_이전_작업은_무시한다() {
        // given
        page(null);
        Emotion added = commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "대기 등록", null, null,
                requester.getPublicId());
        assertThat(page(null).items()).hasSize(1);

        // when
        commands.delete(emotion.getId(), requester.getPublicId());
        EmotionMapPageView immediate = page(null);
        executor.forcePreviouslySubmittedTasks();
        EmotionMapPageView afterOldTask = page(null);

        // then
        assertThat(immediate.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(added.getId()));
        assertThat(afterOldTask).isEqualTo(immediate);
    }

    @Test
    void 미래_생성시각의_핀이_활성화되면_기존_기준선을_재사용하지_않는다() {
        // given
        jdbc.sql("UPDATE emotions SET created_at = :at WHERE id = :id")
                .param("at", java.sql.Timestamp.from(clock.instant().plusSeconds(1)))
                .param("id", emotion.getId()).update();
        assertThat(page(null).items()).isEmpty();

        // when
        clock.advance(java.time.Duration.ofSeconds(1));
        EmotionMapPageView result = page(null);

        // then
        assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(emotion.getId()));
    }

    @Test
    void 보관_후보_수와_미반영_변경의_나이를_관찰한다() {
        // given
        page(null);
        queries.findRegionMap(BOUNDS, com.pheeeew.region.domain.RegionLevel.SIDO, requester.getPublicId(),
                com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter.from(null));
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "관측할 등록", null, null,
                requester.getPublicId());

        // when
        clock.advance(java.time.Duration.ofMillis(500));

        // then
        assertThat(page(null).items()).hasSize(1);
        assertThat(meters.get("pheeeew.map.cache.candidates").tag("cache", "pins").gauge().value()).isEqualTo(2);
        assertThat(meters.get("pheeeew.map.cache.pending.age").tag("cache", "pins").gauge().value()).isEqualTo(0.5);
        assertThat(meters.get("pheeeew.map.cache.pending.age").tag("cache", "regions").gauge().value()).isEqualTo(0.5);
        clock.advance(java.time.Duration.ofMillis(500));
        executor.runDueTasks();
        assertThat(page(null).items()).hasSize(2);
        assertThat(meters.get("pheeeew.map.cache.pending.age").tag("cache", "pins").gauge().value()).isZero();
        assertThat(meters.get("pheeeew.map.cache.pending.age").tag("cache", "regions").gauge().value()).isZero();
    }

    private EmotionMapPageView page(String cursor) {
        return queries.findContentMapWithinBounds(cursor == null ? BOUNDS : null, requester.getPublicId(),
                null, null, null, null, null, cursor);
    }
}
