package com.pheeeew.appversion.application;

import static com.pheeeew.appversion.exception.AppVersionErrorCode.POLICY_NOT_FOUND;
import static com.pheeeew.appversion.fixture.AppVersionFixture.기본_앱_버전_정책_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.pheeeew.appversion.application.dto.AppVersionResult;
import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.domain.AppVersion;
import com.pheeeew.appversion.domain.repository.AppVersionRepository;
import com.pheeeew.appversion.exception.AppVersionException;
import com.pheeeew.support.PostgisDataJpaTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import(AppVersionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppVersionServiceIntegrationTest {

    @Autowired
    private AppVersionService appVersionService;

    @Autowired
    private AppVersionRepository appVersionRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        appVersionRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(AppPlatform.class)
    void 요청한_플랫폼의_활성_버전만_조회한다(AppPlatform platform) {
        // given
        appVersionRepository.save(기본_앱_버전_정책_빌더().build());
        appVersionRepository.save(기본_앱_버전_정책_빌더()
                .platform(AppPlatform.IOS).latestVersion("2.0.0").storeUrl("https://example.com/ios").build());

        // when
        AppVersionResult result = appVersionService.findByPlatformAndActiveTrue(platform);

        // then
        assertThat(result.minSupportedVersion()).isEqualTo("1.0.0");
        assertThat(result.latestVersion()).isEqualTo(platform == AppPlatform.ANDROID ? "1.1.0" : "2.0.0");
        assertThat(result.storeUrl()).isEqualTo(
                platform == AppPlatform.ANDROID ? "https://example.com/app" : "https://example.com/ios"
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 정책이_없거나_비활성이면_찾을_수_없음_오류를_반환한다(boolean inactivePolicyExists) {
        // given
        if (inactivePolicyExists) {
            appVersionRepository.save(기본_앱_버전_정책_빌더().active(false).build());
        }

        // when / then
        assertThatExceptionOfType(AppVersionException.class)
                .isThrownBy(() -> appVersionService.findByPlatformAndActiveTrue(AppPlatform.ANDROID))
                .satisfies(exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(POLICY_NOT_FOUND);
                    assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void SQL로_등록하면_기본_비활성과_감사_시각이_저장된다() {
        // given / when
        insertVersion("ANDROID", "1.0.0", "1.1.0");

        // then
        AppVersion appVersion = appVersionRepository.findAll().getFirst();
        assertThat(appVersion.getPlatform()).isEqualTo(AppPlatform.ANDROID);
        assertThat(appVersion.isActive()).isFalse();
        assertThat(appVersion.getCreatedAt()).isNotNull();
        assertThat(appVersion.getUpdatedAt()).isEqualTo(appVersion.getCreatedAt());
    }

    @Test
    void 비활성_정책이_있어도_같은_플랫폼을_중복_등록할_수_없다() {
        // given
        insertVersion("ANDROID", "1.0.0", "1.1.0");

        // when / then
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> appVersionRepository.save(기본_앱_버전_정책_빌더().build()))
                .withStackTraceContaining("uk_app_version_platform");
    }

    @ParameterizedTest
    @CsvSource({"1.9.0, 1.10.0", "1.0.9, 1.0.10", "1.99.99, 2.0.0", "1.0.0, 1.0.0", "2147483648.0.0, 2147483649.0.0"})
    void SQL로_등록한_버전을_숫자_순서로_검증하고_활성화하면_조회된다(String minimum, String latest) {
        // given
        insertVersion("ANDROID", minimum, latest);
        jdbcClient.sql("UPDATE app_version SET is_active = true WHERE platform = 'ANDROID'").update();

        // when
        AppVersionResult result = appVersionService.findByPlatformAndActiveTrue(AppPlatform.ANDROID);

        // then
        assertThat(result.minSupportedVersion()).isEqualTo(minimum);
        assertThat(result.latestVersion()).isEqualTo(latest);
    }

    @ParameterizedTest
    @CsvSource({
            "1.10.0, 1.9.0", "2.0.0, 1.99.99", "1.0.10, 1.0.9",
            "01.0.0, 1.0.0", "1.0, 1.0.0", "1.0.0, 1.1.0-beta", "1.0.0, invalid", "-1.0.0, 1.0.0"
    })
    void SQL로_잘못된_버전_형식이나_역전된_순서를_등록할_수_없다(String minimum, String latest) {
        // given / when / then
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertVersion("ANDROID", minimum, latest))
                .withStackTraceContaining("ck_app_version_versions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEB", "android", ""})
    void SQL로_정의되지_않은_플랫폼을_등록할_수_없다(String platform) {
        // given / when / then
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertVersion(platform, "1.0.0", "1.1.0"))
                .withStackTraceContaining("ck_app_version_platform");
    }

    private void insertVersion(String platform, String minimum, String latest) {
        jdbcClient.sql("""
                INSERT INTO app_version (platform, min_supported_version, latest_version, store_url)
                VALUES (:platform, :minimum, :latest, :storeUrl)
                """)
                .param("platform", platform)
                .param("minimum", minimum)
                .param("latest", latest)
                .param("storeUrl", 기본_앱_버전_정책_빌더().build().getStoreUrl())
                .update();
    }
}
