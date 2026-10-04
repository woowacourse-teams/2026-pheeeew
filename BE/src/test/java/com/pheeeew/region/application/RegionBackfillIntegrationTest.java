package com.pheeeew.region.application;

import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
        postgis().copyFileToContainer(MountableFile.forHostPath(SCRIPT), "/tmp/emotion-region-backfill.sql");
    }

    @AfterEach
    void tearDown() {
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("INSERT INTO region_datasets (dataset_key) VALUES ('SGIS_2025_2Q') ON CONFLICT DO NOTHING").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
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
