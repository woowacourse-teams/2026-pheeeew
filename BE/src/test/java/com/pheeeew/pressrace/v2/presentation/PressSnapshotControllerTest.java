package com.pheeeew.pressrace.v2.presentation;

import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.기기;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.순위;
import static com.pheeeew.pressrace.v2.fixture.PressSnapshotFixture.참가자;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.pressrace.v2.application.PressSnapshotService;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(
        controllers = PressSnapshotController.class
)
class PressSnapshotControllerTest {

    private static final UUID DEVICE_PUBLIC_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final String SNAPSHOT_URI = "/api/v1/press-race/snapshot";
    private static final PressSnapshot 두명_순위 = 순위(List.of(참가자(1, 9), 참가자(2, 4)));

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private PressSnapshotService pressSnapshotService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_PUBLIC_ID));
    }

    @Test
    void 화면을_생략하면_PRESS로_조회하고_순위와_활동_인원을_반환한다() {
        // given
        when(pressSnapshotService.findSnapshot(DEVICE_PUBLIC_ID, PressView.PRESS))
                .thenReturn(두명_순위.findScreen(기기(2), PressView.PRESS));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(SNAPSHOT_URI);

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.date").isEqualTo("2026-11-04")
                .jsonPath("$.generatedAt").isEqualTo("2026-11-04T02:00:00Z")
                .jsonPath("$.activeCount").isEqualTo(2)
                .jsonPath("$.view").isEqualTo("PRESS")
                .jsonPath("$.me.publicId").isEqualTo("00000000-0000-0000-0000-000000000002")
                .jsonPath("$.me.nickname").isEqualTo("별2")
                .jsonPath("$.me.score").isEqualTo(4)
                .jsonPath("$.me.rank").isEqualTo(2)
                .jsonPath("$.me.participating").isEqualTo(true)
                .jsonPath("$.me.representativeState").isEqualTo("ANGRY")
                .jsonPath("$.nearby.length()").isEqualTo(2)
                .jsonPath("$.nearby[0].representativeState").isEqualTo("ANGRY")
                .jsonPath("$.nearby[0].rank").isEqualTo(1)
                .jsonPath("$.top100").isEmpty()
                .jsonPath("$.snapshotVersion").doesNotExist()
                .jsonPath("$.baseline").doesNotExist()
                .jsonPath("$.overtakes").doesNotExist();
        verify(pressSnapshotService).findSnapshot(DEVICE_PUBLIC_ID, PressView.PRESS);
    }

    @Test
    void 전체_화면은_상위_목록을_담고_미참여한_나는_순위와_공개_ID가_null이다() {
        // given
        when(pressSnapshotService.findSnapshot(DEVICE_PUBLIC_ID, PressView.ALL))
                .thenReturn(두명_순위.findScreen(기기(999), PressView.ALL));

        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(SNAPSHOT_URI + "?view=ALL");

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.view").isEqualTo("ALL")
                .jsonPath("$.me.publicId").value(value -> assertThat(value).isNull())
                .jsonPath("$.me.nickname").value(value -> assertThat(value).isNull())
                .jsonPath("$.me.rank").value(value -> assertThat(value).isNull())
                .jsonPath("$.me.score").isEqualTo(0)
                .jsonPath("$.me.participating").isEqualTo(false)
                .jsonPath("$.me.representativeState").value(value -> assertThat(value).isNull())
                .jsonPath("$.top100.length()").isEqualTo(2)
                .jsonPath("$.top100[1].publicId").isEqualTo("00000000-0000-0000-0000-000000000002");
    }

    @Test
    void 알_수_없는_화면은_400이다() {
        // when
        RestTestClient.ResponseSpec 응답 = 조회한다(SNAPSHOT_URI + "?view=RANKING");

        // then
        응답.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(pressSnapshotService);
    }

    @Test
    void 인증_없이_조회하면_401이고_서비스를_호출하지_않는다() {
        // when
        RestTestClient.ResponseSpec 응답 = client.get().uri(SNAPSHOT_URI).exchange();

        // then
        응답.expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(pressSnapshotService);
    }

    private RestTestClient.ResponseSpec 조회한다(String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange();
    }
}
