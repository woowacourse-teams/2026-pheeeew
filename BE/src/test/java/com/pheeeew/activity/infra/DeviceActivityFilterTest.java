package com.pheeeew.activity.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import jakarta.servlet.ServletException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("prod")
@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, DeviceActivityConfig.class,
        DeviceActivityFilterTest.ActivityController.class})
@WebMvcTest(controllers = DeviceActivityFilterTest.ActivityController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class))
class DeviceActivityFilterTest {

    private static final UUID DEVICE_ID = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-16T14:59:59Z");

    @Autowired
    private RestTestClient client;
    @Autowired
    private DeviceActivityFilter filter;
    @MockitoBean
    private DeviceActivityRecorder recorder;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_ID));
        when(clock.instant()).thenReturn(OCCURRED_AT, OCCURRED_AT.plusSeconds(2));
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/v1/emotions,200", "POST,/api/v1/emotions,200", "GET,/api/v1/emotions/1,200",
            "PUT,/api/v1/emotions/1/emojis/HEART,204", "DELETE,/api/v1/emotions/1/emojis/HEART,204",
            "POST,/api/v2/reports,201", "GET,/api/v2/blocks/sighs,200", "POST,/api/v2/blocks/devices,201",
            "DELETE,/api/v2/blocks/sighs/1,204", "DELETE,/api/v2/blocks/devices/1,204"})
    void 빈_조회와_쓰기_성공은_인증된_기기와_요청_시각으로_기록한다(String method, String path, int status) {
        // given / when
        client.method(HttpMethod.valueOf(method))
                .uri(path + "?status={status}&devicePublicId={spoofed}", status, UUID.randomUUID())
                .header("Authorization", "Bearer access-token").exchange()
                .expectStatus().isEqualTo(status);

        // then
        verify(recorder).record(DEVICE_ID, OCCURRED_AT);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/v2/app/version", "POST,/api/v2/devices", "POST,/api/v2/devices/tokens",
            "POST,/api/v2/devices/challenge", "GET,/actuator/health"})
    void 서비스_활동이_아닌_성공_요청은_제외한다(String method, String path) {
        // given / when
        client.method(HttpMethod.valueOf(method)).uri(path)
                .header("Authorization", "Bearer access-token").exchange().expectStatus().isOk();

        // then
        verifyNoInteractions(recorder);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 404, 500})
    void 성공하지_않은_서비스_응답은_제외한다(int status) {
        // given / when
        client.get().uri("/api/v1/emotions?status={status}", status)
                .header("Authorization", "Bearer access-token").exchange().expectStatus().isEqualTo(status);

        // then
        verifyNoInteractions(recorder);
    }

    @Test
    void 인증되지_않은_지도_조회와_인증_실패는_제외한다() {
        // given
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid token"));

        // when
        client.get().uri("/api/v1/emotions").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/emotions").header("Authorization", "Bearer invalid")
                .exchange().expectStatus().isUnauthorized();

        // then
        verifyNoInteractions(recorder);
    }

    @Test
    void 처리되지_않은_예외는_기본_200_상태여도_활동으로_기록하지_않는다() {
        // given
        SecurityContextHolder.getContext().setAuthentication(AccessTokenFixture.인증된_기기(DEVICE_ID));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/emotions");
        ServletException failure = new ServletException("request failed");

        // when / then
        try {
            assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                throw failure;
            })).isSameAs(failure);
            verifyNoInteractions(recorder);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @RestController
    static class ActivityController {

        @RequestMapping("/**")
        ResponseEntity<List<Object>> respond(
                @RequestParam(defaultValue = "200") int status
        ) {
            return ResponseEntity.status(status).body(List.of());
        }
    }
}
