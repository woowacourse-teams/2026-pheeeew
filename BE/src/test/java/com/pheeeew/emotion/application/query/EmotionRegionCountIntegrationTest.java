package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.common.logging.RequestLoggingConfiguration;
import com.pheeeew.common.logging.RequestServiceTimingAspect;
import com.pheeeew.common.logging.PheeeewRequestTimingStage;
import com.pheeeew.common.logging.RequestTiming;
import com.pheeeew.common.logging.RequestTiming.Measurement;
import com.pheeeew.common.logging.RequestTimingStage;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.metrics.EmotionRegionQueryTimingAspect;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.region.infra.metrics.RegionQueryMetrics;
import com.pheeeew.region.infra.metrics.RegionQueryMetricsAspect;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, AopAutoConfiguration.class,
        RegionQueryMetrics.class, RegionQueryMetricsAspect.class, EmotionRegionQueryTimingAspect.class,
        RequestServiceTimingAspect.class})
class EmotionRegionCountIntegrationTest {

    private static final String EMD = "11010530";

    @Autowired
    private EmotionMapQueryService service;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EmotionRepository emotionRepository;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meterRegistry;

    private Device requester;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        requester = 기본_기기_빌더().build();
        entityManager.persist(requester);
        jdbc.sql("""
                UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP,
                    backfill_verified_at = CURRENT_TIMESTAMP
                """).update();
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 지역_집계는_NONE을_개수와_대표_감정에서_함께_제외한다(RegionLevel level) {
        // given
        for (int i = 0; i < 5; i++) {
            save(classified().state(EmotionState.FRUSTRATED));
        }
        save(classified().memo("메모").state(EmotionState.ANGRY));
        save(classified().audio(Audio.builder().objectKey("recordings/region.m4a").build()).state(EmotionState.DISCOURAGED));
        save(classified().memo("삭제").state(EmotionState.FRUSTRATED)).delete();
        Emotion future = save(classified().memo("미래").state(EmotionState.FRUSTRATED));
        entityManager.flush();
        jdbc.sql("UPDATE emotions SET created_at = '2025-01-01T00:00:00Z'").update();
        jdbc.sql("UPDATE emotions SET created_at = '2100-01-01T00:00:00Z' WHERE id = :id")
                .param("id", future.getId()).update();
        entityManager.clear();
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);

