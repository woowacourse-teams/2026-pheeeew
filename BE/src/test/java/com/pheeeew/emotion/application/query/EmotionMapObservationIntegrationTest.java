package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.application.mapcache.RegionSummaryCache;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.metrics.MapReadObservation;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmotionMapObservationIntegrationTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private PinCandidateCache pins;
    @Autowired
    private RegionSummaryCache regions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private PlatformTransactionManager transactions;

    private Device requester;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        pins.invalidate();
        regions.invalidate();
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP")
                .update();
        requester = devices.save(기본_기기_빌더().build());
        emotion = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("계측할 핀").state(EmotionState.ANGRY).build());
    }

    @AfterEach
    void tearDown() {
        pins.invalidate();
        regions.invalidate();
        jdbc.sql("DELETE FROM emotion_blocks").update();
        jdbc.sql("DELETE FROM device_blocks").update();
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Test
    void 한달_SQL_조회는_차단과_커서_기간을_보존하고_자정_이후_후속_페이지도_분류한다() {
        // given
        Emotion second = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("두 번째 핀").build());
        Emotion hidden = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("차단한 핀").build());
        blockEmotion(hidden.getId());
        LocalDate end = LocalDate.ofInstant(clock.instant(), KOREA);
        LocalDate start = end.minusMonths(1);

        // when
        Observed<EmotionMapPageView> first = observe("pins", () -> queries.findContentMapWithinBoundsV4(
                BOUNDS, requester.getPublicId(), start, end, null, null, null, 1));
        clock.advance(Duration.between(clock.instant(), end.plusDays(1).atStartOfDay(KOREA).toInstant().plusSeconds(1)));
        Observed<EmotionMapPageView> next = observe("pins", () -> queries.findContentMapWithinBoundsV4(
                null, requester.getPublicId(), null, null, null, null, first.value().nextCursor(), 1));

        // then
        assertThat(first.value().hasNext()).isTrue();
        assertThat(next.value().hasNext()).isFalse();
        assertThat(first.value().items()).hasSize(1);
        assertThat(next.value().items()).hasSize(1);
        assertThat(java.util.stream.Stream.concat(first.value().items().stream(), next.value().items().stream())
                .map(item -> item.id()).toList()).containsExactlyInAnyOrder(emotion.getId(), second.getId());
        assertThat(first.details()).containsEntry("query", "month_current_default")
                .containsEntry("page", "first").containsEntry("decision", "bypass_period")
                .containsEntry("cacheAttempted", false);
        assertThat(next.details()).containsEntry("query", "month_other_default")
                .containsEntry("page", "next").containsEntry("decision", "bypass_period");
    }

    @Test
    void 전체_기간_핀의_load와_hit를_구분하고_hit_이후에도_감정과_작성자_차단을_유지한다() {
        // given
        Device author = devices.save(기본_기기_빌더().build());
        emotions.save(기본_한숨_빌더().deviceId(author.getId()).memo("다른 작성자의 핀").build());
        Observed<EmotionMapPageView> loaded = observe("pins", () -> page(null));
        jdbc.sql("""
                INSERT INTO device_blocks(blocker_device_id, blocked_device_id, origin_emotion_id, created_at, updated_at)
                VALUES (:requester, :author, :emotion, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("requester", requester.getId()).param("author", author.getId())
                .param("emotion", emotion.getId()).update();

        // when
        Observed<EmotionMapPageView> hit = observe("pins", () -> page(null));
        blockEmotion(emotion.getId());
        Observed<EmotionMapPageView> blocked = observe("pins", () -> page(null));

        // then
        assertThat(loaded.value().items()).hasSize(2);
        assertThat(loaded.details()).containsEntry("query", "all_default").containsEntry("decision", "cache_load");
        assertThat(hit.value().items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(emotion.getId()));
        assertThat(hit.details()).containsEntry("decision", "cache_hit")
                .containsEntry("eligible", true).containsEntry("cacheAttempted", true);
        assertThat(blocked.value().items()).isEmpty();
        assertThat(blocked.details()).containsEntry("decision", "cache_hit");
    }

    @Test
    void 미게시_등록이_3초_지연되면_SQL_fallback을_기록하고_다음_조회에서_복구한다() {
        // given
        page(null);
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "미게시 등록", null, null,
                requester.getPublicId());
        clock.advance(Duration.ofSeconds(3));

        // when
        Observed<EmotionMapPageView> fallback = observe("pins", () -> page(null));
        Observed<EmotionMapPageView> recovered = observe("pins", () -> page(null));

        // then
        assertThat(fallback.value().items()).hasSize(2);
        assertThat(fallback.details()).containsEntry("decision", "cache_fallback");
        assertThat(recovered.value().items()).hasSize(2);
        assertThat(recovered.details()).containsEntry("decision", "cache_load");
    }

    @ParameterizedTest
    @EnumSource(value = RegionLevel.class, names = {"SIDO", "SIGUNGU"})
    void 전국_요약은_실제_load와_hit_및_한달_SQL_우회를_구분한다(RegionLevel level) {
        // given
        EmotionSearchFilter all = EmotionSearchFilter.from(null);
        LocalDate end = LocalDate.ofInstant(clock.instant(), KOREA);
        EmotionSearchFilter month = EmotionSearchFilter.of(end.minusMonths(1), end, null, null, null);

        // when
        var loaded = observe("regions", () -> queries.findRegionMap(BOUNDS, level, requester.getPublicId(), all));
        var hit = observe("regions", () -> queries.findRegionMap(BOUNDS, level, requester.getPublicId(), all));
        var bypass = observe("regions", () -> queries.findRegionMap(BOUNDS, level, requester.getPublicId(), month));

        // then
        assertThat(loaded.value()).isEqualTo(hit.value()).isEqualTo(bypass.value()).hasSize(1);
        assertThat(loaded.details()).containsEntry("decision", "cache_load");
        assertThat(hit.details()).containsEntry("decision", "cache_hit");
        assertThat(bypass.details()).containsEntry("query", "month_current_default")
                .containsEntry("decision", "bypass_period").containsEntry("cacheAttempted", false);
    }

    @Test
    void 다른_필터와_외부_트랜잭션_EMD_및_빈_지역의_기존_조회_분기를_관찰한다() {
        // given
        EmotionSearchFilter all = EmotionSearchFilter.from(null);

        // when
        var filtered = observe("pins", () -> queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(),
                null, null, EmotionContentFilter.MEMO, EmotionSearchScope.ALL, null));
        var transaction = observe("pins", () -> new TransactionTemplate(transactions).execute(status -> page(null)));
        var emd = observe("regions", () -> queries.findRegionMap(BOUNDS, RegionLevel.EMD, requester.getPublicId(), all));
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        var empty = observe("regions", () -> queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), all));

        // then
        assertThat(filtered.value().items()).hasSize(1);
        assertThat(filtered.details()).containsEntry("query", "other").containsEntry("decision", "bypass_filter")
                .containsEntry("scope", "ALL").containsEntry("contentType", "MEMO");
        assertThat(transaction.value().items()).hasSize(1);
        assertThat(transaction.details()).containsEntry("decision", "bypass_transaction");
        assertThat(emd.value()).hasSize(1);
        assertThat(emd.details()).containsEntry("decision", "bypass_level");
        assertThat(empty.value()).isEmpty();
        assertThat(empty.details()).containsEntry("decision", "empty").containsEntry("eligible", true)
                .containsEntry("cacheAttempted", false);
    }

    @Test
    void 커서_검증_실패와_적격_판정_후_기기_조회_실패를_분리한다() {
        // given
        var request = new MockHttpServletRequest("GET", "/api/v4/emotions/map");

        // when / then
        try (var observation = MapReadObservation.open(request, meters)) {
            assertThatThrownBy(() -> queries.findContentMapWithinBoundsV4(null, requester.getPublicId(), null, null,
                    null, null, "invalid-cursor")).isInstanceOf(EmotionException.class);
            observation.finish(400, false);
            assertThat(observation.details()).containsEntry("query", "unclassified")
                    .containsEntry("decision", "unclassified").containsEntry("outcome", "client_error");
        }
        try (var observation = MapReadObservation.open(request, meters)) {
            assertThatThrownBy(() -> queries.findContentMapWithinBoundsV4(BOUNDS, UUID.randomUUID(), null, null,
                    null, null, null)).isInstanceOf(DeviceException.class);
            observation.finish(404, false);
            assertThat(observation.details()).containsEntry("query", "all_default")
                    .containsEntry("decision", "eligible").containsEntry("outcome", "client_error")
                    .containsEntry("cacheAttempted", false);
        }
    }

    private <T> Observed<T> observe(String route, Supplier<T> action) {
        var request = new MockHttpServletRequest("GET", "/api/v4/emotions/map" + (route.equals("regions") ? "/regions" : ""));
        double before = requestCount();
        try (var observation = MapReadObservation.open(request, meters)) {
            T value = action.get();
            observation.finish(200, false);
            assertThat(requestCount() - before).isEqualTo(1);
            Map<String, Object> details = observation.details();
            assertThat(meters.get("pheeeew.map.read.requests").tags("api_version", "v4", "route", route,
                    "query", (String) details.get("query"), "page", (String) details.get("page"),
                    "decision", (String) details.get("decision"), "outcome", "success").counter().count()).isPositive();
            return new Observed<>(value, details);
        }
    }

    private double requestCount() {
        return meters.find("pheeeew.map.read.requests").counters().stream().mapToDouble(Counter::count).sum();
    }

    private EmotionMapPageView page(String cursor) {
        return queries.findContentMapWithinBoundsV4(cursor == null ? BOUNDS : null, requester.getPublicId(),
                null, null, null, null, cursor);
    }

    private void blockEmotion(Long id) {
        jdbc.sql("""
                INSERT INTO emotion_blocks(blocker_device_id, emotion_id, created_at, updated_at)
                VALUES (:requester, :emotion, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("requester", requester.getId()).param("emotion", id).update();
    }

    private record Observed<T>(T value, Map<String, Object> details) {
    }
}
