package com.pheeeew.appconfig.application;

import static com.pheeeew.appconfig.exception.AppConfigErrorCode.CONFIG_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.pheeeew.appconfig.application.dto.AppConfigResult;
import com.pheeeew.appconfig.domain.AppConfigPlatform;
import com.pheeeew.appconfig.exception.AppConfigException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import javax.sql.DataSource;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import(AppConfigService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppConfigServiceIntegrationTest {

    @Autowired
    private AppConfigService service;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void 초기_설정을_복원한다() {
        jdbc.sql("""
                INSERT INTO app_config (platform, daily_press_ranking_enabled, notice_message, revision)
                VALUES ('ANDROID', true, '', 1), ('IOS', true, '', 1)
                ON CONFLICT (platform) DO UPDATE
                SET daily_press_ranking_enabled = true, notice_message = '', revision = 1,
                    updated_at = app_config.created_at
                """).update();
    }

    @Test
    void 새_스키마에_마이그레이션을_적용하면_두_플랫폼의_초기값이_등록된다() throws Exception {
        // given
        String schema = "app_config_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("SET search_path TO " + schema);

                // when
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/migration/V20261029_1__create_app_config.sql"));

                // then
                try (var rows = statement.executeQuery("SELECT * FROM app_config ORDER BY platform")) {
                    for (String platform : new String[]{"ANDROID", "IOS"}) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString("platform")).isEqualTo(platform);
                        assertThat(rows.getBoolean("daily_press_ranking_enabled")).isTrue();
                        assertThat(rows.getString("notice_message")).isEmpty();
                        assertThat(rows.getLong("revision")).isEqualTo(1);
                        assertThat(rows.getTimestamp("updated_at")).isNotNull();
                        assertThat(rows.getTimestamp("created_at")).isEqualTo(rows.getTimestamp("updated_at"));
                    }
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                statement.execute("RESET search_path");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void 등록된_두_플랫폼의_초기_설정과_감사_시각을_조회한다() {
        // given / when
        AppConfigResult android = service.findByPlatform(AppConfigPlatform.ANDROID);
        AppConfigResult ios = service.findByPlatform(AppConfigPlatform.IOS);

        // then
        assertThat(android.revision()).isEqualTo(1);
        assertThat(android.dailyPressRankingEnabled()).isTrue();
        assertThat(android.noticeMessage()).isEmpty();
        assertThat(android.updatedAt()).isNotNull();
        assertThat(ios.revision()).isEqualTo(1);
        assertThat(ios.dailyPressRankingEnabled()).isTrue();
        assertThat(ios.noticeMessage()).isEmpty();
        assertThat(ios.updatedAt()).isNotNull();
    }

    @Test
    void 설정이_없으면_기본값_대신_찾을_수_없음_오류를_반환한다() {
        // given
        jdbc.sql("DELETE FROM app_config WHERE platform = 'ANDROID'").update();

        // when / then
        assertThatExceptionOfType(AppConfigException.class)
                .isThrownBy(() -> service.findByPlatform(AppConfigPlatform.ANDROID))
                .satisfies(exception -> assertThat(exception.getErrorCode()).isEqualTo(CONFIG_NOT_FOUND));
    }

    @Test
    void 플랫폼별_ON_OFF_ON과_공지_변경_복구를_새_revision으로_조회한다() {
        // given
        AppConfigResult initial = service.findByPlatform(AppConfigPlatform.ANDROID);
        AppConfigResult ios = service.findByPlatform(AppConfigPlatform.IOS);
        String notice = "  점검 안내\n잠시 기다려 주세요.  ";

        // when
        assertThat(변경한다("ANDROID", 1, false, notice)).isEqualTo(1);
        AppConfigResult disabled = service.findByPlatform(AppConfigPlatform.ANDROID);
        assertThat(변경한다("ANDROID", 2, true, "")).isEqualTo(1);
        AppConfigResult restored = service.findByPlatform(AppConfigPlatform.ANDROID);

        // then
        assertThat(disabled.revision()).isEqualTo(2);
        assertThat(disabled.dailyPressRankingEnabled()).isFalse();
        assertThat(disabled.noticeMessage()).isEqualTo(notice);
        assertThat(disabled.updatedAt()).isAfter(initial.updatedAt());
        assertThat(restored.revision()).isEqualTo(3);
        assertThat(restored.dailyPressRankingEnabled()).isTrue();
        assertThat(restored.noticeMessage()).isEmpty();
        assertThat(restored.updatedAt()).isAfter(disabled.updatedAt());
        assertThat(service.findByPlatform(AppConfigPlatform.IOS)).isEqualTo(ios);
    }

    @Test
    void 동일값이나_오래된_revision의_변경은_설정과_감사_시각을_유지한다() {
        // given
        AppConfigResult initial = service.findByPlatform(AppConfigPlatform.ANDROID);

        // when / then
        assertThat(변경한다("ANDROID", 1, true, "")).isZero();
        assertThat(service.findByPlatform(AppConfigPlatform.ANDROID)).isEqualTo(initial);
        assertThat(변경한다("ANDROID", 1, false, "점검 중")).isEqualTo(1);
        AppConfigResult changed = service.findByPlatform(AppConfigPlatform.ANDROID);
        assertThat(변경한다("ANDROID", 1, true, "오래된 변경")).isZero();
        assertThat(service.findByPlatform(AppConfigPlatform.ANDROID)).isEqualTo(changed);
    }

    @Test
    void 같은_revision으로_동시_변경하면_한_변경만_성공한다() throws Exception {
        // given
        CyclicBarrier start = new CyclicBarrier(2);

        // when
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return 변경한다("ANDROID", 1, false, "첫 번째 변경");
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return 변경한다("ANDROID", 1, false, "두 번째 변경");
            });

            // then
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        AppConfigResult result = service.findByPlatform(AppConfigPlatform.ANDROID);
        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.dailyPressRankingEnabled()).isFalse();
        assertThat(result.noticeMessage()).isIn("첫 번째 변경", "두 번째 변경");
    }

    @Test
    void 보조_평면_이모지_200개는_저장하고_201개는_거부한다() {
        // given
        String notice = "😀".repeat(200);

        // when
        assertThat(변경한다("ANDROID", 1, true, notice)).isEqualTo(1);

        // then
        assertThat(service.findByPlatform(AppConfigPlatform.ANDROID).noticeMessage()).isEqualTo(notice);
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> 변경한다("ANDROID", 2, true, notice + "😀"));
        assertThat(service.findByPlatform(AppConfigPlatform.ANDROID).revision()).isEqualTo(2);
    }

    @Test
    void 길이_초과_문자열의_마지막이_공백이어도_잘라내지_않고_거부한다() {
        // given
        String[] notices = {"가".repeat(200) + " ", " ".repeat(201)};

        // when / then
        for (String notice : notices) {
            assertThatExceptionOfType(DataIntegrityViolationException.class)
                    .isThrownBy(() -> 변경한다("ANDROID", 1, true, notice));
        }
        assertThat(service.findByPlatform(AppConfigPlatform.ANDROID).noticeMessage()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "platform = 'WEB'", "platform = NULL", "daily_press_ranking_enabled = NULL",
            "notice_message = NULL", "revision = 0", "revision = -1", "revision = NULL",
            "updated_at = NULL", "created_at = NULL"
    })
    void SQL로_필수값이나_플랫폼_revision_제약을_위반할_수_없다(String assignment) {
        // given / when / then
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.sql("UPDATE app_config SET " + assignment + " WHERE platform = 'ANDROID'").update());
    }

    @Test
    void 같은_플랫폼의_설정을_중복_등록할_수_없다() {
        // given / when / then
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.sql("INSERT INTO app_config (platform) VALUES ('ANDROID')").update())
                .withStackTraceContaining("uk_app_config_platform");
    }

    private int 변경한다(String platform, long expected, boolean enabled, String notice) {
        return jdbc.sql("""
                UPDATE app_config
                SET daily_press_ranking_enabled = :enabled, notice_message = :notice,
                    revision = revision + 1, updated_at = statement_timestamp()
                WHERE platform = :platform AND revision = :expected
                  AND (daily_press_ranking_enabled, notice_message) IS DISTINCT FROM (:enabled, :notice)
                """)
                .param("platform", platform)
                .param("expected", expected)
                .param("enabled", enabled)
                .param("notice", notice)
                .update();
    }
}
