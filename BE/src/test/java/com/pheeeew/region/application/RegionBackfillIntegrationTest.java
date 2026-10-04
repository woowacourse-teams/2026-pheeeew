package com.pheeeew.region.application;

import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.region.domain.repository.RegionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegionBackfillIntegrationTest {

    private static final Path SCRIPT = Path.of("scripts/regions/backfill-batch.sql");
    private static final Path VERIFY_SCRIPT = Path.of("scripts/regions/backfill-verify.sql");
    private static final Path START_SCRIPT = Path.of("scripts/regions/reclassification-start.sql");
    private static final Path RECLASSIFY_SCRIPT = Path.of("scripts/regions/reclassification-batch.sql");

    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private RegionClassifier classifier;
    @Autowired
    private RegionRepository regions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
        postgis().copyFileToContainer(MountableFile.forHostPath(SCRIPT), "/tmp/emotion-region-backfill.sql");
        postgis().copyFileToContainer(MountableFile.forHostPath(VERIFY_SCRIPT), "/tmp/emotion-region-backfill-verify.sql");
        postgis().copyFileToContainer(MountableFile.forHostPath(START_SCRIPT), "/tmp/emotion-region-reclassification-start.sql");
        postgis().copyFileToContainer(MountableFile.forHostPath(RECLASSIFY_SCRIPT), "/tmp/emotion-region-reclassification-batch.sql");
    }

    @AfterEach
    void tearDown() {
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("INSERT INTO region_datasets (dataset_key) VALUES ('SGIS_2025_2Q') ON CONFLICT DO NOTHING").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "MEMO", "AUDIO"})
    void 모든_콘텐츠와_삭제된_기록도_분류_필드만_갱신한다(String contentType) {
        // given: 감사 시각·version·내용·좌표 등 모든 나머지 컬럼을 비교한다.
        Emotion row = save(contentType);
        if (contentType.equals("NONE")) {
            jdbc.sql("UPDATE emotions SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id").param("id", row.getId()).update();
        }
        String before = unrelatedFields(row);

        // when / then
        assertThat(backfill(10, row.getId())).isOne();
        assertThat(stored(row).getRegionCode()).isEqualTo("11010530");
        assertThat(stored(row).getRegionClassifiedAt()).isNotNull();
        assertThat(unrelatedFields(row)).isEqualTo(before);
    }

    @Test
    void 배치_크기와_ID_상한을_지키고_재실행해도_완료된_분류는_보존한다() {
        // given
        Emotion first = save("NONE"), second = save("MEMO"), later = save("AUDIO");

        // when / then
        assertThat(backfill(1, second.getId())).isOne();
        var firstTime = stored(first).getRegionClassifiedAt();
        assertThat(stored(second).getRegionClassifiedAt()).isNull();
        assertThat(backfill(10, second.getId())).isOne();
        assertThat(backfill(10, second.getId())).isZero();
        assertThat(stored(first).getRegionClassifiedAt()).isEqualTo(firstTime);
        assertThat(stored(later).getRegionClassifiedAt()).isNull();
        assertThat(jdbc.sql("SELECT backfill_verified_at IS NULL FROM region_datasets").query(Boolean.class).single()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"2, 128, 38", "2, 128, 37", "1, 127.5, 38"})
    void 공유_경계와_겹침은_나중에_삽입한_작은_행정동_코드를_선택한다(double offset, double longitude, double latitude) {
        // given: 이동한 합성 도형으로 공유 경계·꼭짓점·겹침 내부를 구분한다.
        jdbc.sql("INSERT INTO regions (code, level, name, parent_code, boundary, display_point) "
                + "SELECT '11010529', level, name, parent_code, ST_Translate(boundary, :offset, 0), "
                + "ST_Translate(display_point, :offset, 0) FROM regions WHERE code = '11010530'")
                .param("offset", offset).update();
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(longitude, latitude));
        Emotion row = emotions.saveAndFlush(기본_한숨_빌더().location(point).build());
        // when / then
        assertThat(backfill(1, row.getId())).isOne();
        assertThat(stored(row).getRegionCode()).isEqualTo("11010529");
    }

    @Test
    void 경계_밖의_정상_미매칭도_분류_시각을_남기고_다시_처리하지_않는다() {
        // given
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(0, 0));
        Emotion row = emotions.saveAndFlush(기본_한숨_빌더().location(point).build());

        // when / then
        assertThat(backfill(1, row.getId())).isOne();
        assertThat(stored(row).getRegionCode()).isNull();
        assertThat(stored(row).getRegionClassifiedAt()).isNotNull();
        assertThat(backfill(1, row.getId())).isZero();
    }

    @ParameterizedTest
    @CsvSource({"127,38,11010530", "128.005,38,11010530", "128.02,38,", "0,0,"})
    void 앱_분류와_백필은_포함과_인접과_미매칭에_같은_결과를_사용한다(
            double longitude, double latitude, String expectedCode
    ) {
        // given
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(longitude, latitude));
        Emotion row = emotions.saveAndFlush(기본_한숨_빌더().location(point).build());
        String before = unrelatedFields(row);
        var appResult = classifier.classify(point);

        // when
        assertThat(backfill(1, row.getId())).isOne();
        Emotion classified = stored(row);

        // then
        assertThat(appResult.regionCode()).isEqualTo(expectedCode);
        assertThat(classified.getRegionCode()).isEqualTo(appResult.regionCode());
        assertThat(classified.getRegionClassifiedAt()).isNotNull();
        assertThat(unrelatedFields(row)).isEqualTo(before);
        assertThat(backfill(1, row.getId())).isZero();
        assertThat(stored(row).getRegionClassifiedAt()).isEqualTo(classified.getRegionClassifiedAt());
    }

    @Test
    void 이미_미매칭으로_완료된_인접_기록은_일반_백필에서_재분류하지_않는다() {
        // given: 새 함수로 배정할 수 있어도 기존 완료 기록은 별도 재분류 대상이다.
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(128.005, 38));
        Emotion row = emotions.saveAndFlush(기본_한숨_빌더().location(point)
                .regionClassifiedAt(Instant.parse("2026-10-04T00:00:00Z")).build());
        var classifiedAt = stored(row).getRegionClassifiedAt();
        String before = unrelatedFields(row);
        assertThat(classifier.classify(point).regionCode()).isEqualTo("11010530");

        // when / then
        assertThat(backfill(1, row.getId())).isZero();
        assertThat(stored(row).getRegionCode()).isNull();
        assertThat(stored(row).getRegionClassifiedAt()).isEqualTo(classifiedAt);
        assertThat(unrelatedFields(row)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 미검증과_누락_경계는_감정을_처리하지_않고_실패한다(boolean missing) throws Exception {
        // given
        Emotion row = save("NONE");
        jdbc.sql(missing ? "DELETE FROM region_datasets" : "UPDATE region_datasets SET boundaries_verified_at = NULL").update();

        // when / then
        var result = executeBatch("1", row.getId().toString());
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("검증된 SGIS_2025_2Q 경계가 필요합니다.");
        assertThat(stored(row).getRegionClassifiedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"0|0", "-1|0", "1|-1", "abc|0", "2147483648|0", "1; DELETE FROM public.emotions|0"})
    void 잘못된_입력은_비정상_종료하고_기존_감정을_보존한다(String batchSize, String upperId) throws Exception {
        // given
        Emotion row = save("MEMO");
        String before = unrelatedFields(row);

        // when / then
        assertThat(executeBatch(batchSize, upperId).getExitCode()).isNotZero();
        assertThat(stored(row).getRegionClassifiedAt()).isNull();
        assertThat(unrelatedFields(row)).isEqualTo(before);
    }

    @Test
    void 배치_SQL_오류는_비정상_종료하고_전체_배치를_롤백한다() throws Exception {
        // given: 두 번째 감정 분류에서 실패할 제약을 만든다.
        Emotion first = save("MEMO"), second = save("AUDIO");
        jdbc.sql("ALTER TABLE emotions ADD CONSTRAINT ck_backfill_test_failure CHECK (id <> "
                + second.getId() + " OR region_classified_at IS NULL)").update();
        try {
            // when / then
            var result = executeBatch("10", second.getId().toString());
            assertThat(result.getExitCode()).isNotZero();
            assertThat(result.getStderr()).contains("ck_backfill_test_failure");
            assertThat(stored(first).getRegionClassifiedAt()).isNull();
            assertThat(stored(second).getRegionClassifiedAt()).isNull();
        } finally {
            jdbc.sql("ALTER TABLE emotions DROP CONSTRAINT ck_backfill_test_failure").update();
        }
    }

    @Test
    void 오래된_JPA_객체를_수정해도_다른_트랜잭션의_백필_결과를_보존한다() {
        // given
        Emotion row = save("MEMO");

        // when: T1 조회 → T2 백필 커밋 → T1 내용 수정 커밋.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Emotion stale = stored(row);
            assertThat(backfillSeparately(row.getId())).isOne();
            assertThat(stale.getRegionClassifiedAt()).isNull();
            stale.update(EmotionState.ANGRY, EmotionContent.builder().memo("수정 메모").build(), null);
        });

        // then
        assertThat(stored(row).getRegionCode()).isEqualTo("11010530");
        assertThat(stored(row).getRegionClassifiedAt()).isNotNull();
        assertThat(stored(row).getMemo()).isEqualTo("수정 메모");
        assertThat(stored(row).getState()).isEqualTo(EmotionState.ANGRY);
    }

    @Test
    void 잠긴_선두_행은_건너뛰고_잠금_해제_후_다시_처리한다() {
        // given
        Emotion first = save("NONE"), second = save("MEMO");

        // when / then: 잠긴 행만 있는 호출의 0은 완료가 아니다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.sql("SELECT id FROM emotions WHERE id = :id FOR UPDATE").param("id", first.getId()).query(Long.class).single();
            assertThat(backfillSeparately(first.getId())).isZero();
            assertThat(stored(first).getRegionClassifiedAt()).isNull();
            assertThat(backfillSeparately(second.getId())).isOne();
        });
        assertThat(stored(second).getRegionClassifiedAt()).isNotNull();
        assertThat(backfill(10, second.getId())).isOne();
        assertThat(stored(first).getRegionClassifiedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 동시_배치는_변경_없이_실패하고_커밋이나_롤백_후에는_재실행할_수_있다(boolean commit) throws Exception {
        // given: 첫 배치 SQL을 실행하되 트랜잭션은 아직 끝내지 않는다.
        Emotion row = save("MEMO");
        String before = unrelatedFields(row);
        try (var executor = Executors.newSingleThreadExecutor(); var first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.execute(Files.readString(SCRIPT).replace(":'batch_size'", "'1'")
                        .replace(":'upper_id'", "'" + row.getId() + "'"));

                // when / then: 다른 연결의 실제 psql 배치는 기다리지 않고 실패한다.
                var second = executor.submit(() -> executeBatch("1", row.getId().toString())).get(5, TimeUnit.SECONDS);
                assertThat(second.getExitCode()).isNotZero();
                assertThat(second.getStderr()).contains("다른 감정 행정동 백필 배치가 실행 중입니다.");
                assertThat(stored(row).getRegionClassifiedAt()).isNull();
                assertThat(unrelatedFields(row)).isEqualTo(before);
                if (commit) {
                    first.commit();
                } else {
                    first.rollback();
                }
            } finally {
                first.rollback();
            }
        }
        assertThat(backfill(1, row.getId())).isEqualTo(commit ? 0 : 1);
        assertThat(stored(row).getRegionClassifiedAt()).isNotNull();
    }

    @Test
    void 정상_미매칭도_완료로_인정하고_재검증은_최초_완료_시각을_보존한다() throws Exception {
        // given
        Emotion matched = save("MEMO");
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(0, 0));
        Emotion unmatched = emotions.saveAndFlush(기본_한숨_빌더().location(point).build());
        assertThat(backfill(10, unmatched.getId())).isEqualTo(2);
        String before = unrelatedFields(matched);
        var classifiedAt = stored(matched).getRegionClassifiedAt();

        // when / then
        var first = executeVerification();
        assertThat(first.getExitCode()).withFailMessage(first.getStderr()).isZero();
        assertThat(verificationIsMissing()).isFalse();
        var second = executeVerification();
        assertThat(second.getExitCode()).withFailMessage(second.getStderr()).isZero();
        assertThat(second.getStdout()).isEqualTo(first.getStdout());
        assertThat(stored(unmatched).getRegionCode()).isNull();
        assertThat(stored(unmatched).getRegionClassifiedAt()).isNotNull();
        assertThat(stored(matched).getRegionClassifiedAt()).isEqualTo(classifiedAt);
        assertThat(unrelatedFields(matched)).isEqualTo(before);
    }

    @Test
    void 상한_밖의_삭제된_미분류도_전체_완료를_거부한다() throws Exception {
        // given
        Emotion first = save("NONE"), later = save("MEMO");
        jdbc.sql("UPDATE emotions SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id").param("id", later.getId()).update();
        assertThat(backfill(10, first.getId())).isOne();
        assertThat(backfill(10, first.getId())).isZero();

        // when / then
        var result = executeVerification();
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("미분류 감정이 남아 있습니다.");
        assertThat(verificationIsMissing()).isTrue();
        assertThat(stored(later).getRegionClassifiedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 경계_미검증이나_누락이면_빈_감정_테이블도_완료로_표시하지_않는다(boolean missing) throws Exception {
        // given
        jdbc.sql(missing ? "DELETE FROM region_datasets" : "UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        // when / then
        var result = executeVerification();
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("검증된 SGIS_2025_2Q 경계가 필요합니다.");
        assertThat(verificationIsMissing()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 진행_중_미분류_등록과_충돌하면_실패하고_등록_종료_후_전체를_다시_검사한다(boolean commit) {
        // given / when: 다른 연결에서 아직 커밋하지 않은 INSERT도 완료를 막는다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            save("NONE");
            var result = verify();
            assertThat(result.getExitCode()).isNotZero();
            assertThat(result.getStderr()).contains("could not obtain lock on relation \"public.emotions\"");
            assertThat(verificationIsMissing()).isTrue();
            if (!commit) {
                status.setRollbackOnly();
            }
        });
        // then: 커밋된 미분류는 백필이 필요하며, 롤백된 등록은 남지 않는다.
        if (commit) {
            assertThat(verify().getStderr()).contains("미분류 감정이 남아 있습니다.");
            assertThat(backfill(10, Long.MAX_VALUE)).isOne();
        }
        assertThat(verify().getExitCode()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 완료_검증_중에는_동시_배치와_검증과_감정_쓰기를_막고_종료_후_잠금을_해제한다(boolean commit) throws Exception {
        // given: 완료 SQL 전체를 실행하되 커밋은 보류한다.
        Emotion row = save("MEMO");
        assertThat(backfill(1, row.getId())).isOne();
        String before = unrelatedFields(row);
        try (var first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.execute(Files.readString(VERIFY_SCRIPT));
                // when / then
                var batch = executeBatch("1", row.getId().toString());
                assertThat(batch.getExitCode()).isNotZero();
                assertThat(batch.getStderr()).contains("다른 감정 행정동 백필 배치가 실행 중입니다.");
                var verification = executeVerification();
                assertThat(verification.getExitCode()).isNotZero();
                assertThat(verification.getStderr()).contains("다른 감정 행정동 백필 배치가 실행 중입니다.");
                for (var result : new ExecResult[]{executeReclassificationStart(), executeReclassificationBatch("1", row.getId().toString())}) {
                    assertThat(result.getExitCode()).isNotZero();
                    assertThat(result.getStderr()).contains("다른 감정 행정동 백필 배치가 실행 중입니다.");
                }
                try (var writer = dataSource.getConnection(); var update = writer.createStatement()) {
                    update.execute("SET lock_timeout = '200ms'");
                    assertThatThrownBy(() -> update.executeUpdate("UPDATE emotions SET state = 'ANGRY' WHERE id = " + row.getId()))
                            .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("55P03");
                }
                assertThat(verificationIsMissing()).isTrue();
                assertThat(unrelatedFields(row)).isEqualTo(before);
                if (commit) {
                    first.commit();
                }
            } finally {
                first.rollback();
            }
        }
        assertThat(verificationIsMissing()).isEqualTo(!commit);
        assertThat(executeVerification().getExitCode()).isZero();
        jdbc.sql("UPDATE emotions SET state = 'ANGRY' WHERE id = :id").param("id", row.getId()).update();
        assertThat(stored(row).getState()).isEqualTo(EmotionState.ANGRY);
    }

    @Test
    void 완료_후_분류를_생략한_등록은_차단하지_않지만_재검증은_실패하고_시각을_보존한다() throws Exception {
        // given: 운영 전제를 깨는 구버전 writer를 재현한다.
        assertThat(executeVerification().getExitCode()).isZero();
        String time = jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single();
        save("NONE");
        // when / then
        var result = executeVerification();
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("미분류 감정이 남아 있습니다.");
        assertThat(jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single()).isEqualTo(time);
    }

    @Test
    void 재분류_시작은_집계만_닫고_경계_검증과_감정과_신규_분류를_보존한다() throws Exception {
        // given
        Emotion row = save("MEMO");
        assertThat(backfill(1, row.getId())).isOne();
        assertThat(executeVerification().getExitCode()).isZero();
        assertThat(regions.isAggregationReady()).isTrue();
        String boundaryTime = jdbc.sql("SELECT boundaries_verified_at::text FROM region_datasets").query(String.class).single();
        String before = jdbc.sql("SELECT to_jsonb(e)::text FROM emotions e WHERE id = :id")
                .param("id", row.getId()).query(String.class).single();

        // when
        var result = executeReclassificationStart();

        // then: 전환 중 시작 SQL을 다시 실행해도 경계와 감정은 바뀌지 않는다.
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        assertThat(result.getStdout().strip()).isEqualTo("t");
        assertThat(executeReclassificationStart().getExitCode()).isZero();
        assertThat(regions.isAggregationReady()).isFalse();
        assertThat(regions.areBoundariesVerified()).isTrue();
        assertThat(jdbc.sql("SELECT boundaries_verified_at::text FROM region_datasets").query(String.class).single())
                .isEqualTo(boundaryTime);
        assertThat(jdbc.sql("SELECT to_jsonb(e)::text FROM emotions e WHERE id = :id")
                .param("id", row.getId()).query(String.class).single()).isEqualTo(before);
        var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 38));
        assertThat(classifier.classify(point).regionCode()).isEqualTo("11010530");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 경계가_미검증이나_누락이면_재분류_시작을_거부한다(boolean missing) throws Exception {
        // given
        jdbc.sql(missing ? "DELETE FROM region_datasets" : "UPDATE region_datasets SET boundaries_verified_at = NULL").update();

        // when / then
        var result = executeReclassificationStart();
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("검증된 SGIS_2025_2Q 경계가 필요합니다.");
        assertThat(regions.isAggregationReady()).isFalse();
        assertThat(regions.areBoundariesVerified()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 시작_트랜잭션이나_자료_행이_잠기면_동시_시작은_실패하고_롤백_후_재실행할_수_있다(boolean datasetLock) throws Exception {
        // given: 완료 표시를 가진 상태에서 시작 SQL 또는 외부 자료 행 잠금을 유지한다.
        assertThat(executeVerification().getExitCode()).isZero();
        try (var first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.execute(datasetLock ? "SELECT 1 FROM region_datasets FOR UPDATE" : Files.readString(START_SCRIPT));

                // when / then: 첫 전환의 미커밋 변경과 실패한 두 번째 전환은 외부에 보이지 않는다.
                var result = executeReclassificationStart();
                assertThat(result.getExitCode()).isNotZero();
                assertThat(result.getStderr()).contains(datasetLock
                        ? "could not obtain lock on row in relation \"region_datasets\""
                        : "다른 감정 행정동 백필 배치가 실행 중입니다.");
                assertThat(regions.isAggregationReady()).isTrue();
            } finally {
                first.rollback();
            }
        }
        assertThat(regions.isAggregationReady()).isTrue();
        assertThat(executeReclassificationStart().getExitCode()).isZero();
        assertThat(regions.isAggregationReady()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "MEMO", "AUDIO"})
    void 재분류는_먼_선두_행을_건너뛰고_배치_상한과_기존_배정과_다른_필드를_보존한다(String contentType) throws Exception {
        // given: 범위 밖 선두 행은 LIMIT 전에 제외한다. 삭제된 기록도 분류 대상이다.
        Emotion far = reclassificationRow("NONE", 0), near = reclassificationRow(contentType, 128.005);
        Emotion matched = save("NONE");
        assertThat(backfill(1, matched.getId())).isOne();
        Emotion later = reclassificationRow("MEMO", 127);
        jdbc.sql("UPDATE emotions SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", near.getId()).update();
        jdbc.sql("UPDATE emotions SET region_classified_at = NULL WHERE id = :id").param("id", later.getId()).update();
        String farBefore = allFields(far), matchedBefore = allFields(matched), nearBefore = unrelatedFields(near);
        var oldTime = stored(near).getRegionClassifiedAt();

        // when / then
        assertThat(reclassify(1, matched.getId())).isOne();
        var point = new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(near.getLongitude(), near.getLatitude()));
        assertThat(stored(near).getRegionCode()).isEqualTo(classifier.classify(point).regionCode());
        assertThat(stored(near).getRegionClassifiedAt()).isAfter(oldTime);
        assertThat(unrelatedFields(near)).isEqualTo(nearBefore);
        assertThat(reclassify(1, matched.getId())).isZero();
        assertThat(stored(later).getRegionClassifiedAt()).isNull();
        assertThat(reclassify(1, later.getId())).isOne();
        assertThat(stored(later).getRegionCode()).isEqualTo("11010530");
        assertThat(allFields(far)).isEqualTo(farBefore);
        assertThat(allFields(matched)).isEqualTo(matchedBefore);
        assertThat(regions.isAggregationReady()).isFalse();
        assertThat(executeVerification().getExitCode()).isZero();
        String completedAt = jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single();
        assertThat(reclassify(1, later.getId())).isZero();
        assertThat(jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single()).isEqualTo(completedAt);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 재분류는_수정_중인_행을_건너뛰고_커밋된_내용이나_지역_배정을_덮어쓰지_않는다(boolean assigned) throws Exception {
        // given
        Emotion first = reclassificationRow("MEMO", 128.005), second = reclassificationRow("NONE", 128.005);
        try (var writer = dataSource.getConnection(); var statement = writer.createStatement()) {
            writer.setAutoCommit(false);
            statement.executeUpdate("UPDATE emotions SET memo = '동시 수정', region_code = "
                    + (assigned ? "'11010530'" : "NULL") + " WHERE id = " + first.getId());
            // when / then: 잠긴 행만 있는 호출의 0은 완료를 뜻하지 않는다.
            assertThat(reclassify(1, first.getId())).isZero();
            assertThat(reclassify(1, second.getId())).isOne();
            assertThat(stored(first).getRegionCode()).isNull();
            writer.commit();
        }
        String before = allFields(first);
        assertThat(reclassify(1, second.getId())).isEqualTo(assigned ? 0 : 1);
        assertThat(stored(first).getRegionCode()).isEqualTo("11010530");
        assertThat(stored(first).getMemo()).isEqualTo("동시 수정");
        if (assigned) {
            assertThat(allFields(first)).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 재분류_트랜잭션은_다른_배치와_시작과_완료를_막고_종료_후_재시도할_수_있다(boolean commit) throws Exception {
        // given
        Emotion row = reclassificationRow("MEMO", 128.005);
        String before = allFields(row);
        try (var first = dataSource.getConnection(); var statement = first.createStatement()) {
            first.setAutoCommit(false);
            statement.execute(Files.readString(RECLASSIFY_SCRIPT).replace(":'batch_size'", "'1'")
                    .replace(":'upper_id'", "'" + row.getId() + "'"));
            // when / then
            for (var result : new ExecResult[]{executeReclassificationBatch("1", row.getId().toString()),
                    executeBatch("1", row.getId().toString()), executeReclassificationStart(), executeVerification()}) {
                assertThat(result.getExitCode()).isNotZero();
                assertThat(result.getStderr()).contains("다른 감정 행정동 백필 배치가 실행 중입니다.");
            }
            assertThat(allFields(row)).isEqualTo(before);
            if (commit) {
                first.commit();
            } else {
                first.rollback();
            }
        }
        assertThat(reclassify(1, row.getId())).isEqualTo(commit ? 0 : 1);
        assertThat(stored(row).getRegionCode()).isEqualTo("11010530");
    }

    @Test
    void 재분류_배치_실패는_첫_행의_배정도_롤백하고_기존_분류_시각을_보존한다() throws Exception {
        // given
        Emotion first = reclassificationRow("MEMO", 128.005), second = reclassificationRow("AUDIO", 128.005);
        String before = allFields(first), secondBefore = allFields(second);
        jdbc.sql("ALTER TABLE emotions ADD CONSTRAINT ck_reclassification_test_failure CHECK (id <> "
                + second.getId() + " OR region_code IS NULL)").update();
        try {
            // when / then
            var result = executeReclassificationBatch("10", second.getId().toString());
            assertThat(result.getExitCode()).isNotZero();
            assertThat(result.getStderr()).contains("ck_reclassification_test_failure");
            assertThat(allFields(first)).isEqualTo(before);
            assertThat(allFields(second)).isEqualTo(secondBefore);
        } finally {
            jdbc.sql("ALTER TABLE emotions DROP CONSTRAINT ck_reclassification_test_failure").update();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"0|0", "-1|0", "1|-1", "abc|0", "2147483648|0", "1; DELETE FROM public.emotions|0"})
    void 재분류_입력_오류는_비정상_종료하고_감정을_보존한다(String batchSize, String upperId) throws Exception {
        // given
        Emotion row = reclassificationRow("MEMO", 128.005);
        String before = allFields(row);
        // when / then
        assertThat(executeReclassificationBatch(batchSize, upperId).getExitCode()).isNotZero();
        assertThat(allFields(row)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 미검증이나_누락_경계는_재분류를_거부하고_감정을_보존한다(boolean missing) throws Exception {
        // given
        Emotion row = reclassificationRow("MEMO", 128.005);
        String before = allFields(row);
        jdbc.sql(missing ? "DELETE FROM region_datasets" : "UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        // when / then
        var result = executeReclassificationBatch("1", row.getId().toString());
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("검증된 SGIS_2025_2Q 경계가 필요합니다.");
        assertThat(allFields(row)).isEqualTo(before);
    }

    @Test
    void 집계가_열린_상태의_실제_대상은_재분류를_거부하고_감정과_완료_시각을_보존한다() throws Exception {
        // given: 구 규칙의 완료 기록이 남아 있어도 시작 SQL 없이 부분 집계가 노출되면 안 된다.
        Emotion row = reclassificationRow("MEMO", 128.005);
        jdbc.sql("UPDATE region_datasets SET backfill_verified_at = CURRENT_TIMESTAMP").update();
        String completedAt = jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single();
        String before = allFields(row);

        // when / then
        var result = executeReclassificationBatch("1", row.getId().toString());
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("재분류 시작 SQL로 집계 완료 표시를 먼저 해제해야 합니다.");
        assertThat(allFields(row)).isEqualTo(before);
        assertThat(jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single()).isEqualTo(completedAt);
        assertThat(executeReclassificationStart().getExitCode()).isZero();
        assertThat(reclassify(1, row.getId())).isOne();
    }

    @ParameterizedTest
    @CsvSource({"127,NONE,false", "128.005,MEMO,true", "128.005,AUDIO,false"})
    void 상한_밖의_배정_가능한_NULL이_남으면_집계를_닫아_두고_재분류_뒤_완료한다(
            double longitude, String contentType, boolean deleted
    ) throws Exception {
        // given: 구 규칙의 완료 표시와 분류 시각이 있는 기록을 재현한다.
        Emotion matched = save("NONE");
        assertThat(backfill(1, matched.getId())).isOne();
        Emotion far = reclassificationRow("MEMO", 0), later = reclassificationRow(contentType, longitude);
        if (deleted) {
            jdbc.sql("UPDATE emotions SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id").param("id", later.getId()).update();
        }
        jdbc.sql("UPDATE region_datasets SET backfill_verified_at = CURRENT_TIMESTAMP").update();
        String farBefore = allFields(far), matchedBefore = allFields(matched), laterBefore = allFields(later);
        assertThat(executeReclassificationStart().getExitCode()).isZero();
        assertThat(reclassify(10, matched.getId())).isZero();

        // when / then: 배치 상한 밖·삭제 기록도 전체 완료 검사에서는 제외하지 않는다.
        var result = executeVerification();
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("지역 배정 가능한 미매칭 감정이 남아 있습니다.");
        assertThat(regions.isAggregationReady()).isFalse();
        assertThat(allFields(later)).isEqualTo(laterBefore);
        assertThat(reclassify(10, later.getId())).isOne();
        assertThat(executeVerification().getExitCode()).isZero();
        assertThat(regions.isAggregationReady()).isTrue();
        String completedAt = jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single();
        assertThat(executeVerification().getExitCode()).isZero();
        assertThat(jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single()).isEqualTo(completedAt);
        assertThat(allFields(far)).isEqualTo(farBefore);
        assertThat(allFields(matched)).isEqualTo(matchedBefore);
        assertThat(stored(later).getRegionCode()).isEqualTo("11010530");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 진행_중인_배정_가능한_NULL_등록은_완료를_막고_커밋_후에도_재분류가_필요하다(boolean commit) throws Exception {
        // given / when: 구버전이 남긴 분류 시각 있는 미매칭도 진행 중 쓰기와 충돌한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            reclassificationRow("MEMO", 128.005);
            var result = verify();
            assertThat(result.getExitCode()).isNotZero();
            assertThat(result.getStderr()).contains("could not obtain lock on relation \"public.emotions\"");
            assertThat(regions.isAggregationReady()).isFalse();
            if (!commit) {
                status.setRollbackOnly();
            }
        });
        // then
        if (commit) {
            assertThat(verify().getStderr()).contains("지역 배정 가능한 미매칭 감정이 남아 있습니다.");
            assertThat(regions.isAggregationReady()).isFalse();
            assertThat(reclassify(10, Long.MAX_VALUE)).isOne();
        }
        assertThat(verify().getExitCode()).isZero();
        assertThat(regions.isAggregationReady()).isTrue();
    }

    @Test
    void 완료_뒤_배정_가능한_NULL의_재유입은_재검증을_실패시키고_기존_완료_시각을_보존한다() throws Exception {
        // given: 완료 검증은 이후 구버전·직접 SQL 쓰기를 자동 차단하지 않는다.
        assertThat(executeVerification().getExitCode()).isZero();
        String completedAt = jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single();
        Emotion row = reclassificationRow("MEMO", 128.005);
        String before = allFields(row);

        // when / then
        var result = executeVerification();
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains("지역 배정 가능한 미매칭 감정이 남아 있습니다.");
        assertThat(jdbc.sql("SELECT backfill_verified_at::text FROM region_datasets").query(String.class).single()).isEqualTo(completedAt);
        assertThat(allFields(row)).isEqualTo(before);
    }

    @Test
    void 완료_검증의_분류_SQL_오류는_집계를_열지_않고_복구_뒤에도_미배정을_검사한다() throws Exception {
        // given
        Emotion row = reclassificationRow("MEMO", 128.005);
        String before = allFields(row);
        jdbc.sql("ALTER TABLE regions RENAME TO regions_unavailable").update();
        try {
            // when / then
            var result = executeVerification();
            assertThat(result.getExitCode()).isNotZero();
            assertThat(result.getStderr()).contains("relation \"public.regions\" does not exist");
            assertThat(regions.isAggregationReady()).isFalse();
            assertThat(allFields(row)).isEqualTo(before);
        } finally {
            jdbc.sql("ALTER TABLE regions_unavailable RENAME TO regions").update();
        }
        assertThat(executeVerification().getStderr()).contains("지역 배정 가능한 미매칭 감정이 남아 있습니다.");
        assertThat(reclassify(1, row.getId())).isOne();
        assertThat(executeVerification().getExitCode()).isZero();
        assertThat(regions.isAggregationReady()).isTrue();
    }

    private Emotion reclassificationRow(String type, double longitude) {
        Emotion row = save(type);
        jdbc.sql("UPDATE emotions SET location = ST_SetSRID(ST_MakePoint(:longitude, 38), 4326), "
                + "region_code = NULL, region_classified_at = '2026-10-04T00:00:00Z' WHERE id = :id")
                .param("longitude", longitude).param("id", row.getId()).update();
        return stored(row);
    }

    private String allFields(Emotion row) {
        return jdbc.sql("SELECT to_jsonb(e)::text FROM emotions e WHERE id = :id")
                .param("id", row.getId()).query(String.class).single();
    }

    private ExecResult executeReclassificationBatch(String batchSize, String upperId) throws Exception {
        return postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-qAt", "-v", "batch_size=" + batchSize,
                "-v", "upper_id=" + upperId, "-f", "/tmp/emotion-region-reclassification-batch.sql");
    }

    private int reclassify(int batchSize, long upperId) throws Exception {
        var result = executeReclassificationBatch(Integer.toString(batchSize), Long.toString(upperId));
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        return Integer.parseInt(result.getStdout().strip());
    }

    private ExecResult executeReclassificationStart() throws Exception {
        return postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-qAt", "-f", "/tmp/emotion-region-reclassification-start.sql");
    }

    private boolean verificationIsMissing() {
        return jdbc.sql("SELECT NOT EXISTS (SELECT 1 FROM region_datasets WHERE backfill_verified_at IS NOT NULL)")
                .query(Boolean.class).single();
    }

    private ExecResult executeVerification() throws Exception {
        return postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-qAt", "-f", "/tmp/emotion-region-backfill-verify.sql");
    }

    private ExecResult verify() {
        try {
            return executeVerification();
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    private Emotion save(String type) {
        return emotions.saveAndFlush(기본_한숨_빌더().state(EmotionState.FRUSTRATED)
                .memo(type.equals("MEMO") ? "기존 메모" : null)
                .audio(type.equals("AUDIO") ? Audio.builder().objectKey("test/audio.opus").build() : null).build());
    }

    private Emotion stored(Emotion row) {
        return emotions.findById(row.getId()).orElseThrow();
    }

    private String unrelatedFields(Emotion row) {
        return jdbc.sql("SELECT (to_jsonb(e) - 'region_code' - 'region_classified_at')::text FROM emotions e WHERE id = :id")
                .param("id", row.getId()).query(String.class).single();
    }

    private ExecResult executeBatch(String batchSize, String upperId) throws Exception {
        return postgis().execInContainer("psql", "-U", postgis().getUsername(), "-d", postgis().getDatabaseName(),
                "-X", "--single-transaction", "-v", "ON_ERROR_STOP=1", "-qAt", "-v", "batch_size=" + batchSize,
                "-v", "upper_id=" + upperId, "-f", "/tmp/emotion-region-backfill.sql");
    }

    private int backfill(int batchSize, long upperId) {
        try {
            var result = executeBatch(Integer.toString(batchSize), Long.toString(upperId));
            assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
            return Integer.parseInt(result.getStdout().strip());
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    private int backfillSeparately(long upperId) {
        var executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> backfill(10, upperId)).get(5, TimeUnit.SECONDS);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }
    }
}
