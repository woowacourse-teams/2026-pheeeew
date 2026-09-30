package com.pheeeew.emotion.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.appversion.infra.metrics.AppVersionMetricsFilter;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.config.ClockConfig;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.fixture.EmotionFixture;
import com.pheeeew.emotion.infra.metrics.EmotionMetrics;
import com.pheeeew.emotion.infra.ratelimit.EmotionCreateRateLimiter;
import com.pheeeew.emotion.presentation.config.EmotionWebMvcConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;

@TestPropertySource(properties = "pheeeew.rate-limit.emotion-create.enabled=true")
@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class, ClockConfig.class,
        EmotionCreateRateLimiter.class, EmotionWebMvcConfig.class})
@WebMvcTest(
        controllers = EmotionController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppVersionMetricsFilter.class)
)
class EmotionCreateRateLimitInterceptorTest {

    private static final String EMOTIONS_URI = "/api/v1/emotions";
    private static final AtomicInteger TOKEN_SEQUENCE = new AtomicInteger();

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private EmotionCommandService emotionCommandService;

    @MockitoBean
    private EmotionQueryService emotionQueryService;

    @MockitoBean
    private EmotionMetrics emotionMetrics;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        Emotion saved = EmotionFixture.기본_한숨_빌더().build();
        ReflectionTestUtils.setField(saved, "id", 42L);
        when(emotionCommandService.save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                any())).thenReturn(saved);
    }

    @Test
    void 같은_기기가_1초_안에_다시_등록하면_429와_Retry_After를_반환한다() {
        // given
        String token = newDeviceToken();
        create(token).expectStatus().isOk();

        // when
        RestTestClient.ResponseSpec result = create(token);

        // then
        result.expectStatus().isEqualTo(429)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "1")
                .expectBody().json("""
                        {"code":"EMOTION-012","message":"감정은 1초에 한 번만 남길 수 있습니다."}
                        """);
    }

    @Test
    void 차단된_요청은_저장을_시도하지_않고_지표에_기록한다() {
        // given
        String token = newDeviceToken();
        create(token).expectStatus().isOk();

        // when
        create(token).expectStatus().isEqualTo(429);

        // then
        verify(emotionMetrics).recordCreateThrottled();
        verify(emotionCommandService).save(any(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(), any(),
                any());
    }

    @Test
    void 기기가_다르면_서로_제한하지_않는다() {
        // given
        create(newDeviceToken()).expectStatus().isOk();

        // when
        RestTestClient.ResponseSpec result = create(newDeviceToken());

        // then
        result.expectStatus().isOk();
    }

    @Test
    void 조회는_제한하지_않는다() {
        // given
        String token = newDeviceToken();
        create(token).expectStatus().isOk();

        // when
        int status = client.get().uri(EMOTIONS_URI + "/42")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();

        // then
        assertThat(status).isNotEqualTo(429);
    }

    private String newDeviceToken() {
        String token = "access-token-" + TOKEN_SEQUENCE.incrementAndGet();
        when(jwtDecoder.decode(token)).thenReturn(AccessTokenFixture.액세스_토큰_클레임(UUID.randomUUID()));
        return token;
    }

    private RestTestClient.ResponseSpec create(String token) {
        return client.post().uri(EMOTIONS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"requestId":"%s","state":"FRUSTRATED","longitude":126.97,"latitude":37.56,
                         "rotationDegrees":35.5,"contentType":"NONE"}
                        """.formatted(UUID.randomUUID()))
                .exchange();
    }
}
