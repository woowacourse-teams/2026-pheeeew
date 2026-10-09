package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

@PostgisDataJpaTest
@Import({EmotionQueryService.class, AopAutoConfiguration.class, RegionQueryMetrics.class, RegionQueryMetricsAspect.class})
class EmotionRegionCountIntegrationTest {

    private static final String EMD = "11010530";

    @Autowired
    private EmotionQueryService service;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("""
                UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP,
                    backfill_verified_at = CURRENT_TIMESTAMP
                """).update();
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 내용_있는_지역_지도는_NONE을_개수와_대표_감정에서_함께_제외한다(RegionLevel level) {
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
        assertThat(service.findContentRegionMap(bounds, level, null)).singleElement().satisfies(item ->
                assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(2L, EmotionState.ANGRY)));
        assertThat(service.findRegionMap(bounds, level, null)).singleElement().satisfies(item ->
                assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(7L, EmotionState.FRUSTRATED)));
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
        assertThat(service.findContentRegionMap(bounds, RegionLevel.EMD, target.getPublicId()))
                .singleElement().satisfies(item ->
                        assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(1L, EmotionState.DISCOURAGED)));
        assertThat(service.findContentRegionMap(bounds, RegionLevel.EMD, other.getPublicId())).isEmpty();
        assertThat(service.findRegionMap(bounds, RegionLevel.EMD, other.getPublicId())).hasSize(1);
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
                save(classified().regionCode(emdCodes.get(index)).state(state));
            }
        }
        entityManager.flush();
        Map<String, Long> expected = switch (level) {
            case EMD -> Map.of(EMD, 1L, "11010531", 2L, "11020530", 3L, "21010530", 4L);
            case SIGUNGU -> Map.of("11010", 3L, "11020", 3L, "21010", 4L);
            case SIDO -> Map.of("11", 6L, "21", 4L);
        };
        var codes = expected.keySet().stream().flatMap(code -> Stream.of(code, code)).toList();

        // when / then: 중복 요청 코드가 하위 감정 개수를 늘리지 않는다.
        var summaries = service.findSummariesByRegionCodes(codes, null);
        assertThat(counts(codes, null)).containsExactlyInAnyOrderEntriesOf(expected);
        Map<String, EmotionState> representatives = switch (level) {
            case EMD -> Map.of(EMD, EmotionState.ANGRY, "11010531", EmotionState.FRUSTRATED,
                    "11020530", EmotionState.FRUSTRATED, "21010530", EmotionState.IRRITATED);
            case SIGUNGU -> Map.of("11010", EmotionState.FRUSTRATED, "11020", EmotionState.FRUSTRATED,
                    "21010", EmotionState.IRRITATED);
            case SIDO -> Map.of("11", EmotionState.FRUSTRATED, "21", EmotionState.IRRITATED);
        };
        representatives.forEach((code, state) -> assertThat(summaries.get(code).representativeState()).isEqualTo(state));
    }

    @Test
    void 상위와_하위_지역을_함께_요청해도_각_지역_안에서는_한번만_센다() {
        // given
        save(classified());
        entityManager.flush();

        // when / then: 서로 겹치는 계층의 결과끼리 다시 합산하지 않는다.
        assertThat(counts(List.of("11", "11010", EMD, "11"), null))
                .containsExactlyInAnyOrderEntriesOf(Map.of("11", 1L, "11010", 1L, EMD, 1L));
        assertThat(service.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD, null))
                .singleElement().satisfies(item -> {
                    assertThat(item.summary().totalCount()).isEqualTo(1L);
                    assertThat(item.summary().representativeState()).isNull();
                });
    }

    @ParameterizedTest
    @EnumSource(RegionLevel.class)
    void 화면_밖_기록과_모든_콘텐츠_및_NULL_상태를_페이지_제한없이_누적한다(RegionLevel level) {
        // given: 화면은 지역 일부만 포함하지만, 기록은 그 화면 밖의 서울시청 좌표에 있다.
        save(classified());
        save(classified().memo("누적 메모").state(EmotionState.FRUSTRATED));
        save(classified().audio(Audio.builder().objectKey("recordings/test.m4a").build()).state(EmotionState.ANGRY));
        for (int i = 0; i < 201; i++) {
            save(classified());
        }
        save(classified().state(EmotionState.FRUSTRATED)).delete();
        Emotion hidden = save(classified().state(EmotionState.FRUSTRATED));
        Emotion future = save(classified().state(EmotionState.FRUSTRATED));
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
        assertThat(service.findRegionMap(bounds, level, null)).singleElement().satisfies(item -> {
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
        Emotion blocked = save(classified().deviceId(author.getId()).groupStamp(targetStamp).state(EmotionState.IRRITATED));
        save(classified().deviceId(author.getId()).groupStamp(targetStamp).state(EmotionState.IRRITATED));
        save(classified().groupStamp(otherStamp).state(EmotionState.ANGRY));
        save(classified().state(EmotionState.ANGRY));
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                SELECT '11010531', level, name, parent_code, boundary, display_point FROM regions WHERE code = '11010530'
                """).update();
        save(classified().regionCode("11010531").state(EmotionState.ANGRY));
        entityManager.persist(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(blocked.getId()).build());
        entityManager.persist(DeviceBlock.builder().blockerDeviceId(viewer.getId()).blockedDeviceId(author.getId())
                .originEmotionId(blocked.getId()).build());
        entityManager.flush();

        // when / then: 같은 스탬프를 공유하는 감정도 각각 한 번씩 그룹별로 센다.
        assertThat(counts(List.of(code, code), null)).containsEntry(code, totalCount).hasSize(1);
        assertThat(counts(List.of("11010531"), null)).containsEntry("11010531", 1L).hasSize(1);
        assertThat(service.findSummariesByRegionCodes(List.of(code), target.getPublicId()))
                .containsEntry(code, RegionEmotionSummary.of(2L, EmotionState.IRRITATED));
        assertThat(service.findSummariesByRegionCodes(List.of(code), other.getPublicId()))
                .containsEntry(code, RegionEmotionSummary.of(1L, EmotionState.ANGRY));
        assertThat(service.findSummariesByRegionCodes(List.of(code), null).get(code).representativeState())
                .isEqualTo(EmotionState.ANGRY);
        assertThat(counts(List.of(code), UUID.randomUUID())).isEmpty();
        assertThat(service.findMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 39), viewer.getPublicId(), target.getPublicId(), null).items()).isEmpty();
        RegionLevel level = switch (code.length()) {
            case 2 -> RegionLevel.SIDO;
            case 5 -> RegionLevel.SIGUNGU;
            default -> RegionLevel.EMD;
        };
        assertThat(service.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, target.getPublicId()))
                .singleElement().satisfies(item -> {
                    assertThat(item.region().code()).isEqualTo(code);
                    assertThat(item.summary()).isEqualTo(RegionEmotionSummary.of(2L, EmotionState.IRRITATED));
                });
        assertThat(service.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), level, UUID.randomUUID())).isEmpty();
    }

    @Test
    void 빈_지역은_제외하고_삭제_후_재조회도_코드순의_완전한_목록을_반환한다() {
        // given: 같은 화면에 3개 지역이 걸치지만 기록은 두 지역에만 있다.
        addRegion("11010529", RegionLevel.EMD, "11010");
        addRegion("11010531", RegionLevel.EMD, "11010");
        Emotion removed = save(classified().state(EmotionState.FRUSTRATED));
        save(classified().regionCode("11010529").state(EmotionState.ANGRY));
        entityManager.flush();
        var bounds = EmotionSearchBounds.of(126, 37, 128, 39);

        // when / then
        assertThat(service.findRegionMap(bounds, RegionLevel.EMD, null))
                .extracting(item -> item.region().code()).containsExactly("11010529", EMD);
        removed.delete();
        entityManager.flush();
        assertThat(service.findRegionMap(bounds, RegionLevel.EMD, null))
                .extracting(item -> item.region().code()).containsExactly("11010529");
        assertThat(service.findRegionMap(EmotionSearchBounds.of(130, 40, 132, 42), RegionLevel.EMD, null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"ANGRY,DISCOURAGED", "DISCOURAGED,EXHAUSTED", "EXHAUSTED,FRUSTRATED", "FRUSTRATED,IRRITATED"})
    void 동률이면_확정한_감정_순서를_적용한다(EmotionState first, EmotionState second) {
        // given: 삽입 순서와 Enum 선언 순서에 의존하지 않는다.
        save(classified().state(second));
        save(classified().state(first));
        entityManager.flush();

        // when / then
        assertThat(service.findSummariesByRegionCodes(List.of(EMD), null))
                .containsEntry(EMD, RegionEmotionSummary.of(2L, first));
    }

    @Test
    void 모든_감정이_동률이면_ANGRY를_대표로_고른다() {
        // given
        for (EmotionState state : EmotionState.values()) {
            save(classified().state(state));
        }
        entityManager.flush();

        // when / then
        assertThat(service.findSummariesByRegionCodes(List.of(EMD), null))
                .containsEntry(EMD, RegionEmotionSummary.of(5L, EmotionState.ANGRY));
    }

    @Test
    void 동률_순서보다_실제_빈도가_우선한다() {
        // given: 동률 순서가 뒤인 감정일수록 더 많은 기록을 둔다.
        var states = List.of(EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
                EmotionState.FRUSTRATED, EmotionState.IRRITATED);
        for (int index = 0; index < states.size(); index++) {
            for (int count = 0; count <= index; count++) {
                save(classified().state(states.get(index)));
            }
        }
        entityManager.flush();

        // when / then
        assertThat(service.findSummariesByRegionCodes(List.of(EMD), null))
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
        assertThatThrownBy(() -> counts(empty ? List.of() : List.of(EMD), null))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
        assertThat(집계_준비_조회_수("success")).isEqualTo(successCount + 1);
        assertThat(집계_준비_조회_수("error")).isEqualTo(errorCount);
        var bounds = empty ? EmotionSearchBounds.of(130, 40, 132, 42) : EmotionSearchBounds.of(126, 37, 128, 39);
        assertThatThrownBy(() -> service.findRegionMap(bounds, RegionLevel.EMD, null))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_REGION_DATA_UNAVAILABLE));
    }

    @Test
    void 집계할_코드나_기록이_없으면_내부_개수_맵도_비어있다() {
        // given / when / then
        long successCount = 집계_준비_조회_수("success");
        assertThat(counts(List.of(), null)).isEmpty();
        assertThat(counts(List.of(EMD), null)).isEmpty();
        assertThat(service.findRegionMap(EmotionSearchBounds.of(126, 37, 128, 39), RegionLevel.EMD, null)).isEmpty();
        assertThat(집계_준비_조회_수("success")).isEqualTo(successCount + 3);
    }

    @Test
    void 집계_준비_조회_SQL_장애를_실패로_기록하고_예외를_유지한다() {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        jdbc.sql("ALTER TABLE region_datasets RENAME TO unavailable_region_datasets").update();
        long errorCount = 집계_준비_조회_수("error");

        // when / then
        assertThatThrownBy(() -> counts(List.of(EMD), null)).isInstanceOf(DataAccessException.class);
        assertThat(집계_준비_조회_수("error")).isEqualTo(errorCount + 1);
    }

    @Test
    void 집계_SQL_장애를_정상적인_빈_개수로_숨기지_않는다() {
        // given: 테스트 트랜잭션 종료 시 DDL도 롤백된다.
        jdbc.sql("ALTER TABLE emotions RENAME TO unavailable_emotions").update();

        // when / then
        assertThatThrownBy(() -> counts(List.of(EMD), null)).isInstanceOf(DataAccessException.class);
    }

    private Map<String, Long> counts(List<String> regionCodes, UUID groupId) {
        return service.findSummariesByRegionCodes(regionCodes, groupId).entrySet().stream()
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
