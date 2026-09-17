package com.pheeeew.appversion.presentation;

import static com.pheeeew.appversion.exception.AppVersionErrorCode.POLICY_NOT_FOUND;
import static com.pheeeew.appversion.fixture.AppVersionFixture.기본_앱_버전_정책_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.application.AppVersionService;
import com.pheeeew.appversion.application.dto.AppVersionResult;
import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.exception.AppVersionException;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.support.SharedMetricsTestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureRestTestClient
@Import({
        GlobalExceptionHandler.class,
        SharedMetricsTestConfiguration.class,
        AppVersionControllerTest.OtherController.class
})
@WebMvcTest({AppVersionController.class, AppVersionControllerTest.OtherController.class})
class AppVersionControllerTest {

    private static final String VERSION_URI = "/api/v2/app/version";
    private static final String SUCCESS_METRIC = "pheeeew.app.version.checks";

    @Autowired
    private RestTestClient client;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private AppVersionService appVersionService;

    private double androidBefore;
    private double iosBefore;

    @BeforeEach
    void setUp() {
        androidBefore = 성공_요청_수를_조회한다("android");
        iosBefore = 성공_요청_수를_조회한다("ios");
    }

    @ParameterizedTest
    @CsvSource({"android, ANDROID", "ANDROID, ANDROID", "AnDrOiD, ANDROID", "ios, IOS", "IOS, IOS", "iOs, IOS"})
    void 플랫폼을_변환하고_버전_정보만_반환한다(String input, AppPlatform platform) {
        // given
        when(appVersionService.findByPlatformAndActiveTrue(platform))
                .thenReturn(AppVersionResult.from(기본_앱_버전_정책_빌더().platform(platform).build()));

        // when
        RestTestClient.ResponseSpec result = 조회한다(input);

        // then
        result.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {"minSupportedVersion":"1.0.0","latestVersion":"1.1.0","storeUrl":"https://example.com/app"}
                        """, JsonCompareMode.STRICT);
        verify(appVersionService).findByPlatformAndActiveTrue(platform);
        성공_집계_증가량을_검증한다(platform == AppPlatform.ANDROID ? 1 : 0, platform == AppPlatform.IOS ? 1 : 0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"web", " android ", " "})
    void 플랫폼이_누락되거나_잘못되면_400을_반환한다(String platform) {
        // given / when
        RestTestClient.ResponseSpec result = 조회한다(platform);

        // then
        result.expectStatus().isBadRequest()
                .expectBody().json("""
                        {"code":"APP_VERSION-001","message":"플랫폼은 android 또는 ios여야 합니다."}
                        """, JsonCompareMode.STRICT);
        verifyNoInteractions(appVersionService);
        성공_집계_증가량을_검증한다(0, 0);
    }

    @Test
    void 활성_정책이_없으면_404를_반환한다() {
        // given
        when(appVersionService.findByPlatformAndActiveTrue(AppPlatform.ANDROID))
                .thenThrow(new AppVersionException(POLICY_NOT_FOUND));

        // when
        RestTestClient.ResponseSpec result = 조회한다("android");

        // then
        result.expectStatus().isNotFound()
                .expectBody().json("""
                        {"code":"APP_VERSION-002","message":"활성 앱 버전 정책을 찾을 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        성공_집계_증가량을_검증한다(0, 0);
    }

    @Test
    void 서버_오류는_성공_요청_수에_포함하지_않는다() {
        // given
        when(appVersionService.findByPlatformAndActiveTrue(AppPlatform.ANDROID))
                .thenThrow(new IllegalStateException("version lookup failed"));

        // when
        RestTestClient.ResponseSpec result = 조회한다("android");

        // then
        result.expectStatus().isEqualTo(500)
                .expectBody().json("""
                        {"code":"COMMON-002","message":"서버 내부 오류가 발생했습니다."}
                        """, JsonCompareMode.STRICT);
        성공_집계_증가량을_검증한다(0, 0);
    }

    @Test
    void 반복_성공_요청은_각각_집계한다() {
        // given
        when(appVersionService.findByPlatformAndActiveTrue(AppPlatform.ANDROID))
                .thenReturn(AppVersionResult.from(기본_앱_버전_정책_빌더().build()));

        // when
        조회한다("android").expectStatus().isOk();
        조회한다("ANDROID").expectStatus().isOk();

        // then
        성공_집계_증가량을_검증한다(2, 0);
    }

    @Test
    void HEAD_성공_응답은_집계하지_않는다() {
        // given
        when(appVersionService.findByPlatformAndActiveTrue(AppPlatform.ANDROID))
                .thenReturn(AppVersionResult.from(기본_앱_버전_정책_빌더().build()));

        // when
        RestTestClient.ResponseSpec result = client.head().uri(VERSION_URI + "?platform=android").exchange();

        // then
        result.expectStatus().isOk();
        성공_집계_증가량을_검증한다(0, 0);
    }

    @Test
    void 다른_경로의_성공_응답은_집계하지_않는다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get().uri("/metrics-test/other?platform=android").exchange();

        // then
        result.expectStatus().isOk();
        verifyNoInteractions(appVersionService);
        성공_집계_증가량을_검증한다(0, 0);
    }

    private RestTestClient.ResponseSpec 조회한다(String platform) {
        return client.get().uri(builder -> {
            builder.path(VERSION_URI);
            if (platform != null) {
                builder.queryParam("platform", platform);
            }
            return builder.build();
        }).exchange();
    }

    private double 성공_요청_수를_조회한다(String platform) {
        return meterRegistry.get(SUCCESS_METRIC).tag("platform", platform).counter().count();
    }

    private void 성공_집계_증가량을_검증한다(double android, double ios) {
        assertThat(성공_요청_수를_조회한다("android") - androidBefore).isEqualTo(android);
        assertThat(성공_요청_수를_조회한다("ios") - iosBefore).isEqualTo(ios);
        assertThat(meterRegistry.get(SUCCESS_METRIC).counters())
                .hasSize(2)
                .allSatisfy(counter -> assertThat(counter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .containsExactly("platform"));
        assertThat(meterRegistry.get(SUCCESS_METRIC).counters())
                .extracting(counter -> counter.getId().getTag("platform"))
                .containsExactlyInAnyOrder("android", "ios");
    }

    @RestController
    static class OtherController {

        @GetMapping("/metrics-test/other")
        String find() {
            return "ok";
        }
    }
}
