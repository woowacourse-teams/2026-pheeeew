package com.pheeeew.emotion;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static com.pheeeew.support.SharedTestContainers.postgis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
class EmotionAnonymousMigrationIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 익명_여부와_닉네임_기본값은_기존_감정을_보존하고_명시와_생략_등록을_허용한다() {
        // given: 실제 이전 스키마에 작성 기기 유무가 다른 감정을 준비한다.
        String database = "emotion_anonymous_" + UUID.randomUUID().toString().replace("-", "");
        var container = postgis();
        var dataSource = new DriverManagerDataSource(
                container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + database),
                container.getUsername(), container.getPassword());
        jdbc.sql("CREATE DATABASE " + database).update();
        try {
            Flyway.configure().dataSource(dataSource).target("20261007.2").load().migrate();
            JdbcClient isolated = JdbcClient.create(dataSource);
            검증용_지역_계층을_저장한다(isolated);
            isolated.sql("""
                    INSERT INTO devices (public_id, request_id, platform, nickname, created_at, updated_at)
                    VALUES (gen_random_uuid(), gen_random_uuid(), 'ANDROID', '스타크', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """).update();
            구버전_감정을_저장한다(isolated);
            var before = isolated.sql("SELECT to_jsonb(e)::text FROM emotions e ORDER BY id").query(String.class).list();
            assertThatThrownBy(() -> isolated.sql("""
                    INSERT INTO emotions (request_id, location, device_id, created_at, updated_at,
                                          region_code, region_classified_at)
                    VALUES (gen_random_uuid(), ST_SetSRID(ST_MakePoint(126.9774, 37.5669), 4326), NULL,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '11010530', CURRENT_TIMESTAMP)
                    """).update()).isInstanceOf(DataIntegrityViolationException.class);


            // when
            Flyway latest = Flyway.configure().dataSource(dataSource).target("20261007.3").load();
            latest.migrate();

            // then: 닉네임·위치·작성 기기·감사 시각 등 기존 값은 변경하지 않는다.
            assertThat(isolated.sql("SELECT (to_jsonb(e) - 'anonymous')::text FROM emotions e ORDER BY id")
                    .query(String.class).list()).isEqualTo(before);
            assertThat(isolated.sql("SELECT anonymous FROM emotions ORDER BY id").query(Boolean.class).list())
                    .containsExactly(true, true);
            구버전_감정을_저장한다(isolated);
            assertThat(isolated.sql("SELECT count(*) FROM emotions WHERE anonymous").query(Long.class).single()).isEqualTo(4);
            assertThatThrownBy(() -> isolated.sql("UPDATE emotions SET anonymous = NULL").update())
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> isolated.sql("UPDATE emotions SET anonymous = FALSE WHERE device_id IS NULL").update())
                    .isInstanceOf(DataIntegrityViolationException.class);
            isolated.sql("UPDATE emotions SET anonymous = FALSE WHERE device_id IS NOT NULL").update();
            assertThat(isolated.sql("SELECT count(*) FROM emotions WHERE nickname = '기존 랜덤 닉네임'")
                    .query(Long.class).single()).isEqualTo(4);
            닉네임을_생략하고_감정을_저장한다(isolated);
            assertThat(isolated.sql("SELECT anonymous FROM emotions WHERE nickname = '익명' ORDER BY id")
                    .query(Boolean.class).list()).containsExactly(true, false);
            assertThatThrownBy(() -> isolated.sql("UPDATE emotions SET nickname = NULL").update())
                    .isInstanceOf(DataIntegrityViolationException.class);
            var after = isolated.sql("SELECT to_jsonb(e)::text FROM emotions e ORDER BY id").query(String.class).list();

            latest.migrate();
            assertThat(isolated.sql("SELECT to_jsonb(e)::text FROM emotions e ORDER BY id")
                    .query(String.class).list()).isEqualTo(after);
            assertThat(isolated.sql("SELECT count(*) FROM emotions WHERE NOT anonymous").query(Long.class).single()).isEqualTo(3);
            assertThat(isolated.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '20261007.3' AND success")
                    .query(Long.class).single()).isOne();
        } finally {
            jdbc.sql("DROP DATABASE " + database).update();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 선택한_익명_여부와_작성_기기를_JPA로_저장하고_조회한다(boolean anonymous) {
        // given
        검증용_지역_계층을_저장한다(jdbc);
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("스타크").build());
        Emotion saved = emotionRepository.saveAndFlush(기본_한숨_빌더().deviceId(device.getId()).anonymous(anonymous).build());
        entityManager.clear();

        // when
        Emotion loaded = emotionRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(loaded.isAnonymous()).isEqualTo(anonymous);
        assertThat(loaded.getDeviceId()).isEqualTo(device.getId());
    }

    private void 구버전_감정을_저장한다(JdbcClient client) {
        client.sql("""
                INSERT INTO emotions (request_id, location, nickname, device_id, created_at, updated_at,
                                      region_code, region_classified_at)
                SELECT gen_random_uuid(), ST_SetSRID(ST_MakePoint(126.9774, 37.5669), 4326),
                       '기존 랜덤 닉네임', owner, CURRENT_TIMESTAMP - INTERVAL '1 day',
                       CURRENT_TIMESTAMP - INTERVAL '1 hour', '11010530', CURRENT_TIMESTAMP
                FROM (VALUES (NULL::BIGINT), ((SELECT id FROM devices LIMIT 1))) AS authors(owner)
                """).update();
    }

    private void 닉네임을_생략하고_감정을_저장한다(JdbcClient client) {
        client.sql("""
                INSERT INTO emotions (request_id, location, anonymous, device_id, created_at, updated_at,
                                      region_code, region_classified_at)
                SELECT gen_random_uuid(), ST_SetSRID(ST_MakePoint(126.9774, 37.5669), 4326),
                       anonymous, owner, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '11010530', CURRENT_TIMESTAMP
                FROM (VALUES (TRUE, NULL::BIGINT), (FALSE, (SELECT id FROM devices LIMIT 1))) AS authors(anonymous, owner)
                """).update();
    }
}
