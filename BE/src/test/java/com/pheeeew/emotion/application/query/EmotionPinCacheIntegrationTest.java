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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
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
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionPinCacheIntegrationTest {

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

    @Autowired
    private GroupRepository groups;
    @Autowired
    private GroupStampRepository stamps;

    @Autowired
    private PlatformTransactionManager transactions;

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
        stamps.deleteAllInBatch();
        groups.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Test
    void 기본_핀_조회는_공유_후보_캐시를_재사용한다() {
        // given
        EmotionMapPageView first = page(null);
        double hits = cacheHits();

        // when
        EmotionMapPageView second = page(null);

        // then
        assertThat(second).isEqualTo(first);
        assertThat(second.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(emotion.getId()));
        assertThat(cacheHits() - hits).isEqualTo(1);
        assertThat(meters.find("pheeeew.map.cache.pin.search").timer().count()).isGreaterThanOrEqualTo(2);
        assertThat(meters.find("pheeeew.map.cache.pin.scanned").summary().totalAmount()).isGreaterThanOrEqualTo(2);
        assertThat(meters.find("pheeeew.map.cache.rebuild").tag("cache", "pins").timer().count()).isPositive();
    }

    @Test
    void 조회마다_감정과_작성자_차단을_먼저_제외하고_최대_200개를_고른다() {
        // given
        Device author = devices.save(기본_기기_빌더().build());
        insertPins(220, author.getId());
        page(null);
        double hits = cacheHits();
        jdbc.sql("""
                INSERT INTO device_blocks(blocker_device_id, blocked_device_id, origin_emotion_id, created_at, updated_at)
                VALUES (:requester, :author, :emotion, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("requester", requester.getId()).param("author", author.getId())
                .param("emotion", emotion.getId()).update();

        // when
        EmotionMapPageView result = page(null);
        jdbc.sql("""
                INSERT INTO emotion_blocks(blocker_device_id, emotion_id, created_at, updated_at)
                VALUES (:requester, :emotion, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).param("requester", requester.getId()).param("emotion", emotion.getId()).update();
        EmotionMapPageView allBlocked = page(null);

        // then
        assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(emotion.getId()));
        assertThat(result.hasNext()).isFalse();
        assertThat(allBlocked.items()).isEmpty();
        assertThat(cacheHits() - hits).isEqualTo(2);
    }

    @Test
    void 수정과_삭제_성공_후_조회는_기존_핀_스냅샷에_즉시_반영된다() {
        // given
        page(null);
        double hits = cacheHits();

        // when
        commands.update(emotion.getId(), requester.getPublicId(), EmotionState.ANGRY, null, null, false, null);
        EmotionMapPageView updated = page(null);
        commands.update(emotion.getId(), requester.getPublicId(), EmotionState.ANGRY, "복원한 내용", null, false, null);
        EmotionMapPageView restored = page(null);
        commands.delete(emotion.getId(), requester.getPublicId());
        EmotionMapPageView deleted = page(null);

        // then
        assertThat(updated.items()).isEmpty();
        assertThat(restored.items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo(EmotionState.ANGRY));
        assertThat(deleted.items()).isEmpty();
        assertThat(cacheHits() - hits).isEqualTo(3);
    }

    @Test
    void 후보_10만건은_캐시하고_등록으로_상한을_초과하면_SQL로_전환한다() {
        // given
        insertPins(99_999, requester.getId());
        page(null);
        double hits = cacheHits();
        page(null);
        assertThat(cacheHits() - hits).isEqualTo(1);

        // when
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "상한 초과", null, null, requester.getPublicId());
        double fallbacks = cacheFallbacks();
        EmotionMapPageView result = page(null);
        page(null);

        // then
        assertThat(result.items()).hasSize(200);
        assertThat(result.hasNext()).isTrue();
        assertThat(cacheHits() - hits).isEqualTo(1);
        assertThat(cacheFallbacks() - fallbacks).isEqualTo(2);
    }

    @Test
    void 페이지_사이_등록은_고정된_커서_시각_이후이므로_다음_페이지에서_제외한다() {
        // given
        insertPins(220, requester.getId());
        EmotionMapPageView first = page(null);
        assertThat(first.items()).hasSize(200);
        assertThat(first.hasNext()).isTrue();

        // when
        Emotion added = commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "다음 기록", null, null,
                requester.getPublicId());
        EmotionMapPageView second = page(first.nextCursor());

        // then
        assertThat(second.items()).hasSize(21);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.items()).noneSatisfy(item -> assertThat(item.id()).isEqualTo(added.getId()));
        assertThat(second.items()).extracting(item -> item.id())
                .doesNotContainAnyElementsOf(first.items().stream().map(item -> item.id()).toList());
    }

    @Test
    void 날짜변경선_영역의_경계를_포함하고_작성자와_상태가_없는_핀도_조회한다() {
        // given
        insertPins(1, requester.getId());
        jdbc.sql("UPDATE emotions SET location = ST_SetSRID(ST_MakePoint(180, 37), 4326), device_id = NULL, state = NULL WHERE id = :id")
                .param("id", emotion.getId()).update();
        jdbc.sql("UPDATE emotions SET location = ST_SetSRID(ST_MakePoint(-180, 39), 4326) WHERE id <> :id")
                .param("id", emotion.getId()).update();

        // when
        EmotionMapPageView result = queries.findContentMapWithinBounds(EmotionSearchBounds.of(170, 37, -170, 39),
                requester.getPublicId(), null, null, null, null, null, null);

        // then
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).filteredOn(item -> item.id().equals(emotion.getId())).singleElement()
                .satisfies(item -> assertThat(item.state()).isNull());
    }

    @Test
    void 선택된_핀의_스탬프_표시정보는_후보_캐시와_별개로_매번_조회한다() {
        // given
        Group group = groups.save(기본_그룹_빌더().build());
        GroupStamp stamp = stamps.save(기본_스탬프_빌더(group).build());
        jdbc.sql("UPDATE emotions SET group_stamp_id = :stamp WHERE id = :id")
                .param("stamp", stamp.getId()).param("id", emotion.getId()).update();
        assertThat(page(null).items().getFirst().groupStamp().text()).isEqualTo("기본");
        double hits = cacheHits();

        // when
        jdbc.sql("UPDATE group_stamps SET text = '변경' WHERE id = :id").param("id", stamp.getId()).update();
        EmotionMapPageView result = page(null);

        // then
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.groupStamp().text()).isEqualTo("변경");
            assertThat(item.groupId()).isEqualTo(group.getPublicId());
        });
        assertThat(cacheHits() - hits).isEqualTo(1);
    }

    @Test
    void SQL_첫_페이지보다_캐시_기준선이_오래되면_다음_페이지도_SQL로_응답한다() {
        // given
        insertPins(220, requester.getId());
        page(null);
        EmotionMapPageView sqlFirst = new TransactionTemplate(transactions).execute(status -> page(null));
        double fallbacks = cacheFallbacks();
        double hits = cacheHits();

        // when
        EmotionMapPageView result = page(sqlFirst.nextCursor());

        // then
        assertThat(result.items()).hasSize(21);
        assertThat(cacheFallbacks() - fallbacks).isEqualTo(1);
        assertThat(cacheHits() - hits).isZero();
    }

    private double cacheFallbacks() {
        Counter counter = meters.find("pheeeew.map.cache.requests").tag("cache", "pins").tag("result", "fallback").counter();
        return counter == null ? 0 : counter.count();
    }

    private void insertPins(int count, Long authorId) {
        jdbc.sql("""
                INSERT INTO emotions(request_id, location, memo, device_id, state, created_at, updated_at,
                                     region_code, region_classified_at)
                SELECT gen_random_uuid(), ST_SetSRID(ST_MakePoint(127, 38), 4326), '추가 핀', :author,
                       'ANGRY', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP, '11010530', CURRENT_TIMESTAMP
                FROM generate_series(1, :count)
                """).param("author", authorId).param("count", count).update();
    }

    private EmotionMapPageView page(String cursor) {
        return queries.findContentMapWithinBounds(cursor == null ? BOUNDS : null, requester.getPublicId(),
                null, null, null, null, null, cursor);
    }

    private double cacheHits() {
        Counter counter = meters.find("pheeeew.map.cache.requests").tag("cache", "pins").tag("result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }
}
