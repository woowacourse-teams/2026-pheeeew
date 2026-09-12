package com.pheeeew.auth.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.fixture.JwtTestKeys;
import com.pheeeew.auth.infra.jwt.JwtProperties;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.report.domain.repository.SighReportRepository;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@Import(SharedPostgisTestConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityAuthorizationIntegrationTest {

    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 사칭하려는_기기_식별자 = UUID.fromString("00000000-0000-4000-8000-000000009999");
    private static final String 인증_필요_응답 = """
            {"code":"AUTH-001","message":"인증이 필요합니다."}
            """;
    private static final String 권한_없음_응답 = """
            {"code":"AUTH-002","message":"접근 권한이 없습니다."}
            """;
    private static final String 지도_영역_질의 =
            "?minLongitude=126.9&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6";
    private static final String 규칙에_없는_경로 = "/api/v2/unknown";

    @LocalServerPort
    private int port;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    @Autowired
    private SighRepository sighRepository;

    @Autowired
    private SighReportRepository sighReportRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void tearDown() {
        sighReportRepository.deleteAll();
        deviceRefreshTokenRepository.deleteAll();
        deviceRepository.deleteAll();
        sighRepository.deleteAll();
    }

    @Test
    void 토큰_없이_한숨을_등록하면_401을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(sighRepository.count()).isZero();
    }

    @Test
    void 토큰_없이_신고하면_401을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"sighId": 1, "reason": "광고성 게시물입니다"}
                        """)
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(sighReportRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("거부해야_하는_토큰들")
    void 쓸_수_없는_토큰은_403이_아니라_401로_거부한다(String 설명, String 토큰) {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/sighs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(sighRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic dXNlcjpwYXNz", "Bearer", "Bearer ", "eyJhbGciOiJSUzI1NiJ9"})
    void 인증_헤더_형식이_어긋나면_401을_반환한다(String authorization) {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/sighs")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        result.expectStatus().isUnauthorized();
        assertThat(sighRepository.count()).isZero();
    }

    @Test
    void 유효한_토큰으로_한숨을_등록하면_인증을_통과해_201을_반환한다() {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/sighs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        result.expectStatus().isCreated();
        assertThat(sighRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/sighs", "/api/v2/sighs"})
    void 인증_없이_한숨을_조회할_수_있다(String uri) {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(uri + 지도_영역_질의)
                .exchange();

        // then
        result.expectStatus().isOk();
    }

    @Test
    void 인증_없이_한숨_단건을_조회하는_요청은_필터를_통과한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/sighs/" + Long.MAX_VALUE)
                .exchange();

        // then
        result.expectStatus().isNotFound()
                .expectBody()
                .json("""
                        {"code":"SIGH-002","message":"한숨을 찾을 수 없습니다."}
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 등록되지_않은_기기의_토큰으로_신고하면_401_기기_없음을_반환한다() {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);
        Long sighId = 한숨을_등록한다(accessToken);

        // when
        RestTestClient.ResponseSpec result = 신고한다(accessToken, sighId);

        // then
        result.expectStatus().isUnauthorized()
                .expectBody()
                .json("""
                        {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        assertThat(sighReportRepository.count()).isZero();
    }

    @Test
    void 폐기_예정인_v1_한숨_등록은_아직_인증_없이_동작한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/sighs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"requestId": "%s", "latitude": 37.5664, "longitude": 126.9780}
                        """.formatted(UUID.randomUUID()))
                .exchange();

        // then
        result.expectStatus().isCreated();
        assertThat(sighRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"})
    void 인증_없이_상태_점검과_API_문서를_열_수_있다(String uri) {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(uri)
                .exchange();

        // then
        result.expectStatus().isOk();
    }

    @Test
    void 토큰_없이_규칙에_없는_경로를_호출하면_401을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(규칙에_없는_경로)
                .exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @Test
    void 유효한_토큰으로_규칙에_없는_경로를_호출하면_403을_반환한다() {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(규칙에_없는_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();

        // then
        result.expectStatus().isForbidden()
                .expectBody()
                .json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @Test
    void 인증_실패_응답은_토큰_조각과_실패_사유를_내보내지_않는다() {
        // given
        String accessToken = AccessTokenFixture.만료된_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"sighId": 1, "reason": "광고성 게시물입니다"}
                        """)
                .exchange();

        // then
        String 응답_본문 = result.expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(응답_본문)
                .doesNotContain(accessToken)
                .doesNotContain(기기_공개_식별자.toString());
        assertThat(응답_본문).isEqualTo("""
                {"code":"AUTH-001","message":"인증이 필요합니다."}""");
    }

    @Test
    void 인증_실패_경로는_토큰과_기기_공개_식별자를_로그에_남기지_않는다() {
        // given
        List<String> 거부되는_토큰들 = 거부해야_하는_토큰들()
                .map(arguments -> (String) arguments.get()[1])
                .toList();
        ListAppender<ILoggingEvent> appender = 로그_수집을_시작한다();

        try {
            // when
            for (String 토큰 : 거부되는_토큰들) {
                client.post()
                        .uri("/api/v2/sighs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(한숨_등록_본문())
                        .exchange()
                        .expectStatus().isUnauthorized();
            }

            // then
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).contains("/api/v2/sighs");
            assertThat(남은_로그).doesNotContain(기기_공개_식별자.toString());
            for (String 토큰 : 거부되는_토큰들) {
                assertThat(남은_로그).doesNotContain(토큰);
            }
        } finally {
            로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void 인증된_기기로_신고하면_본문의_기기_식별자를_무시하고_중복_신고를_막는다() {
        // given
        UUID requestId = UUID.randomUUID();
        String accessToken = 기기를_등록하고_토큰을_받는다(requestId);
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        Long sighId = 한숨을_등록한다(accessToken);

        // when
        RestTestClient.ResponseSpec 최초_신고 = 신고한다(accessToken, sighId);
        RestTestClient.ResponseSpec 다시_신고 = 신고한다(accessToken, sighId);

        // then
        최초_신고.expectStatus().isCreated();
        다시_신고.expectStatus().isOk();

        Map<String, Object> 저장된_신고 = jdbcClient.sql("SELECT reporter_device_id FROM sigh_reports")
                .query()
                .singleRow();
        assertThat(저장된_신고.get("reporter_device_id")).isEqualTo(device.getId());
        assertThat(device.getPublicId()).isNotEqualTo(사칭하려는_기기_식별자);
    }

    private static Stream<Arguments> 거부해야_하는_토큰들() {
        return Stream.of(
                Arguments.of("만료된 토큰", AccessTokenFixture.만료된_토큰(기기_공개_식별자)),
                Arguments.of("다른 키로 서명한 토큰", AccessTokenFixture.다른_키로_서명한_토큰(기기_공개_식별자)),
                Arguments.of("용도가 액세스가 아닌 토큰", AccessTokenFixture.용도가_액세스가_아닌_토큰(기기_공개_식별자)),
                Arguments.of("대상이 기기 공개 식별자가 아닌 토큰", AccessTokenFixture.대상이_기기_공개_식별자가_아닌_토큰()),
                Arguments.of("JWT 형식이 아닌 값", "not.a.jwt")
        );
    }

    private String 한숨_등록_본문() {
        return """
                {"requestId": "%s", "latitude": 37.5664, "longitude": 126.9780, "memo": "오늘은 조금 지쳤다"}
                """.formatted(UUID.randomUUID());
    }

    private void 인증_필요를_검증한다(RestTestClient.ResponseSpec result) {
        result.expectStatus().isUnauthorized()
                .expectBody()
                .json(인증_필요_응답, JsonCompareMode.STRICT);
    }

    private String 기기를_등록하고_토큰을_받는다(UUID requestId) {
        DeviceTokens tokens = client.post()
                .uri("/api/v2/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"requestId": "%s", "attestation": {"platform": "ANDROID"}}
                        """.formatted(requestId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(DeviceTokens.class)
                .returnResult()
                .getResponseBody();

        return tokens.accessToken();
    }

    private Long 한숨을_등록한다(String accessToken) {
        client.post()
                .uri("/api/v2/sighs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange()
                .expectStatus().isCreated();

        return jdbcClient.sql("SELECT id FROM sighs")
                .query(Long.class)
                .single();
    }

    private RestTestClient.ResponseSpec 신고한다(String accessToken, Long sighId) {
        return client.post()
                .uri("/api/v2/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"sighId": %d, "deviceId": "%s", "reason": "광고성 게시물입니다"}
                        """.formatted(sighId, 사칭하려는_기기_식별자))
                .exchange();
    }

    private ListAppender<ILoggingEvent> 로그_수집을_시작한다() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        루트_로거().addAppender(appender);
        인증_경로_로거().forEach(logger -> logger.setLevel(Level.TRACE));
        return appender;
    }

    private void 로그_수집을_끝낸다(ListAppender<ILoggingEvent> appender) {
        인증_경로_로거().forEach(logger -> logger.setLevel(null));
        루트_로거().detachAppender(appender);
        appender.stop();
    }

    private List<Logger> 인증_경로_로거() {
        return List.of(
                (Logger) LoggerFactory.getLogger("com.pheeeew"),
                (Logger) LoggerFactory.getLogger("org.springframework.security"),
                (Logger) LoggerFactory.getLogger("org.springframework.web"),
                (Logger) LoggerFactory.getLogger("org.hibernate.SQL")
        );
    }

    private String 수집한_로그(ListAppender<ILoggingEvent> appender) {
        StringBuilder collected = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            collected.append(event.getLoggerName()).append(' ').append(event.getFormattedMessage()).append('\n');
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            while (throwableProxy != null) {
                collected.append(throwableProxy.getMessage()).append('\n');
                throwableProxy = throwableProxy.getCause();
            }
        }
        return collected.toString();
    }

    private Logger 루트_로거() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }

    record DeviceTokens(String accessToken, String refreshToken, long expiresIn) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestKeyConfiguration {

        @Bean
        DynamicPropertyRegistrar jwtTestKeyProperties() {
            JwtProperties keys = JwtTestKeys.기본_키_설정();
            return registry -> {
                registry.add("pheeeew.jwt.private-key-base64", keys::privateKeyBase64);
                registry.add("pheeeew.jwt.public-key-base64", keys::publicKeyBase64);
            };
        }
    }
}
