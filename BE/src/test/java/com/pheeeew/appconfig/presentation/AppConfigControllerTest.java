package com.pheeeew.appconfig.presentation;

import static com.pheeeew.appconfig.exception.AppConfigErrorCode.CONFIG_NOT_FOUND;
import static com.pheeeew.appconfig.fixture.AppConfigFixture.기본_앱_설정_빌더;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appconfig.application.AppConfigService;
import com.pheeeew.appconfig.application.dto.AppConfigResult;
import com.pheeeew.appconfig.domain.AppConfig;
import com.pheeeew.appconfig.domain.AppConfigPlatform;
import com.pheeeew.appconfig.exception.AppConfigException;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(AppConfigController.class)
class AppConfigControllerTest {

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private AppConfigService appConfigService;

    @ParameterizedTest
    @CsvSource({"AnDrOiD, ANDROID", "iOs, IOS"})
    void 플랫폼의_설정과_UTC_시각을_필수_필드로_반환하고_캐시를_금지한다(String input, AppConfigPlatform platform) {
        // given
        AppConfig config = 기본_앱_설정_빌더().platform(platform).dailyPressRankingEnabled(false).revision(3).build();
        ReflectionTestUtils.setField(config, "updatedAt", Instant.parse("2026-10-29T03:00:00Z"));
        when(appConfigService.findByPlatform(platform)).thenReturn(AppConfigResult.from(config));

        // when
        RestTestClient.ResponseSpec result = 조회한다(input);

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"revision":3,"dailyPressRankingEnabled":false,"noticeMessage":"","updatedAt":"2026-10-29T03:00:00Z"}
                        """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"web", " android "})
    void 잘못된_플랫폼은_400으로_반환하고_캐시를_금지한다(String platform) {
        // given / when
        RestTestClient.ResponseSpec result = 조회한다(platform);

        // then
        result.expectStatus().isBadRequest()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"code":"APP_CONFIG-001","message":"플랫폼은 android 또는 ios여야 합니다."}
                        """, JsonCompareMode.STRICT);
        verifyNoInteractions(appConfigService);
    }

    @Test
    void 설정이_없으면_404로_반환하고_캐시를_금지한다() {
        // given
        when(appConfigService.findByPlatform(AppConfigPlatform.ANDROID))
                .thenThrow(new AppConfigException(CONFIG_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 조회한다("android");

        // then
        result.expectStatus().isNotFound()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"code":"APP_CONFIG-002","message":"앱 설정을 찾을 수 없습니다."}
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 서버_오류는_기존_형식으로_반환하고_캐시를_금지한다() {
        // given
        when(appConfigService.findByPlatform(AppConfigPlatform.ANDROID))
                .thenThrow(new IllegalStateException("config lookup failed"));

        // when
        RestTestClient.ResponseSpec result = 조회한다("android");

        // then
        result.expectStatus().isEqualTo(500)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"code":"COMMON-002","message":"서버 내부 오류가 발생했습니다."}
                        """, JsonCompareMode.STRICT);
    }

    private RestTestClient.ResponseSpec 조회한다(String platform) {
        return client.get().uri(builder -> {
            builder.path("/api/v2/app/config");
            if (platform != null) {
                builder.queryParam("platform", platform);
            }
            return builder.build();
        }).exchange();
    }
}