        // when / then: 화면 밖 기록까지 선택 지역 전체를 합산한다.
        assertThat(findRegionMap(bounds, level, null)).singleElement().satisfies(item ->
                assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(2L, EmotionState.ANGRY)));
    }

    @Test
    void 내용_있는_지역_지도는_그룹_조건을_유지하고_NONE만_있는_지역을_생략한다() {
        // given
        Group target = 기본_그룹_빌더().build();
        Group other = 기본_그룹_빌더().name("다른 그룹").build();
        entityManager.persist(target);
        entityManager.persist(other);
        GroupStamp targetStamp = 기본_스탬프_빌더(target).build();
        GroupStamp otherStamp = 기본_스탬프_빌더(other).build();
        entityManager.persist(targetStamp);
        entityManager.persist(otherStamp);
        save(classified().groupStamp(targetStamp).memo("메모").state(EmotionState.DISCOURAGED));
        save(classified().groupStamp(targetStamp).state(EmotionState.ANGRY));
        save(classified().groupStamp(otherStamp).state(EmotionState.FRUSTRATED));
        entityManager.flush();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);

        // when / then
        assertThat(findRegionMap(bounds, RegionLevel.EMD, target.getPublicId()))
                .singleElement().satisfies(item ->
                        assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(1L, EmotionState.DISCOURAGED)));
        assertThat(findRegionMap(bounds, RegionLevel.EMD, other.getPublicId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 각_계층에서_선택한_지역의_하위_기록만_중복없이_합산한다(RegionLevel level) {
        // given: 형제 읍면동, 다른 시군구, 다른 시도에 각각 다른 수의 기록을 둔다.
        addRegion("11010531", RegionLevel.EMD, "11010");
        addRegion("11020", RegionLevel.SIGUNGU, "11");
        addRegion("11020530", RegionLevel.EMD, "11020");
        addRegion("21", RegionLevel.SIDO, null);
        addRegion("21010", RegionLevel.SIGUNGU, "21");
        addRegion("21010530", RegionLevel.EMD, "21010");
        var emdCodes = List.of(EMD, "11010531", "11020530", "21010530");
        for (int index = 0; index < emdCodes.size(); index++) {
            for (int count = 0; count <= index; count++) {
                EmotionState state = switch (index) {
                    case 0 -> EmotionState.ANGRY;
                    case 1, 2 -> EmotionState.FRUSTRATED;
                    default -> EmotionState.IRRITATED;
                };
                save(classified().memo("집계 메모").regionCode(emdCodes.get(index)).state(state));
            }
        }
        entityManager.flush();
        Map<String, Long> expected = switch (level) {
            case EMD -> Map.of(EMD, 1L, "11010531", 2L, "11020530", 3L, "21010530", 4L);
            case SIGUNGU -> Map.of("11010", 3L, "11020", 3L, "21010", 4L);
            case SIDO -> Map.of("11", 6L, "21", 4L);
        };

        // when / then
        var summaries = summaries(level, null);
        assertThat(counts(level, null)).containsExactlyInAnyOrderEntriesOf(expected);
        Map<String, EmotionState> representatives = switch (level) {
            case EMD -> Map.of(EMD, EmotionState.ANGRY, "11010531", EmotionState.FRUSTRATED,
                    "11020530", EmotionState.FRUSTRATED, "21010530", EmotionState.IRRITATED);
            case SIGUNGU -> Map.of("11010", EmotionState.FRUSTRATED, "11020", EmotionState.FRUSTRATED,
                    "21010", EmotionState.IRRITATED);
            case SIDO -> Map.of("11", EmotionState.FRUSTRATED, "21", EmotionState.IRRITATED);
        };
        representatives.forEach((code, state) -> assertThat(summaries.get(code).representativeState()).isEqualTo(state));
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 모든_기록의_상태가_NULL이면_개수만_집계하고_대표_감정은_없다(RegionLevel level) {
        // given
        save(classified().memo("집계 메모"));
        entityManager.flush();

        // when / then
        assertThat(summaries(level, null)).hasSize(1).containsValue(RegionEmotionSummary.of(1L, null));
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 화면_밖_내용_있는_기록과_NULL_상태를_페이지_제한없이_누적한다(RegionLevel level) {
        // given: 화면은 지역 일부만 포함하지만, 기록은 그 화면 밖의 서울시청 좌표에 있다.
        save(classified().memo("집계 메모"));
        save(classified().state(EmotionState.FRUSTRATED));
        save(classified().memo("누적 메모").state(EmotionState.FRUSTRATED));
        save(classified().audio(Audio.builder().objectKey("recordings/test.m4a").build()).state(EmotionState.ANGRY));
        for (int i = 0; i < 201; i++) {
            save(classified().memo("집계 메모"));
        }
        save(classified().memo("집계 메모").state(EmotionState.FRUSTRATED)).delete();
        Emotion hidden = save(classified().memo("집계 메모").state(EmotionState.FRUSTRATED));
        Emotion future = save(classified().memo("집계 메모").state(EmotionState.FRUSTRATED));
        entityManager.flush();
        jdbc.sql("UPDATE emotions SET created_at = '2000-01-01T00:00:00Z'").update();
        jdbc.sql("UPDATE emotions SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id").param("id", hidden.getId()).update();
        jdbc.sql("UPDATE emotions SET created_at = '2100-01-01T00:00:00Z' WHERE id = :id").param("id", future.getId()).update();
        entityManager.clear();
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);

        // when / then: 과거 기간 하한도, 개별 지도 200개 제한도 적용하지 않는다.
        String code = switch (level) {
            case SIDO -> "11";
            case SIGUNGU -> "11010";
            case EMD -> EMD;
        };
        assertThat(findRegionMap(bounds, level, null)).singleElement().satisfies(item -> {
            assertThat(item.region().code()).isEqualTo(code);
            assertThat(item.region().level()).isEqualTo(level);
            assertThat(item.region().name()).isEqualTo("검증용 " + level);
            assertThat(item.region().parentCode()).isEqualTo(level == RegionLevel.SIDO ? null :
                    code.substring(0, code.length() == 5 ? 2 : 5));
            assertThat(item.region().longitude()).isEqualTo(127);
            assertThat(item.region().latitude()).isEqualTo(38);
            assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(204L, EmotionState.ANGRY));
        });
    }

    @ParameterizedTest
    @CsvSource({"11010530,4", "11010,5", "11,5"})
    void 개인_차단은_집계에_적용하지_않고_그룹_조건과_기존_개별_조회는_유지한다(String code, long totalCount) {
        // given: 그룹 회원 관계 없이 공개 감정을 조회한다.
        Device viewer = 기본_기기_빌더().build();
        Device author = 기본_기기_빌더().build();
        entityManager.persist(viewer);
        entityManager.persist(author);
        Group target = 기본_그룹_빌더().build();
        Group other = 기본_그룹_빌더().name("다른 그룹").build();
        entityManager.persist(target);
        entityManager.persist(other);
        GroupStamp targetStamp = 기본_스탬프_빌더(target).build();
        GroupStamp otherStamp = 기본_스탬프_빌더(other).build();
        entityManager.persist(targetStamp);
        entityManager.persist(otherStamp);
        Emotion blocked = save(classified().memo("집계 메모").deviceId(author.getId()).groupStamp(targetStamp).state(EmotionState.IRRITATED));
        save(classified().memo("집계 메모").deviceId(author.getId()).groupStamp(targetStamp).state(EmotionState.IRRITATED));
        save(classified().memo("집계 메모").groupStamp(otherStamp).state(EmotionState.ANGRY));
        save(classified().memo("집계 메모").state(EmotionState.ANGRY));
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT '11010531', level, name, parent_code, boundary, display_point FROM regions WHERE code = '11010530'
                """).update();
        save(classified().memo("집계 메모").regionCode("11010531").state(EmotionState.ANGRY));
        entityManager.persist(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(blocked.getId()).build());
        entityManager.persist(DeviceBlock.builder().blockerDeviceId(viewer.getId()).blockedDeviceId(author.getId())
                .originEmotionId(blocked.getId()).build());
        entityManager.flush();

        RegionLevel level = switch (code.length()) {
            case 2 -> RegionLevel.SIDO;
            case 5 -> RegionLevel.SIGUNGU;
            default -> RegionLevel.EMD;
        };

        // when / then: 같은 스탬프를 공유하는 감정도 각각 한 번씩 그룹별로 센다.
        assertThat(counts(level, null)).containsEntry(code, totalCount);
        assertThat(counts(RegionLevel.EMD, null)).containsEntry("11010531", 1L).hasSize(2);
        assertThat(summaries(level, target.getPublicId()))
                .containsEntry(code, RegionEmotionSummary.of(2L, EmotionState.IRRITATED));
        assertThat(summaries(level, other.getPublicId()))
                .containsEntry(code, RegionEmotionSummary.of(1L, EmotionState.ANGRY));
        assertThat(summaries(level, null).get(code).representativeState())
                .isEqualTo(EmotionState.ANGRY);
        assertThatThrownBy(() -> counts(level, UUID.randomUUID()))
                .isInstanceOfSatisfying(GroupException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
        assertThat(service.findMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 39), viewer.getPublicId(), target.getPublicId(), null).items()).isEmpty();
        assertThat(findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, target.getPublicId()))
                .singleElement().satisfies(item -> {
                    assertThat(item.region().code()).isEqualTo(code);
                    assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(2L, EmotionState.IRRITATED));
                });

    }

    @Test
    void 빈_지역은_제외하고_삭제_후_재조회도_코드순의_완전한_목록을_반환한다() {
        // given: 같은 화면에 3개 지역이 걸치지만 기록은 두 지역에만 있다.
        addRegion("11010529", RegionLevel.EMD, "11010");
        addRegion("11010531", RegionLevel.EMD, "11010");
        Emotion removed = save(classified().memo("집계 메모").state(EmotionState.FRUSTRATED));
        save(classified().memo("집계 메모").regionCode("11010529").state(EmotionState.ANGRY));
        entityManager.flush();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);

        // when / then
        assertThat(findRegionMap(bounds, RegionLevel.EMD, null))
                .extracting(item -> item.region().code()).containsExactly("11010529", EMD);
        removed.delete();
        entityManager.flush();
        assertThat(findRegionMap(bounds, RegionLevel.EMD, null))
                .extracting(item -> item.region().code()).containsExactly("11010529");
        assertThat(findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.EMD, null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"ANGRY,DISCOURAGED", "DISCOURAGED,EXHAUSTED", "EXHAUSTED,FRUSTRATED", "FRUSTRATED,IRRITATED"})
    void 동률이면_확정한_감정_순서를_적용한다(EmotionState first, EmotionState second) {
        // given: 삽입 순서와 Enum 선언 순서에 의존하지 않는다.
        save(classified().memo("집계 메모").state(second));
        save(classified().memo("집계 메모").state(first));
        entityManager.flush();

        // when / then
        assertThat(summaries(RegionLevel.EMD, null))
                .containsEntry(EMD, RegionEmotionSummary.of(2L, first));
    }

    @Test
    void 모든_감정이_동률이면_ANGRY를_대표로_고른다() {
        // given
        for (EmotionState state : EmotionState.values()) {
            save(classified().memo("집계 메모").state(state));
        }
        entityManager.flush();

        // when / then
        assertThat(summaries(RegionLevel.EMD, null))
                .containsEntry(EMD, RegionEmotionSummary.of(5L, EmotionState.ANGRY));
    }

    @Test
    void 동률_순서보다_실제_빈도가_우선한다() {
        // given: 동률 순서가 뒤인 감정일수록 더 많은 기록을 둔다.
        var states = List.of(EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
                EmotionState.FRUSTRATED, EmotionState.IRRITATED);
        for (int index = 0; index < states.size(); index++) {
            for (int count = 0; count <= index; count++) {
                save(classified().memo("집계 메모").state(states.get(index)));
            }
        }
        entityManager.flush();

        // when / then
        assertThat(summaries(RegionLevel.EMD, null))
                .containsEntry(EMD, RegionEmotionSummary.of(15L, EmotionState.IRRITATED));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "1,false", "1,true", "2,false", "2,true"})
    void 경계나_백필이_미준비면_빈_요청도_부분_집계로_응답하지_않는다(int status, boolean empty) {
        // given
        jdbc.sql(switch (status) {
            case 0 -> "UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL";
            case 1 -> "UPDATE region_datasets SET backfill_verified_at = NULL";
            default -> "DELETE FROM region_datasets";
        }).update();
        long successCount = 집계_준비_조회_수("success");
        long errorCount = 집계_준비_조회_수("error");

        // when / then
        var bounds = empty ? EmotionSearchBounds.of(130, 40, 132, 42) : EmotionSearchBounds.of(126, 37, 128, 39);
        assertThatThrownBy(() -> findRegionMap(bounds, RegionLevel.EMD, null))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
        assertThat(집계_준비_조회_수("success")).isEqualTo(successCount + (status == 1 ? 1 : 0));
        assertThat(집계_준비_조회_수("error")).isEqualTo(errorCount);
    }

    @Test
    void 화면에_지역이_없거나_지역에_기록이_없으면_빈_목록을_반환한다() {
        // given / when / then
        long successCount = 집계_준비_조회_수("success");
        assertThat(findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.EMD, null)).isEmpty();
        assertThat(findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD, null)).isEmpty();
        assertThat(집계_준비_조회_수("success")).isEqualTo(successCount + 2);
    }

    @Test
    void 집계_준비_조회_SQL_장애를_실패로_기록하고_예외를_유지한다() throws Exception {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        jdbc.sql("ALTER TABLE region_datasets RENAME COLUMN backfill_verified_at TO unavailable_backfill_verified_at").update();
        long errorCount = 집계_준비_조회_수("error");

        // when / then
        var timings = inRequest(() ->
                assertThatThrownBy(() -> counts(RegionLevel.EMD, null)).isInstanceOf(DataAccessException.class));
        assertThat(timings).containsOnlyKeys(PheeeewRequestTimingStage.REGIONS_SERVICE, PheeeewRequestTimingStage.REGIONS_BOUNDARIES,
                PheeeewRequestTimingStage.REGIONS_INTERSECTION, PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY);
        assertThat(timings.get(PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY).count()).isEqualTo(1);
        assertThat(집계_준비_조회_수("error")).isEqualTo(errorCount + 1);
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 집계_SQL_장애를_정상적인_빈_개수로_숨기지_않는다(RegionLevel level) throws Exception {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        jdbc.sql("ALTER TABLE emotions RENAME TO unavailable_emotions").update();

        // when / then
        var timings = inRequest(() ->
                assertThatThrownBy(() -> counts(level, null)).isInstanceOf(DataAccessException.class));
        var expected = level == RegionLevel.EMD
                ? List.of(PheeeewRequestTimingStage.REGIONS_SERVICE, PheeeewRequestTimingStage.REGIONS_BOUNDARIES,
                        PheeeewRequestTimingStage.REGIONS_INTERSECTION, PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY,
                        PheeeewRequestTimingStage.REGIONS_SUMMARY)
                : List.of(PheeeewRequestTimingStage.REGIONS_SERVICE, PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY,
                        PheeeewRequestTimingStage.REGIONS_SUMMARY);
        assertThat(timings).containsOnlyKeys(expected);
        assertThat(timings.get(PheeeewRequestTimingStage.REGIONS_SUMMARY).count()).isEqualTo(1);
        assertThat(timings.get(PheeeewRequestTimingStage.REGIONS_SUMMARY).totalNanos()).isGreaterThanOrEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 지역_지도는_실행한_단계만_요청별로_기록한다(boolean intersects) throws Exception {
        // given
        save(classified().memo("메모").state(EmotionState.ANGRY));
        entityManager.flush();
        var bounds = intersects ? EmotionSearchBounds.of(126, 37, 128, 39)
                : EmotionSearchBounds.of(130, 40, 132, 42);

        // when
        var timings = inRequest(() -> {
            var result = findRegionMap(bounds, RegionLevel.EMD, null);
            if (intersects) {
                assertThat(result).singleElement().satisfies(item ->
                        assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(1L, EmotionState.ANGRY)));
            } else {
                assertThat(result).isEmpty();
            }
        });

        // then: 조회 생략을 0초짜리 집계 호출로 기록하지 않는다.
        var expected = intersects
                ? List.of(PheeeewRequestTimingStage.REGIONS_SERVICE, PheeeewRequestTimingStage.REGIONS_BOUNDARIES, PheeeewRequestTimingStage.REGIONS_INTERSECTION,
                        PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY, PheeeewRequestTimingStage.REGIONS_SUMMARY)
                : List.of(PheeeewRequestTimingStage.REGIONS_SERVICE, PheeeewRequestTimingStage.REGIONS_BOUNDARIES,
                        PheeeewRequestTimingStage.REGIONS_INTERSECTION, PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY);
        assertThat(timings).containsOnlyKeys(expected);
        assertThat(timings.values()).allSatisfy(value -> {
            assertThat(value.count()).isEqualTo(1);
            assertThat(value.totalNanos()).isGreaterThanOrEqualTo(0).isEqualTo(value.maxNanos());
        });
    }

    @ParameterizedTest
    @CsvSource({
            "ALL,ALL,5,DISCOURAGED,EMD", "MEMO,ALL,3,ANGRY,EMD", "AUDIO,ALL,2,DISCOURAGED,EMD",
            "ALL,GROUP,3,DISCOURAGED,EMD", "MEMO,GROUP,2,ANGRY,EMD", "AUDIO,GROUP,1,DISCOURAGED,EMD",
            "ALL,MY,2,ANGRY,EMD", "MEMO,MY,1,ANGRY,EMD", "AUDIO,MY,1,DISCOURAGED,EMD",
            "ALL,ALL,5,DISCOURAGED,SIGUNGU", "MEMO,ALL,3,ANGRY,SIGUNGU", "AUDIO,ALL,2,DISCOURAGED,SIGUNGU",
            "ALL,GROUP,3,DISCOURAGED,SIGUNGU", "MEMO,GROUP,2,ANGRY,SIGUNGU", "AUDIO,GROUP,1,DISCOURAGED,SIGUNGU",
            "ALL,MY,2,ANGRY,SIGUNGU", "MEMO,MY,1,ANGRY,SIGUNGU", "AUDIO,MY,1,DISCOURAGED,SIGUNGU",
            "ALL,ALL,5,DISCOURAGED,SIDO", "MEMO,ALL,3,ANGRY,SIDO", "AUDIO,ALL,2,DISCOURAGED,SIDO",
            "ALL,GROUP,3,DISCOURAGED,SIDO", "MEMO,GROUP,2,ANGRY,SIDO", "AUDIO,GROUP,1,DISCOURAGED,SIDO",
            "ALL,MY,2,ANGRY,SIDO", "MEMO,MY,1,ANGRY,SIDO", "AUDIO,MY,1,DISCOURAGED,SIDO"
    })
    void KST_날짜_내용_범위_AND_필터를_개수와_대표_감정에_함께_적용한다(
            EmotionContentFilter contentType, EmotionSearchScope scope, long count, EmotionState state, RegionLevel level
    ) {
        // given: 요청 기기는 그룹 비회원이며, 다른 기기와 작성 기기 없는 과거 기록도 있다.
        Device other = 기본_기기_빌더().build();
        entityManager.persist(other);
        Group group = 기본_그룹_빌더().build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        saveAt(classified().memo("내 메모").deviceId(requester.getId()).groupStamp(stamp).state(EmotionState.ANGRY),
                "2025-01-01T15:00:00Z");
        saveAt(classified().memo("과거 메모").state(EmotionState.IRRITATED), "2025-01-02T01:00:00Z");
        saveAt(classified().memo("그룹 메모").deviceId(other.getId()).groupStamp(stamp).state(EmotionState.DISCOURAGED),
                "2025-01-02T02:00:00Z");
        saveAt(classified().audio(Audio.builder().objectKey("other.m4a").build()).deviceId(other.getId()).state(EmotionState.EXHAUSTED),
                "2025-01-02T03:00:00Z");
        saveAt(classified().audio(Audio.builder().objectKey("mine.m4a").build()).deviceId(requester.getId())
                .groupStamp(stamp).state(EmotionState.DISCOURAGED), "2025-01-02T14:59:59.999999Z");
        saveAt(classified().memo("시작 직전").deviceId(requester.getId()).groupStamp(stamp).state(EmotionState.IRRITATED),
                "2025-01-01T14:59:59.999999Z");
        saveAt(classified().memo("종료 직후").deviceId(requester.getId()).groupStamp(stamp).state(EmotionState.IRRITATED),
                "2025-01-02T15:00:00Z");
        saveAt(classified().deviceId(requester.getId()).groupStamp(stamp).state(EmotionState.IRRITATED),
                "2025-01-02T02:00:00Z");
        saveAt(classified().memo("삭제").deviceId(requester.getId()).groupStamp(stamp).state(EmotionState.IRRITATED),
                "2025-01-02T03:00:00Z").delete();
        entityManager.flush();
        entityManager.clear();
        LocalDate date = LocalDate.of(2025, 1, 2);
        UUID groupId = scope == EmotionSearchScope.GROUP ? group.getPublicId() : null;
        var filter = EmotionSearchFilter.of(date, date, contentType, scope, groupId == null ? null : List.of(groupId));
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);

        // when
        var regions = service.findRegionMap(bounds, level, requester.getPublicId(), filter);
        var map = service.findContentMapWithinBounds(bounds, requester.getPublicId(), groupId == null ? null : List.of(groupId),
                date, date, contentType, scope, null);

        // then: 공간·차단 정책 차이가 없는 자료에서는 개별 지도와 같은 대상이다.
        assertThat(regions).singleElement().satisfies(item ->
                assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(count, state)));
        assertThat(map.items()).hasSize((int) count);
    }

    @ParameterizedTest
    @CsvSource({
            "ALL,EMD,6,DISCOURAGED", "MEMO,EMD,4,ANGRY", "AUDIO,EMD,2,DISCOURAGED",
            "ALL,SIGUNGU,6,DISCOURAGED", "MEMO,SIGUNGU,4,ANGRY", "AUDIO,SIGUNGU,2,DISCOURAGED",
            "ALL,SIDO,6,DISCOURAGED", "MEMO,SIDO,4,ANGRY", "AUDIO,SIDO,2,DISCOURAGED"
    })
    void 여러_그룹을_지역별로_합산하고_날짜와_내용_조건을_개수와_대표_감정에_함께_적용한다(
            EmotionContentFilter contentType, RegionLevel level, long total, EmotionState state
    ) {
        // given: 그룹 비회원이며, 선택한 A와 B 이외의 기록만 있는 세 번째 지역도 있다.
        addRegion("11010531", RegionLevel.EMD, "11010");
        addRegion("11010532", RegionLevel.EMD, "11010");
        GroupStamp a = saveStamp("그룹 A");
        GroupStamp b = saveStamp("그룹 B");
        GroupStamp c = saveStamp("그룹 C");
        Device author = 기본_기기_빌더().build();
        entityManager.persist(author);
        Emotion blocked = saveAt(classified().groupStamp(a).deviceId(author.getId()).memo("시작 경계")
                .state(EmotionState.ANGRY), "2025-01-01T15:00:00Z");
        saveAt(classified().groupStamp(a).deviceId(author.getId()).memo("같은 스탬프")
                .state(EmotionState.ANGRY), "2025-01-02T01:00:00Z");
        saveAt(classified().groupStamp(a).audio(Audio.builder().objectKey("a.m4a").build())
                .state(EmotionState.DISCOURAGED), "2025-01-02T14:59:59.999999Z");
        saveAt(classified().groupStamp(b).memo("그룹 B")
                .state(EmotionState.DISCOURAGED), "2025-01-02T02:00:00Z");
        saveAt(classified().groupStamp(b).regionCode("11010531").audio(Audio.builder().objectKey("b.m4a").build())
                .state(EmotionState.DISCOURAGED), "2025-01-02T03:00:00Z");
        saveAt(classified().groupStamp(b).regionCode("11010531").memo("상태 없음"), "2025-01-02T04:00:00Z");
        saveAt(classified().groupStamp(c).memo("선택하지 않은 그룹").state(EmotionState.IRRITATED), "2025-01-02T05:00:00Z");
        saveAt(classified().groupStamp(c).regionCode("11010532").memo("제외할 지역"), "2025-01-02T05:00:00Z");
        saveAt(classified().memo("그룹 없음").state(EmotionState.IRRITATED), "2025-01-02T05:00:00Z");
        saveAt(classified().groupStamp(a).memo("시작 직전").state(EmotionState.IRRITATED), "2025-01-01T14:59:59.999999Z");
        saveAt(classified().groupStamp(b).memo("종료 직후").state(EmotionState.IRRITATED), "2025-01-02T15:00:00Z");
        saveAt(classified().groupStamp(a).state(EmotionState.IRRITATED), "2025-01-02T05:00:00Z");
        saveAt(classified().groupStamp(b).memo("삭제").state(EmotionState.IRRITATED), "2025-01-02T05:00:00Z").delete();
        saveAt(classified().groupStamp(b).memo("미래").state(EmotionState.IRRITATED), "2100-01-02T05:00:00Z");
        entityManager.persist(EmotionBlock.builder().blockerDeviceId(requester.getId()).emotionId(blocked.getId()).build());
        entityManager.persist(DeviceBlock.builder().blockerDeviceId(requester.getId()).blockedDeviceId(author.getId())
                .originEmotionId(blocked.getId()).build());
        entityManager.flush();
        entityManager.clear();
        LocalDate date = LocalDate.of(2025, 1, 2);
        var bounds = level == RegionLevel.EMD ? EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8)
                : EmotionSearchBounds.of(130, 40, 132, 42);
        var filter = EmotionSearchFilter.of(date, date, contentType, null,
                List.of(b.getGroup().getPublicId(), a.getGroup().getPublicId(), b.getGroup().getPublicId()));

        // when
        var combined = service.findRegionMap(bounds, level, requester.getPublicId(), filter).stream()
                .collect(Collectors.toMap(item -> item.region().code(), EmotionRegionMapItemView::summary));
        long aCount = service.findRegionMap(bounds, level, requester.getPublicId(),
                EmotionSearchFilter.of(date, date, contentType, null, List.of(a.getGroup().getPublicId()))).stream()
                .mapToLong(item -> item.summary().totalCount()).sum();
        long bCount = service.findRegionMap(bounds, level, requester.getPublicId(),
                EmotionSearchFilter.of(date, date, contentType, null, List.of(b.getGroup().getPublicId()))).stream()
                .mapToLong(item -> item.summary().totalCount()).sum();

        // then: 개인 차단은 집계에 적용하지 않으며, 그룹 중복 지정으로 개수가 늘어나지 않는다.
        assertThat(aCount).isEqualTo(total / 2);
        assertThat(bCount).isEqualTo(total / 2);
        assertThat(combined.values().stream().mapToLong(RegionEmotionSummary::totalCount).sum()).isEqualTo(aCount + bCount);
        Map<String, RegionEmotionSummary> expected = level == RegionLevel.EMD ? switch (contentType) {
            case ALL -> Map.of(EMD, RegionEmotionSummary.of(4, EmotionState.ANGRY),
                    "11010531", RegionEmotionSummary.of(2, EmotionState.DISCOURAGED));
            case MEMO -> Map.of(EMD, RegionEmotionSummary.of(3, EmotionState.ANGRY),
                    "11010531", RegionEmotionSummary.of(1, null));
            case AUDIO -> Map.of(EMD, RegionEmotionSummary.of(1, EmotionState.DISCOURAGED),
                    "11010531", RegionEmotionSummary.of(1, EmotionState.DISCOURAGED));
        } : Map.of(level == RegionLevel.SIDO ? "11" : "11010", RegionEmotionSummary.of(total, state));
        assertThat(combined).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 필터는_지역_전체와_하위_지역에_적용하고_필터_후_0건인_지역은_생략한다(RegionLevel level) {
        // given: 두 읍면동의 화면 밖 기록을 합산하되 AUDIO만 선택한다.
        addRegion("11010531", RegionLevel.EMD, "11010");
        save(classified().memo("제외할 메모").state(EmotionState.IRRITATED));
        save(classified().audio(Audio.builder().objectKey("first.m4a").build()).state(EmotionState.ANGRY));
        save(classified().regionCode("11010531").audio(Audio.builder().objectKey("second.m4a").build())
                .state(EmotionState.DISCOURAGED));
        entityManager.flush();
        var filter = EmotionSearchFilter.of(null, null, EmotionContentFilter.AUDIO, null, null);
        var bounds = EmotionSearchBounds.of(127.8, 37.2, 128.2, 37.8);

        // when / then
        var results = service.findRegionMap(bounds, level, requester.getPublicId(), filter);
        assertThat(results.stream().mapToLong(item -> item.summary().totalCount()).sum()).isEqualTo(2);
        if (level != RegionLevel.EMD) {
            assertThat(results).singleElement().satisfies(item ->
                    assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(2, EmotionState.ANGRY)));
        }
        LocalDate noRecords = LocalDate.of(1, 1, 1);
        assertThat(service.findRegionMap(bounds, level, requester.getPublicId(),
                EmotionSearchFilter.of(noRecords, noRecords, null, null, null))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void 선택한_그룹_중_하나라도_미존재나_삭제되었으면_빈_화면에서도_거부한다(boolean deleted, boolean nationwide) {
        // given
        UUID groupId = UUID.randomUUID();
        if (deleted) {
            Group group = 기본_그룹_빌더().build();
            entityManager.persist(group);
            group.delete(Instant.now());
            groupId = group.getPublicId();
        }
        Group valid = 기본_그룹_빌더().name("유효 그룹").build();
        entityManager.persist(valid);
        entityManager.flush();
        var filter = EmotionSearchFilter.from(List.of(valid.getPublicId(), groupId));

        // when / then
        assertThatThrownBy(() -> {
            if (nationwide) {
                service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.SIDO, requester.getPublicId(), filter);
            } else {
                service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42),
                        RegionLevel.EMD, requester.getPublicId(), filter);
            }
        })
                .isInstanceOfSatisfying(GroupException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(value = RegionLevel.class, names = {"SIDO", "SIGUNGU"})
    void 전국_조회는_bbox와_무관하게_경계_공간_연산_없이_코드순으로_반환한다(RegionLevel level) throws Exception {
        // given: 떨어진 두 지역에 내용이 있고 세 번째 지역에는 NONE만 있다.
        addRegion("10", RegionLevel.SIDO, null);
        addRegion("10010", RegionLevel.SIGUNGU, "10");
        addRegion("10010530", RegionLevel.EMD, "10010");
        addRegion("12", RegionLevel.SIDO, null);
        addRegion("12010", RegionLevel.SIGUNGU, "12");
        addRegion("12010530", RegionLevel.EMD, "12010");
        jdbc.sql("""
                UPDATE regions SET boundary = ST_Translate(boundary, 2, -2),
                    display_point = ST_Translate(display_point, 2, -2) WHERE code LIKE '10%'
                """).update();
        save(classified().memo("메모").state(EmotionState.ANGRY));
        save(classified().regionCode("10010530").audio(Audio.builder().objectKey("national.m4a").build())
                .state(EmotionState.DISCOURAGED));
        save(classified().regionCode("12010530"));
        entityManager.flush();
        // EMD 호환 경로는 여전히 화면 교차에 따라 다른 결과를 반환한다.
        assertThat(findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.EMD, null)).isEmpty();
        assertThat(findRegionMap(EmotionSearchBounds.of(126.5, 37.5, 127.5, 38.5), RegionLevel.EMD, null))
                .extracting(item -> item.region().code()).containsExactly(EMD);
        // 경계 컬럼을 읽는 회귀는 SQL 실패로 드러나며 변경은 테스트 트랜잭션에서 롤백된다.
        jdbc.sql("ALTER TABLE regions RENAME COLUMN boundary TO unavailable_boundary").update();

        // when
        var results = service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), level, requester.getPublicId(),
                EmotionSearchFilter.from(null));
        var timings = inRequest(() -> {
            assertThat(findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, null)).isEqualTo(results);
            assertThat(findRegionMap(EmotionSearchBounds.of(-180, -90, 180, 90), level, null)).isEqualTo(results);
            assertThat(findRegionMap(EmotionSearchBounds.of(170, 37, -170, 39), level, null)).isEqualTo(results);
        });
        assertThat(timings).containsOnlyKeys(PheeeewRequestTimingStage.REGIONS_SERVICE,
                PheeeewRequestTimingStage.REGIONS_AGGREGATION_READY, PheeeewRequestTimingStage.REGIONS_SUMMARY);
        assertThat(timings.values()).allSatisfy(value -> assertThat(value.count()).isEqualTo(3));

        // then: 선택 계층만 반환하고 원래 표시 좌표와 지역 메타데이터를 유지한다.
        String firstCode = level == RegionLevel.SIDO ? "10" : "10010";
        String secondCode = level == RegionLevel.SIDO ? "11" : "11010";
        assertThat(results).extracting(item -> item.region().code()).containsExactly(firstCode, secondCode);
        assertThat(results.getFirst().region()).satisfies(region -> {
            assertThat(region.level()).isEqualTo(level);
            assertThat(region.name()).isEqualTo("합산 검증 지역");
            assertThat(region.parentCode()).isEqualTo(level == RegionLevel.SIDO ? null : "10");
            assertThat(region.longitude()).isEqualTo(129);
            assertThat(region.latitude()).isEqualTo(36);
        });
        assertThat(results).extracting(EmotionRegionMapItemView::summary).containsExactly(
                RegionEmotionSummary.of(1, EmotionState.DISCOURAGED), RegionEmotionSummary.of(1, EmotionState.ANGRY));
    }

    @Test
    void 전국_조회도_기기_확인을_그룹_확인보다_먼저_수행한다() {
        // given
        var filter = EmotionSearchFilter.from(List.of(UUID.randomUUID()));

        // when / then
        assertThatThrownBy(() -> service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.SIDO, UUID.randomUUID(), filter))
                .isInstanceOfSatisfying(DeviceException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "1,false", "1,true", "2,false", "2,true"})
    void 전국_조회도_경계나_백필_미준비면_지역이_없어도_거부한다(int status, boolean empty) {
        // given
        String sql = switch (status) {
            case 0 -> "UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL";
            case 1 -> "UPDATE region_datasets SET backfill_verified_at = NULL";
            default -> "DELETE FROM region_datasets";
        };
        jdbc.sql(sql).update();
        if (empty) {
            jdbc.sql("DELETE FROM regions").update();
        }
        var filter = EmotionSearchFilter.from(null);

        // when / then
        assertThatThrownBy(() -> service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.SIDO, requester.getPublicId(), filter))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
    }

    @Test
    void 전국_조회는_준비된_데이터에_내용_있는_감정이_없으면_빈_목록을_반환한다() {
        // given
        var filter = EmotionSearchFilter.from(null);

        // when / then
        assertThat(service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.SIDO, requester.getPublicId(), filter)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = RegionLevel.class, names = {"SIDO", "SIGUNGU"})
    void 전국_조회_이후에도_지역_코드_지정_조회는_해당_지역만_집계한다(RegionLevel level) {
        // given
        addRegion("21", RegionLevel.SIDO, null);
        addRegion("21010", RegionLevel.SIGUNGU, "21");
        addRegion("21010530", RegionLevel.EMD, "21010");
        save(classified().memo("선택 지역").state(EmotionState.ANGRY));
        save(classified().regionCode("21010530").memo("다른 지역").state(EmotionState.IRRITATED));
        entityManager.flush();
        String code = level == RegionLevel.SIDO ? "11" : "11010";
        var filter = EmotionSearchFilter.of(null, null, null, null, null);

        // when / then: 서비스의 전국 결과와 기존 저장소의 부분 지역 계약을 함께 확인한다.
        assertThat(findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), level, null)).hasSize(2);
        assertThat(emotionRepository.findSummariesByRegionCodes(
                List.of(code), level, filter, requester.getId(), Instant.now()))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.regionCode()).isEqualTo(code);
                    assertThat(summary.totalCount()).isEqualTo(1);
                    assertThat(summary.representativeState()).isEqualTo(EmotionState.ANGRY);
                });
        assertThat(emotionRepository.findSummariesByRegionCodes(
                List.of(), level, filter, requester.getId(), Instant.now())).isEmpty();
    }

    @Test
    void 전국_집계_저장소는_읍면동의_지역_제한을_생략하지_않는다() {
        // given
        var filter = EmotionSearchFilter.of(null, null, null, null, null);

        // when / then
        assertThatThrownBy(() -> emotionRepository.findNationalSummaries(
                RegionLevel.EMD, filter, requester.getId(), Instant.now()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("전국 집계는 시도와 시군구만 지원합니다.");
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 외부_쓰기_트랜잭션의_감정_수정은_집계_전에_자동_flush한다(RegionLevel level) {
        // given: DataJpaTest의 외부 쓰기 트랜잭션에 서비스가 참여한다.
        Emotion emotion = save(classified().memo("변경 전").state(EmotionState.ANGRY));
        entityManager.flush();
        emotion.update(EmotionState.DISCOURAGED, EmotionContent.builder().audio(
                Audio.builder().objectKey("changed.m4a").build()).build(), null);
        assertThat(jdbc.sql("SELECT state FROM emotions WHERE id = :id").param("id", emotion.getId())
                .query(String.class).single()).isEqualTo("ANGRY");
        var filter = EmotionSearchFilter.of(null, null, EmotionContentFilter.AUDIO, null, null);

        // when: 명시적인 flush 없이 내용 필터와 대표 감정 모두 변경분을 읽는다.
        var result = service.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level,
                requester.getPublicId(), filter);

        // then
        assertThat(result).singleElement().satisfies(item ->
                assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(1, EmotionState.DISCOURAGED)));
        assertThat(jdbc.sql("SELECT state FROM emotions WHERE id = :id").param("id", emotion.getId())
                .query(String.class).single()).isEqualTo("DISCOURAGED");
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 외부_쓰기_트랜잭션의_감정_삭제는_자동_flush하여_0건_지역을_생략한다(RegionLevel level) {
        // given
        Emotion emotion = save(classified().memo("삭제 전").state(EmotionState.ANGRY));
        entityManager.flush();
        emotion.delete();

        // when / then
        assertThat(findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, null)).isEmpty();
        assertThat(jdbc.sql("SELECT deleted_at IS NOT NULL FROM emotions WHERE id = :id")
                .param("id", emotion.getId()).query(Boolean.class).single()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 그룹_집계는_감정의_그룹_변경과_스탬프의_자동_flush를_유지한다(RegionLevel level) {
        // given
        Group group = 기본_그룹_빌더().build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        Emotion emotion = save(classified().memo("메모").state(EmotionState.ANGRY));
        entityManager.flush();
        emotion.update(EmotionState.DISCOURAGED, emotion.getContent(), stamp);

        // when / then: 기존 행의 연관 변경이 그룹 필터에 반영된다.
        assertThat(findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, group.getPublicId()))
                .singleElement().satisfies(item ->
                        assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(1, EmotionState.DISCOURAGED)));

        // 감정 변경 없이 스탬프만 수정해도 GROUP 쿼리의 동기화 대상에 포함한다.
        stamp.change("수정", stamp.getTextColor(), stamp.getBackgroundColor(), stamp.getFrame());
        findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, group.getPublicId());
        assertThat(jdbc.sql("SELECT text FROM group_stamps WHERE id = :id").param("id", stamp.getId())
                .query(String.class).single()).isEqualTo("수정");
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 집계는_외부_트랜잭션의_MANUAL_flush_설정을_강제로_덮어쓰지_않는다(RegionLevel level) {
        // given: 읽기 전용 트랜잭션에서도 사용하는 MANUAL 모드를 보존한다.
        Emotion emotion = save(classified().memo("메모").state(EmotionState.ANGRY));
        entityManager.flush();
        Session session = entityManager.unwrap(Session.class);
        FlushMode previous = session.getHibernateFlushMode();
        session.setHibernateFlushMode(FlushMode.MANUAL);
        emotion.delete();

        try {
            // when / then
            assertThat(findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, null))
                    .singleElement().satisfies(item ->
                            assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(1, EmotionState.ANGRY)));
            assertThat(jdbc.sql("SELECT deleted_at IS NULL FROM emotions WHERE id = :id")
                    .param("id", emotion.getId()).query(Boolean.class).single()).isTrue();
            assertThat(session.getHibernateFlushMode()).isEqualTo(FlushMode.MANUAL);
        } finally {
            session.setHibernateFlushMode(previous);
        }
    }

    private GroupStamp saveStamp(String name) {
        Group group = 기본_그룹_빌더().name(name).build();
        entityManager.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        entityManager.persist(stamp);
        return stamp;
    }

    private List<EmotionRegionMapItemView> findRegionMap(EmotionSearchBounds bounds, RegionLevel level, UUID groupId) {
        return service.findRegionMap(bounds, level, requester.getPublicId(),
                EmotionSearchFilter.from(groupId == null ? null : List.of(groupId)));
    }

    private Emotion saveAt(Emotion.EmotionBuilder builder, String createdAt) {
        Emotion saved = save(builder);
        entityManager.flush();
        jdbc.sql("UPDATE emotions SET created_at = CAST(:createdAt AS timestamptz) WHERE id = :id")
                .param("createdAt", createdAt).param("id", saved.getId()).update();
        return saved;
    }

    private Map<RequestTimingStage, Measurement> inRequest(Runnable operation) throws Exception {
        var request = new MockHttpServletRequest();
        var snapshot = new AtomicReference<Map<RequestTimingStage, Measurement>>();
        new RequestLoggingConfiguration().requestLoggingFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            try {
                operation.run();
                snapshot.set(RequestTiming.snapshot(request));
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        });
        assertThat(RequestTiming.snapshot(request)).isEmpty();
        return snapshot.get();
    }

    private Map<String, RegionEmotionSummary> summaries(RegionLevel level, UUID groupId) {
        return findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, groupId).stream()
                .collect(Collectors.toMap(item -> item.region().code(), item -> item.summary()));
    }

    private Map<String, Long> counts(RegionLevel level, UUID groupId) {
        return summaries(level, groupId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().totalCount()));
    }

    private long 집계_준비_조회_수(String outcome) {
        return meterRegistry.get("pheeeew.region.query")
                .tags("operation", "aggregation_ready", "outcome", outcome).timer().count();
    }

    private void addRegion(String code, RegionLevel level, String parentCode) {
        // 공간 분류가 아닌, 저장된 행정동 참조의 계층 합산을 검증하는 합성 지역이다.
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT :code, :level, '합산 검증 지역', :parentCode, boundary, display_point
                FROM regions WHERE code = '11010530'
                """).param("code", code).param("level", level.name()).param("parentCode", parentCode).update();
    }

    private Emotion.EmotionBuilder classified() {
        return 기본_한숨_빌더().regionCode(EMD).regionClassifiedAt(Instant.now());
    }

    private Emotion save(Emotion.EmotionBuilder builder) {
        Emotion emotion = builder.build();
        entityManager.persist(emotion);
        return emotion;
    }
}
