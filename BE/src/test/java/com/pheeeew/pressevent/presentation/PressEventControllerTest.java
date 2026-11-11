package com.pheeeew.pressevent.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.infra.security.AuthenticationErrorHandler;
import com.pheeeew.auth.infra.security.SecurityConfig;
import com.pheeeew.auth.presentation.config.AuthWebMvcConfig;
import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import com.pheeeew.common.exception.GlobalExceptionHandler;
import com.pheeeew.pressevent.application.PressEventAttemptResult;
import com.pheeeew.pressevent.application.PressEventRankingResult;
import com.pheeeew.pressevent.application.PressEventService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

@AutoConfigureRestTestClient
@ImportAutoConfiguration({ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, AuthenticationErrorHandler.class, AuthWebMvcConfig.class,
        CurrentDeviceArgumentResolver.class, GlobalExceptionHandler.class})
@WebMvcTest(controllers = PressEventController.class)
class PressEventControllerTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final Instant RANKED_AT = Instant.parse("2026-11-11T03:00:00Z");
    private static final String ATTEMPTS = "/api/v1/press-events/{eventId}/attempts";
    private static final String RANKINGS = "/api/v1/press-events/{eventId}/rankings";
    private static final String COUNTS = """
            {"FRUSTRATED":40,"IRRITATED":32,"EXHAUSTED":20,"DISCOURAGED":15,"ANGRY":25}
            """;

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private PressEventService pressEventService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode("access-token")).thenReturn(AccessTokenFixture.액세스_토큰_클레임(DEVICE_ID));
    }

    @Test
    void 인증된_기기는_400회를_넘는_도전_결과를_제출할_수_있다() {
        // given
        when(pressEventService.submit(eq(EVENT_ID), eq(DEVICE_ID), eq(REQUEST_ID), any()))
                .thenReturn(PressEventAttemptResult.of(REQUEST_ID, 532, 556, 3, RANKED_AT, false));

        // when
        RestTestClient.ResponseSpec 응답 = 제출한다(COUNTS.replace("25", "425"));

        // then
        응답.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.attemptId").isEqualTo(REQUEST_ID.toString())
                .jsonPath("$.total").isEqualTo(532)
                .jsonPath("$.bestScore").isEqualTo(556)
                .jsonPath("$.rank").isEqualTo(3)
                .jsonPath("$.rankedAt").isEqualTo(RANKED_AT.toString())
                .jsonPath("$.replayed").isEqualTo(false);
    }

    @ParameterizedTest
    @MethodSource("잘못된_횟수")
    void 유효하지_않은_감정별_횟수는_저장_전에_거절한다(String counts, String code) {
        // when & then
        제출한다(counts).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(code);
        verifyNoInteractions(pressEventService);
    }

    @Test
    void 토큰이_없으면_두_경로_모두_인증_오류를_반환한다() {
        // when & then
        client.post().uri(ATTEMPTS, EVENT_ID).exchange().expectStatus().isUnauthorized();
        client.get().uri(RANKINGS, EVENT_ID).exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(pressEventService);
    }

    @Test
    void 빈_이벤트_순위는_1위와_내_기록을_null로_표시한다() {
        // given
        when(pressEventService.findRanking(EVENT_ID, DEVICE_ID, null, 20))
                .thenReturn(PressEventRankingResult.of(null, List.of(), null, null, RANKED_AT));

        // when
        RestTestClient.ResponseSpec 응답 = client.get().uri(RANKINGS, EVENT_ID)
                .headers(headers -> headers.setBearerAuth("access-token")).exchange();

        // then
        응답.expectStatus().isOk().expectBody().json("""
                {
                  "leader": null,
                  "rankings": {"items": [], "hasNext": false, "nextCursor": null},
                  "me": null,
                  "rankedAt": "2026-11-11T03:00:00Z"
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void 페이지_크기가_범위를_벗어나면_거절한다(int limit) {
        // when & then
        client.get().uri(RANKINGS + "?limit={limit}", EVENT_ID, limit)
                .headers(headers -> headers.setBearerAuth("access-token")).exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("COMMON-001");
        verifyNoInteractions(pressEventService);
    }

    private static Stream<Arguments> 잘못된_횟수() {
        return Stream.of(
                Arguments.of(COUNTS.replace("25", "-1"), "PRESS-EVENT-004"),
                Arguments.of(COUNTS.replace("25", "25.0"), "COMMON-001"),
                Arguments.of(COUNTS.replace("25", "\"25\""), "COMMON-001"),
                Arguments.of(COUNTS.replace("25", "null"), "COMMON-001"),
                Arguments.of(COUNTS.replace("25", "9223372036854775808"), "COMMON-001"),
                Arguments.of(COUNTS.replace("40", "2147483647").replace("25", "2147483647"), "PRESS-EVENT-004"),
                Arguments.of(COUNTS.replace("ANGRY", "HAPPY"), "COMMON-001"),
                Arguments.of(COUNTS.replace("ANGRY", "FRUSTRATED"), "COMMON-001"),
                Arguments.of(COUNTS.replace(",\"ANGRY\":25", ""), "PRESS-EVENT-004"));
    }

    private RestTestClient.ResponseSpec 제출한다(String counts) {
        return client.post().uri(ATTEMPTS, EVENT_ID)
                .headers(headers -> headers.setBearerAuth("access-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"requestId":"%s","counts":%s}
                        """.formatted(REQUEST_ID, counts))
                .exchange();
    }
}
