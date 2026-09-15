package com.pheeeew.appversion.presentation;

import static com.pheeeew.appversion.exception.AppVersionErrorCode.POLICY_NOT_FOUND;
import static com.pheeeew.appversion.fixture.AppVersionFixture.기본_앱_버전_정책_빌더;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.application.AppVersionService;
import com.pheeeew.appversion.application.dto.AppVersionResult;
import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.exception.AppVersionException;
import com.pheeeew.common.exception.GlobalExceptionHandler;
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

@AutoConfigureRestTestClient
@Import(GlobalExceptionHandler.class)
@WebMvcTest(AppVersionController.class)
class AppVersionControllerTest {

    private static final String VERSION_URI = "/api/v2/app/version";

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private AppVersionService appVersionService;

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
}
