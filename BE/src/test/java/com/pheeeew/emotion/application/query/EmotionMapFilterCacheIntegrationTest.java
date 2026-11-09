package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.application.mapcache.RegionSummaryCache;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class, MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionMapFilterCacheIntegrationTest {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

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
    private PlatformTransactionManager transactions;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private MapCacheTestTime.ManualScheduledExecutor tasks;

    private Device requester;
    private LocalDate start;
    private LocalDate end;
    private Long memoSeoul;
    private Long audioSeoul;
    private Long memoBusan;
    private Long oldAudioBusan;

    @BeforeEach
    void setUp() {
        pins.invalidate();
        regions.invalidate();
        검증용_지역_계층을_저장한다(jdbc);
        saveRegion("26", "SIDO", null);
        saveRegion("26010", "SIGUNGU", "26");
        saveRegion("26010510", "EMD", "26010");
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP")
                .update();
        requester = devices.save(기본_기기_빌더().build());
        Device other = devices.save(기본_기기_빌더().build());
        end = LocalDate.ofInstant(clock.instant(), KOREA);
        start = end.minusMonths(1);
        Instant now = clock.instant();

        memoSeoul = save(requester, memo(EmotionState.ANGRY, "11010530"), now.minusSeconds(5 * 3600));
        audioSeoul = save(requester, audio(EmotionState.EXHAUSTED, "11010530"), now.minusSeconds(4 * 3600));
        memoBusan = save(requester, memo(EmotionState.EXHAUSTED, "26010510"), now.minusSeconds(3 * 3600));
        oldAudioBusan = save(requester, audio(EmotionState.ANGRY, "26010510"),
                start.atStartOfDay(KOREA).toInstant().minusSeconds(1));
        save(other, memo(EmotionState.ANGRY, "11010530"), now.minusSeconds(3600));
        Long blocked = save(other, audio(EmotionState.DISCOURAGED, "26010510"), now.minusSeconds(1800));
        jdbc.sql("""
                INSERT INTO emotion_blocks(blocker_device_id, emotion_id, created_at, updated_at)
                VALUES (:requester, :emotion, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("requester", requester.getId()).param("emotion", blocked).update();
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
    void 내_감정과_내용_필터의_지역_요약과_핀은_캐시에서_응답하고_SQL_결과와_같다() {
        for (EmotionSearchScope scope : List.of(EmotionSearchScope.ALL, EmotionSearchScope.MY)) {
            for (EmotionContentFilter content : EmotionContentFilter.values()) {
                if (scope == EmotionSearchScope.ALL && content == EmotionContentFilter.ALL) {
                    continue;
                }
                for (boolean period : List.of(false, true)) {
                    // given
                    LocalDate from = period ? start : null;
                    LocalDate to = period ? end : null;
                    var filter = EmotionSearchFilter.of(from, to, content, scope, null);
                    double served = cacheServed("regions") + cacheServed("pins");

                    // when
                    var sido = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), filter);
                    var sigungu = queries.findRegionMap(BOUNDS, RegionLevel.SIGUNGU, requester.getPublicId(), filter);
                    List<List<Long>> pages = cachedPages(from, to, content, scope);
                    double cacheCalls = cacheServed("regions") + cacheServed("pins") - served;

                    // then
                    String label = scope + "/" + content + "/" + (period ? "기간" : "전체");
                    assertThat(summaries(sido)).as(label).isEqualTo(summaries(inTransaction(() ->
                            queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), filter))));
                    assertThat(summaries(sigungu)).as(label).isEqualTo(summaries(inTransaction(() ->
                            queries.findRegionMap(BOUNDS, RegionLevel.SIGUNGU, requester.getPublicId(), filter))));
                    assertThat(pages).as(label).isEqualTo(sqlPages(from, to, content, scope));
                    assertThat(cacheCalls).as(label).isEqualTo(2 + pages.size());
                }
            }
        }
    }

    @Test
    void 필터_결과는_기간_밖_감정과_차단을_계약대로_처리한다() {
        // when
        var myPeriodRegions = queries.findRegionMap(BOUNDS, RegionLevel.SIGUNGU, requester.getPublicId(),
                EmotionSearchFilter.of(start, end, null, EmotionSearchScope.MY, null));
        var myAudioPins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), null, null,
                EmotionContentFilter.AUDIO, EmotionSearchScope.MY, null, 10);
        var myPeriodPins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end,
                null, EmotionSearchScope.MY, null, 10);
        var audioRegions = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(),
                EmotionSearchFilter.of(start, end, EmotionContentFilter.AUDIO, null, null));
        var audioPins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), start, end,
                EmotionContentFilter.AUDIO, null, null, 10);

        // then
        assertThat(totals(myPeriodRegions)).containsExactlyInAnyOrderEntriesOf(Map.of("11010", 2L, "26010", 1L));
        assertThat(myAudioPins.items()).extracting(item -> item.id()).containsExactly(audioSeoul, oldAudioBusan);
        assertThat(myPeriodPins.items()).extracting(item -> item.id())
                .containsExactly(memoBusan, audioSeoul, memoSeoul);
        assertThat(totals(audioRegions)).containsExactlyInAnyOrderEntriesOf(Map.of("11", 1L, "26", 1L));
        assertThat(audioPins.items()).extracting(item -> item.id()).containsExactly(audioSeoul);
    }

    @Test
    void 삭제는_내_감정_필터에_즉시_반영하고_등록은_배치_게시_후_반영한다() {
        // given
        var mine = EmotionSearchFilter.of(null, null, null, EmotionSearchScope.MY, null);
        queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), mine);
        queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), null, null,
                EmotionContentFilter.AUDIO, EmotionSearchScope.MY, null, 10);

        // when
        commands.delete(audioSeoul, requester.getPublicId());
        var deletedRegions = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), mine);
        var deletedPins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), null, null,
                EmotionContentFilter.AUDIO, EmotionSearchScope.MY, null, 10);
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "새 기록", null, null, requester.getPublicId());
        clock.advance(Duration.ofSeconds(1));
        tasks.runDueTasks();
        var registeredRegions = queries.findRegionMap(BOUNDS, RegionLevel.SIDO, requester.getPublicId(), mine);
        var registeredPins = queries.findContentMapWithinBoundsV4(BOUNDS, requester.getPublicId(), null, null,
                EmotionContentFilter.MEMO, EmotionSearchScope.MY, null, 10);

        // then
        assertThat(totals(deletedRegions)).containsExactlyInAnyOrderEntriesOf(Map.of("11", 1L, "26", 2L));
        assertThat(deletedPins.items()).extracting(item -> item.id()).containsExactly(oldAudioBusan);
        assertThat(totals(registeredRegions).values().stream().mapToLong(Long::longValue).sum()).isEqualTo(4);
        assertThat(registeredPins.items()).hasSize(3);
    }

    private List<List<Long>> cachedPages(LocalDate from, LocalDate to, EmotionContentFilter content,
            EmotionSearchScope scope) {
        return pages(from, to, content, scope, false);
    }

    /** 캐시가 발급한 커서를 그대로 써서 SQL 페이지를 비교한다. 첫 페이지 커서의 기준 시각은 경로마다 다르다. */
    private List<List<Long>> sqlPages(LocalDate from, LocalDate to, EmotionContentFilter content,
            EmotionSearchScope scope) {
        return pages(from, to, content, scope, true);
    }

    private List<List<Long>> pages(LocalDate from, LocalDate to, EmotionContentFilter content, EmotionSearchScope scope,
            boolean sql) {
        List<List<Long>> pages = new ArrayList<>();
        String cursor = null;
        do {
            String current = cursor;
            EmotionMapPageView cached = queries.findContentMapWithinBoundsV4(current == null ? BOUNDS : null,
                    requester.getPublicId(), from, to, content, scope, current, 1);
            EmotionMapPageView page = sql ? inTransaction(() -> queries.findContentMapWithinBoundsV4(
                    current == null ? BOUNDS : null, requester.getPublicId(), from, to, content, scope, current, 1))
                    : cached;
            pages.add(page.items().stream().map(item -> item.id()).toList());
            assertThat(page.hasNext()).isEqualTo(cached.hasNext());
            cursor = cached.nextCursor();
        } while (cursor != null);

        return pages;
    }

    private <T> T inTransaction(Supplier<T> query) {
        return new TransactionTemplate(transactions).execute(status -> query.get());
    }

    private double cacheServed(String cache) {
        return meters.find("pheeeew.map.cache.requests").tag("cache", cache).counters().stream()
                .filter(counter -> !"fallback".equals(counter.getId().getTag("result")))
                .mapToDouble(Counter::count).sum();
    }

    private Map<String, Long> totals(List<EmotionRegionMapItemView> items) {
        return items.stream().collect(Collectors.toMap(item -> item.region().code(), item -> item.summary().totalCount()));
    }

    private Map<String, RegionEmotionSummary> summaries(List<EmotionRegionMapItemView> items) {
        return items.stream().collect(Collectors.toMap(item -> item.region().code(), EmotionRegionMapItemView::summary));
    }

    private Emotion.EmotionBuilder memo(EmotionState state, String regionCode) {
        return 기본_한숨_빌더().regionCode(regionCode).memo("메모").state(state);
    }

    private Emotion.EmotionBuilder audio(EmotionState state, String regionCode) {
        return 기본_한숨_빌더().regionCode(regionCode).state(state)
                .audio(Audio.builder().objectKey("emotions/audio/" + UUID.randomUUID() + ".m4a").build());
    }

    private Long save(Device author, Emotion.EmotionBuilder builder, Instant createdAt) {
        Long id = emotions.save(builder.deviceId(author.getId()).build()).getId();
        jdbc.sql("UPDATE emotions SET created_at = ? WHERE id = ?").params(Timestamp.from(createdAt), id).update();

        return id;
    }

    private void saveRegion(String code, String level, String parentCode) {
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                VALUES (:code, :level, :name, :parentCode,
                        ST_GeomFromText('MULTIPOLYGON(((126 37, 128 37, 128 39, 126 39, 126 37)))', 4326),
                        ST_GeomFromText('POINT(127 38)', 4326))
                """).param("code", code).param("level", level).param("name", "검증용 " + level)
                .param("parentCode", parentCode).update();
    }
}
