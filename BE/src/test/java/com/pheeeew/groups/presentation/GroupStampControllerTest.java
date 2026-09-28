package com.pheeeew.groups.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.groups.application.GroupService;
import com.pheeeew.groups.application.dto.GroupStampItemResult;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.StampFrame;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(controllers = GroupController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class))
class GroupStampControllerTest {

    private static final String URI = "/api/v2/groups/stamps";
    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 그룹_공개_식별자 = UUID.fromString("5f2b1c84-9d0e-4a13-b6c7-2e8f0a4d7b19");

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(기기_공개_식별자));
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
    }

    @Test
    void 토큰의_기기로_조회하고_세_필드만_있는_배열을_반환한다() {
        // given
        GroupStampResult stamp = GroupStampResult.of("기본", "#FFFFFF", "#4A90D9", StampFrame.CIRCLE);
        when(groupService.findMyStamps(기기_공개_식별자))
                .thenReturn(List.of(GroupStampItemResult.of(그룹_공개_식별자, "한숨모임", stamp)));

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(URI + "?devicePublicId=" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange();

        // then
        result.expectStatus().isOk().expectBody().json("""
                [{
                  "groupId": "%s",
                  "name": "한숨모임",
                  "stamp": {
                    "text": "기본",
                    "textColor": "#FFFFFF",
                    "backgroundColor": "#4A90D9",
                    "frame": "CIRCLE"
                  }
                }]
                """.formatted(그룹_공개_식별자), JsonCompareMode.STRICT);
        verify(groupService).findMyStamps(기기_공개_식별자);
    }

    @Test
    void 내_그룹_스탬프가_없으면_빈_배열을_반환한다() {
        // given
        when(groupService.findMyStamps(기기_공개_식별자)).thenReturn(List.of());

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .exchange();

        // then
        result.expectStatus().isOk().expectBody().json("[]", JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    void 인증되지_않은_요청은_스탬프_목록을_조회할_수_없다(String 토큰) {
        // given
        RestTestClient.RequestHeadersSpec<?> request = client.get().uri(URI);
        if (토큰 != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰);
        }

        // when
        RestTestClient.ResponseSpec result = request.exchange();

        // then
        result.expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("AUTH-001");
        verifyNoInteractions(groupService);
    }
}
