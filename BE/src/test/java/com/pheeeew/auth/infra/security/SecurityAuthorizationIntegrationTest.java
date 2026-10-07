package com.pheeeew.auth.infra.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import static com.pheeeew.appversion.fixture.AppVersionFixture.기본_앱_버전_정책_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.appversion.domain.repository.AppVersionRepository;
import com.pheeeew.auth.fixture.AccessTokenFixture;
import com.pheeeew.auth.fixture.JwtTestKeys;
import com.pheeeew.auth.infra.jwt.JwtProperties;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceChallengeRepository;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.report.domain.repository.EmotionReportRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

@Import(SharedPostgisTestConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/",
        "pheeeew.s3.region=ap-northeast-2"
})
class SecurityAuthorizationIntegrationTest {

    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final UUID 사칭하려는_기기_식별자 = UUID.fromString("00000000-0000-4000-8000-000000009999");
    private static final String 인증_필요_응답 = """
            {"code":"AUTH-001","message":"인증이 필요합니다."}
            """;
    private static final String 권한_없음_응답 = """
            {"code":"AUTH-002","message":"접근 권한이 없습니다."}
            """;
    private static final String 증명_확인_불가_응답 = """
            {"code":"DEVICE-006","message":"무결성 증명을 확인할 수 없습니다."}
            """;
    private static final String 지도_영역_질의 =
            "?minLongitude=126.9&minLatitude=37.5&maxLongitude=127.1&maxLatitude=37.6";
    private static final String 규칙에_없는_경로 = "/api/v2/unknown";
    private static final String CHALLENGE_경로 = "/api/v2/devices/challenge";
    private static final String 닉네임_조회_경로 = "/api/v3/devices/nicknames/availability";
    private static final String 닉네임_등록_경로 = "/api/v3/devices";
    private static final String 내_닉네임_경로 = "/api/v3/devices/me/nickname";
    private static final String 무결성_토큰 = "integrity-token-from-app";

    @LocalServerPort
    private int port;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    @Autowired
    private DeviceChallengeRepository deviceChallengeRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private EmotionReportRepository emotionReportRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AppVersionRepository appVersionRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbcClient);
        jdbcClient.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP").update();
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void tearDown() {
        appVersionRepository.deleteAll();
        jdbcClient.sql("DELETE FROM emotion_blocks").update();
        jdbcClient.sql("DELETE FROM device_blocks").update();
        emotionReportRepository.deleteAll();
        emotionRepository.deleteAll();
        deviceRefreshTokenRepository.deleteAll();
        deviceRepository.deleteAll();
        deviceChallengeRepository.deleteAll();
        jdbcClient.sql("DELETE FROM regions").update();
        jdbcClient.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
    }

    @Test
    void 개인_프레스_v2_경로는_인증된_기기만_호출할_수_있다() {
        // given
        String token = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        String body = """
                {"longitude":126.9774,"latitude":37.5669,"counts":{}}
                """;

        // when
        RestTestClient.ResponseSpec unauthenticated = client.post().uri("/api/v2/emotions/presses")
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange();
        RestTestClient.ResponseSpec authenticated = client.post().uri("/api/v2/emotions/presses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange();

        // then
        인증_필요를_검증한다(unauthenticated);
        authenticated.expectStatus().isOk().expectBody()
                .jsonPath("$.regionCode").isEqualTo("11010530")
                .jsonPath("$.total").isEqualTo(0);
    }

    @Test
    void 내_닉네임_조회는_토큰의_기기만_조회하고_식별자나_토큰을_노출하지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceTokens tokens = 닉네임으로_등록한다(requestId, "Star  K").expectStatus().isCreated()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();
        String otherToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        UUID otherPublicId = deviceRepository.findAll().stream().filter(device -> !device.getRequestId().equals(requestId))
                .findFirst().orElseThrow().getPublicId();

        // when
        RestTestClient.ResponseSpec result = client.get().uri(builder -> builder.path(내_닉네임_경로)
                        .queryParam("devicePublicId", otherPublicId).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()).exchange();

        // then
        result.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"nickname":"Star  K"}
                        """, JsonCompareMode.STRICT);
        assertThat(otherToken).isNotBlank();
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void v2로_가입한_미설정_기기는_닉네임_필드를_생략하지_않고_null을_반환한다() {
        // given
        DeviceTokens tokens = 닉네임_없이_등록한다(UUID.randomUUID()).expectStatus().isCreated()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();

        // when
        RestTestClient.ResponseSpec result = client.get().uri(내_닉네임_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()).exchange();

        // then
        result.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("""
                        {"nickname":null}
                        """, JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 내_닉네임_조회는_인증_없이_호출할_수_없다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get().uri(내_닉네임_경로).exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("거부해야_하는_토큰들")
    void 내_닉네임_조회는_유효하지_않은_토큰을_거절한다(String description, String token) {
        // given / when
        RestTestClient.ResponseSpec result = client.get().uri(내_닉네임_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @Test
    void 내_닉네임_조회는_토큰이_유효해도_기기가_없으면_401을_반환한다() {
        // given
        String token = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get().uri(내_닉네임_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        // then
        result.expectStatus().isUnauthorized().expectBody().json("""
                {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH", "DELETE"})
    void 내_닉네임_GET과_PUT_외의_메서드는_유효한_토큰으로도_거절한다(String method) {
        // given
        String token = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.method(HttpMethod.valueOf(method)).uri(내_닉네임_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @Test
    void 내_닉네임_조회_하위_경로는_유효한_토큰으로도_거절한다() {
        // given
        String token = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get().uri(내_닉네임_경로 + "/extra")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v2/devices/me/nickname",
            "PUT, /api/v2/devices/me/nickname",
            "GET, /api/v2/devices/nicknames/availability"
    })
    void v3로_이동한_닉네임_API는_v2_경로로_접근할_수_없다(String method, String path) {
        // given
        String token = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.method(HttpMethod.valueOf(method)).uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 내_닉네임_수정은_사칭한_식별자를_무시하고_본인의_대소문자_변경만_처리한다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceTokens tokens = 닉네임으로_등록한다(requestId, "Star K").expectStatus().isCreated()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();
        UUID otherRequestId = UUID.randomUUID();
        닉네임으로_등록한다(otherRequestId, "다른 기기").expectStatus().isCreated();
        Device other = deviceRepository.findByRequestId(otherRequestId).orElseThrow();

        // when
        RestTestClient.ResponseSpec result = client.put().uri(builder -> builder.path(내_닉네임_경로)
                        .queryParam("devicePublicId", other.getPublicId()).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nickname", " star k ", "devicePublicId", other.getPublicId())).exchange();

        // then
        result.expectStatus().isNoContent().expectBody().isEmpty();
        client.get().uri(내_닉네임_경로).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                .exchange().expectStatus().isOk().expectBody().json("{\"nickname\":\"star k\"}", JsonCompareMode.STRICT);
        assertThat(deviceRepository.findByPublicId(other.getPublicId()).orElseThrow().getNickname()).isEqualTo("다른 기기");
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void v2로_가입한_미설정_기기는_같은_수정_API로_닉네임을_최초_설정한다() {
        // given
        DeviceTokens tokens = 닉네임_없이_등록한다(UUID.randomUUID()).expectStatus().isCreated()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();

        // when
        RestTestClient.ResponseSpec result = 닉네임을_수정한다(tokens.accessToken(), "{\"nickname\":\"  Star  K  \"}");

        // then
        result.expectStatus().isNoContent().expectBody().isEmpty();
        client.get().uri(내_닉네임_경로).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                .exchange().expectStatus().isOk().expectBody().json("{\"nickname\":\"Star  K\"}", JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 내_닉네임_수정이_다른_기기와_중복되면_409와_기존_닉네임을_유지한다() {
        // given
        String token = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        닉네임으로_등록한다(UUID.randomUUID(), "Star K").expectStatus().isCreated();

        // when
        RestTestClient.ResponseSpec result = 닉네임을_수정한다(token, "{\"nickname\":\" star k \"}");

        // then
        result.expectStatus().isEqualTo(409).expectBody().json(
                "{\"code\":\"DEVICE-009\",\"message\":\"이미 사용 중인 닉네임입니다.\"}", JsonCompareMode.STRICT);
        client.get().uri(내_닉네임_경로).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody().json("{\"nickname\":\"기기 A\"}", JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nickname\":null}"})
    void 닉네임_수정_요청에서_누락과_null을_거절한다(String body) {
        // given
        String token = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());

        // when
        RestTestClient.ResponseSpec result = 닉네임을_수정한다(token, body);

        // then
        result.expectStatus().isBadRequest().expectBody().json(
                "{\"code\":\"COMMON-001\",\"message\":\"요청 값이 올바르지 않습니다.\"}", JsonCompareMode.STRICT);
        assertThat(deviceRepository.findAll()).extracting(Device::getNickname).containsExactly("기기 A");
    }

    @Test
    void 규칙을_위반한_닉네임_수정은_400을_반환하고_기존_값을_유지한다() {
        // given
        String token = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());

        // when
        RestTestClient.ResponseSpec result = 닉네임을_수정한다(token, "{\"nickname\":\" 익명 \"}");

        // then
        result.expectStatus().isBadRequest().expectBody().json("""
                {"code":"DEVICE-008","message":"닉네임은 한글, 영문, 공백으로 1~10자여야 하며 익명은 사용할 수 없습니다."}
                """, JsonCompareMode.STRICT);
        assertThat(deviceRepository.findAll()).extracting(Device::getNickname).containsExactly("기기 A");
    }

    @Test
    void 내_닉네임_수정은_인증_없이_호출할_수_없다() {
        // given / when
        RestTestClient.ResponseSpec result = client.put().uri(내_닉네임_경로).contentType(MediaType.APPLICATION_JSON)
                .body("{\"nickname\":\"스타크\"}").exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("거부해야_하는_토큰들")
    void 내_닉네임_수정은_유효하지_않은_토큰을_거절한다(String description, String token) {
        // given / when
        RestTestClient.ResponseSpec result = 닉네임을_수정한다(token, "{\"nickname\":\"스타크\"}");

        // then
        인증_필요를_검증한다(result);
    }

    @Test
    void 내_닉네임_수정은_유효한_토큰이어도_기기가_없으면_401을_반환한다() {
        // given
        String token = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = 닉네임을_수정한다(token, "{\"nickname\":\"스타크\"}");

        // then
        result.expectStatus().isUnauthorized().expectBody().json(
                "{\"code\":\"DEVICE-004\",\"message\":\"인증 정보를 사용할 수 없습니다.\"}", JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void 내_닉네임_수정_하위_경로는_유효한_토큰으로도_거절한다() {
        // given
        String token = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.put().uri(내_닉네임_경로 + "/extra")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body("{\"nickname\":\"스타크\"}").exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @Test
    void v2는_닉네임_없이_등록하고_같은_요청으로_재시도할_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceTokens first = 닉네임_없이_등록한다(requestId).expectStatus().isCreated()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();
        Device firstDevice = deviceRepository.findByRequestId(requestId).orElseThrow();

        // when
        DeviceTokens retried = 닉네임_없이_등록한다(requestId).expectStatus().isOk()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(device.getNickname()).isNull();
        assertThat(device.getPublicId()).isEqualTo(firstDevice.getPublicId());
        assertThat(first.accessToken()).isNotBlank();
        assertThat(retried.accessToken()).isNotBlank();
        assertThat(retried.refreshToken()).isNotBlank().isNotEqualTo(first.refreshToken());
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void v2와_v3를_바꿔_재시도해도_최초_기기의_닉네임을_유지한다(boolean legacyFirst) {
        // given
        UUID requestId = UUID.randomUUID();
        RestTestClient.ResponseSpec first = legacyFirst
                ? 닉네임_없이_등록한다(requestId) : 닉네임으로_등록한다(requestId, "스타크");
        first.expectStatus().isCreated();
        Device firstDevice = deviceRepository.findByRequestId(requestId).orElseThrow();

        // when
        RestTestClient.ResponseSpec retried = legacyFirst
                ? 닉네임으로_등록한다(requestId, "스타크") : 닉네임_없이_등록한다(requestId);

        // then
        retried.expectStatus().isOk().expectBody().jsonPath("$.accessToken").isNotEmpty();
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(device.getNickname()).isEqualTo(legacyFirst ? null : "스타크");
        assertThat(device.getPublicId()).isEqualTo(firstDevice.getPublicId());
        assertThat(device.getPlatform()).isEqualTo(firstDevice.getPlatform());
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 닉네임과_함께_기기를_등록하면_정규화하여_저장하고_토큰을_발급한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceTokens tokens = 닉네임으로_등록한다(requestId, "  Star K  ").expectStatus().isCreated()
                .expectBody(DeviceTokens.class).returnResult().getResponseBody();

        // then
        assertThat(deviceRepository.findByRequestId(requestId).orElseThrow().getNickname()).isEqualTo("Star K");
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresIn()).isEqualTo(1800);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " 익명 ", "Star1", "abcdefghijk", "Star\tK"})
    void 닉네임_규칙을_어긴_등록은_400을_반환하고_기기와_토큰을_남기지_않는다(String nickname) {
        // given / when
        RestTestClient.ResponseSpec result = 닉네임으로_등록한다(UUID.randomUUID(), nickname);

        // then
        result.expectStatus().isBadRequest().expectBody().json("""
                {"code":"DEVICE-008","message":"닉네임은 한글, 영문, 공백으로 1~10자여야 하며 익명은 사용할 수 없습니다."}
                """, JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 닉네임이_중복된_등록은_409를_반환하고_기기와_토큰을_추가하지_않는다() {
        // given
        닉네임으로_등록한다(UUID.randomUUID(), "Star K").expectStatus().isCreated();
        UUID failedRequestId = UUID.randomUUID();

        // when
        RestTestClient.ResponseSpec result = 닉네임으로_등록한다(failedRequestId, " star k ");

        // then
        result.expectStatus().isEqualTo(409).expectBody().json("""
                {"code":"DEVICE-009","message":"이미 사용 중인 닉네임입니다."}
                """, JsonCompareMode.STRICT);
        assertThat(deviceRepository.findByRequestId(failedRequestId)).isEmpty();
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 등록_재시도에서_다른_닉네임을_보내도_최초_닉네임과_플랫폼을_유지한다() {
        // given
        UUID requestId = UUID.randomUUID();
        닉네임으로_등록한다(requestId, "스타크").expectStatus().isCreated();

        // when
        RestTestClient.ResponseSpec result = client.post().uri(닉네임_등록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", requestId, "nickname", "다른 닉네임", "attestation", Map.of("platform", "IOS")))
                .exchange();

        // then
        result.expectStatus().isOk();
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(device.getNickname()).isEqualTo("스타크");
        assertThat(device.getPlatform()).isEqualTo(com.pheeeew.device.domain.DevicePlatform.ANDROID);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ", \"nickname\": null"})
    void v3_등록은_닉네임_누락이나_null을_거절한다(String nicknameField) {
        // given / when
        RestTestClient.ResponseSpec result = client.post().uri(닉네임_등록_경로)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"requestId":"%s", "attestation":{"platform":"ANDROID"}%s}
                        """.formatted(UUID.randomUUID(), nicknameField)).exchange();

        // then
        result.expectStatus().isBadRequest().expectBody().json("""
                {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                """, JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void v3_등록_POST_외의_메서드는_유효한_토큰으로도_거절한다(String method) {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.method(HttpMethod.valueOf(method)).uri(닉네임_등록_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/extra", "/tokens", "/challenge", "/nicknames/availability"})
    void v3_등록_하위_경로는_유효한_토큰으로도_거절한다(String suffix) {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.post().uri(닉네임_등록_경로 + suffix)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("거부해야_하는_토큰들")
    void 공개_v3_등록에도_유효하지_않은_토큰을_보내면_401을_반환한다(String description, String token) {
        // given / when
        RestTestClient.ResponseSpec result = client.post().uri(닉네임_등록_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", UUID.randomUUID(), "nickname", "스타크",
                        "attestation", Map.of("platform", "ANDROID"))).exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 토큰_없이_닉네임_사용_가능_여부만_조회하고_캐시나_기기나_토큰을_남기지_않는다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(builder -> builder.path(닉네임_조회_경로).queryParam("nickname", "잠에서 깨는 너구리").build())
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"available\":true}", JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Star K", "star k", " STAR K "})
    void 토큰_없이_중복_닉네임을_조회하면_기기_정보_없이_false만_반환한다(String nickname) {
        // given
        deviceRepository.saveAndFlush(기본_기기_빌더().nickname("Star K").build());

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(builder -> builder.path(닉네임_조회_경로).queryParam("nickname", nickname).build())
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().json("{\"available\":false}", JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " 익명 ", "Star1", "abcdefghijk", "Star\tK"})
    void 토큰_없이_잘못된_닉네임을_조회하면_400을_반환한다(String nickname) {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(builder -> builder.path(닉네임_조회_경로).queryParam("nickname", nickname).build())
                .exchange();

        // then
        result.expectStatus().isBadRequest().expectBody().json("""
                {"code":"DEVICE-008","message":"닉네임은 한글, 영문, 공백으로 1~10자여야 하며 익명은 사용할 수 없습니다."}
                """, JsonCompareMode.STRICT);
    }

    @Test
    void 닉네임_조회_파라미터가_없으면_400을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get().uri(닉네임_조회_경로).exchange();

        // then
        result.expectStatus().isBadRequest().expectBody().json("""
                {"code":"COMMON-001","message":"요청 값이 올바르지 않습니다."}
                """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void 닉네임_조회_GET_외의_메서드는_유효한_토큰으로도_접근할_수_없다(String method) {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.method(HttpMethod.valueOf(method))
                .uri(닉네임_조회_경로)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @Test
    void 닉네임_조회_경로의_하위_경로도_유효한_토큰으로_접근할_수_없다() {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get().uri(닉네임_조회_경로 + "/extra")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @Test
    void 공개_닉네임_조회에도_만료된_토큰을_보내면_401을_반환한다() {
        // given
        String expiredToken = AccessTokenFixture.만료된_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(builder -> builder.path(닉네임_조회_경로).queryParam("nickname", "Star K").build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken).exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @Test
    void 토큰_없이_활성_앱_버전을_조회할_수_있다() {
        // given
        appVersionRepository.save(기본_앱_버전_정책_빌더().build());

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/app/version?platform=android").exchange();

        // then
        result.expectStatus().isOk().expectBody().json("""
                {"minSupportedVersion":"1.0.0","latestVersion":"1.1.0","storeUrl":"https://example.com/app"}
                """, JsonCompareMode.STRICT);
    }

    @Test
    void 토큰_없이_등록되지_않은_앱_버전을_조회하면_404를_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v2/app/version?platform=android").exchange();

        // then
        result.expectStatus().isNotFound().expectBody().json("""
                {"code":"APP_VERSION-002","message":"활성 앱 버전 정책을 찾을 수 없습니다."}
                """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v2/app/version", "PUT, /api/v2/app/version",
            "PATCH, /api/v2/app/version", "DELETE, /api/v2/app/version", "GET, /api/v1/app/version"
    })
    void 앱_버전_v2_GET_외의_경로와_메서드는_유효한_토큰으로도_접근할_수_없다(String method, String uri) {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.method(HttpMethod.valueOf(method))
                .uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();

        // then
        result.expectStatus().isForbidden().expectBody().json(권한_없음_응답, JsonCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions", "/api/v3/emotions", "/api/v1/audio-uploads"})
    void 토큰_없이_감정_등록이나_녹음_업로드를_요청하면_401을_반환한다(String uri) {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 토큰_없이_신고하면_401을_반환한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v2/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"emotionId": 1, "reason": "광고성 게시물입니다"}
                        """)
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(emotionReportRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("인증이_필요한_차단_경로들")
    void 토큰_없이_차단_API를_호출하면_401을_반환한다(String method, String uri) {
        // given / when
        RestTestClient.ResponseSpec result = client.method(HttpMethod.valueOf(method))
                .uri(uri)
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(차단_행_수()).isZero();
    }

    @Test
    void 이전_한숨_차단_경로는_더_이상_제공하지_않는다() {
        String accessToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());

        차단_목록을_조회한다(accessToken, "/api/v2/blocks/sighs")
                .expectStatus().isNotFound();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions"})
    void 조회_경로에_쓸_수_없는_토큰을_보내면_결과_대신_401을_반환한다(String uri) {
        // given
        String 만료된_토큰 = AccessTokenFixture.만료된_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(uri + 지도_영역_질의)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + 만료된_토큰)
                .exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions"})
    void 인증된_기기가_조회하면_차단_필터를_켜고_200을_반환한다(String uri) {
        // given
        String accessToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        작성자를_모르는_한숨을_넣는다();

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(uri + 지도_영역_질의)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();

        // then
        result.expectStatus().isOk();
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/emotions,/api/v3/emotions,true", "/api/v3/emotions,/api/v1/emotions,false"})
    void 버전을_바꾼_재시도는_최초_익명_선택과_작성자를_유지한다(String first, String next, boolean anonymous) {
        // given
        UUID deviceRequestId = UUID.randomUUID();
        String token = 기기를_등록하고_토큰을_받는다(deviceRequestId);
        Device device = deviceRepository.findByRequestId(deviceRequestId).orElseThrow();
        UUID requestId = UUID.randomUUID();
        Map<String, Object> body = Map.of("requestId", requestId, "latitude", 37.5664, "longitude", 126.9780,
                "state", "FRUSTRATED", "rotationDegrees", 0, "contentType", "NONE", "anonymous", false);
        client.post().uri(first + "?devicePublicId=" + 사칭하려는_기기_식별자)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).exchange().expectStatus().isOk();
        Long id = emotionRepository.findByRequestId(requestId).orElseThrow().getId();

        // when
        RestTestClient.ResponseSpec result = client.post().uri(next)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", requestId, "latitude", 37.5664, "longitude", 126.9780,
                        "state", "FRUSTRATED", "rotationDegrees", 0, "contentType", "NONE", "anonymous", true)).exchange();

        // then
        result.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/emotions/" + id)
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().jsonPath("$.id").isEqualTo(id.intValue());
        assertThat(emotionRepository.count()).isEqualTo(1);
        var saved = emotionRepository.findById(id).orElseThrow();
        assertThat(saved.isAnonymous()).isEqualTo(anonymous);
        assertThat(saved.getDeviceId()).isEqualTo(device.getId());
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/v3/emotions", "PUT,/api/v3/emotions", "DELETE,/api/v3/emotions/1",
            "POST,/api/v3/emotions/1", "GET,/api/v3/emotions/map"})
    void v3_감정_등록_외의_경로와_메서드는_열지_않는다(String method, String path) {
        // given / when
        client.method(HttpMethod.valueOf(method)).uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AccessTokenFixture.유효한_토큰(기기_공개_식별자))
                .exchange().expectStatus().isForbidden();

        // then
        assertThat(emotionRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("거부해야_하는_토큰들")
    void 쓸_수_없는_토큰은_403이_아니라_401로_거부한다(String 설명, String 토큰) {
        // given / when
        for (String uri : List.of("/api/v1/emotions", "/api/v3/emotions")) {
            RestTestClient.ResponseSpec result = client.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

            // then
            인증_필요를_검증한다(result);
            assertThat(emotionRepository.count()).isZero();
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {"Basic dXNlcjpwYXNz", "Bearer", "Bearer ", "eyJhbGciOiJSUzI1NiJ9"})
    void 인증_헤더_형식이_어긋나면_401을_반환한다(String authorization) {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        result.expectStatus().isUnauthorized();
        assertThat(emotionRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 지역_검사보다_인증을_먼저_적용한다(boolean boundariesReady) {
        // given
        if (!boundariesReady) {
            jdbcClient.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        }

        // when
        RestTestClient.ResponseSpec result = client.post().uri("/api/v1/emotions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", UUID.randomUUID(), "state", "FRUSTRATED", "contentType", "NONE",
                        "longitude", 0, "latitude", 0, "rotationDegrees", 0)).exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(emotionRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 지원_범위_밖_400과_자료_미준비_503을_다른_오류_본문으로_반환한다(boolean boundariesReady) {
        // given
        String accessToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        if (!boundariesReady) {
            jdbcClient.sql("UPDATE region_datasets SET boundaries_verified_at = NULL").update();
        }

        // when
        RestTestClient.ResponseSpec result = client.post().uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", UUID.randomUUID(), "state", "FRUSTRATED", "contentType", "NONE",
                        "longitude", 0, "latitude", 0, "rotationDegrees", 0)).exchange();

        // then
        result.expectStatus().isEqualTo(boundariesReady ? 400 : 503).expectBody().json(boundariesReady ? """
                {"code":"EMOTION-014","message":"이 위치에서는 기록할 수 없습니다. 다른 위치를 선택해 주세요."}
                """ : """
                {"code":"EMOTION-013","message":"지역 분류 자료를 사용할 수 없습니다."}
                """, JsonCompareMode.STRICT);
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 등록된_기기의_유효한_토큰으로_감정을_등록하거나_재시도하면_200을_반환한다() {
        // given
        String accessToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        String requestBody = 한숨_등록_본문();

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange();

        // then
        result.expectStatus().isOk();

        // when
        RestTestClient.ResponseSpec retried = client.post()
                .uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange();

        // then
        retried.expectStatus().isOk();
        assertThat(emotionRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions", "/api/v3/emotions", "/api/v1/audio-uploads"})
    void 등록되지_않은_기기는_감정_등록이나_업로드를_요청할_수_없다(String uri) {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(uri.equals("/api/v1/audio-uploads")
                        ? "{\"contentType\":\"audio/mp4\",\"contentLength\":1024}" : 한숨_등록_본문())
                .exchange();

        // then
        result.expectStatus().isUnauthorized()
                .expectBody()
                .json("""
                        {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        assertThat(emotionRepository.count()).isZero();
        assertThat(jdbcClient.sql("SELECT count(*) FROM audio_uploads").query(Long.class).single()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 등록되지_않은_기기의_토큰으로는_신규_등록과_재요청을_모두_거부한다(boolean existingEmotion) {
        // given
        String requestBody = 한숨_등록_본문();
        if (existingEmotion) {
            String registeredDeviceToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
            client.post()
                    .uri("/api/v1/emotions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + registeredDeviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isOk();
        }
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);
        long originalCount = emotionRepository.count();

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange();

        // then
        result.expectStatus().isUnauthorized()
                .expectBody()
                .json("""
                        {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        assertThat(emotionRepository.count()).isEqualTo(originalCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions" + 지도_영역_질의, "/api/v1/emotions/42"})
    void 토큰_없이_v2_목록이나_단건을_조회하면_401을_반환한다(String uri) {
        // given / when
        RestTestClient.ResponseSpec result = client.get().uri(uri).exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/emotions" + 지도_영역_질의, "/api/v1/emotions/42"})
    void 만료된_토큰으로_v2_목록이나_단건을_조회하면_401을_반환한다(String uri) {
        // given
        String accessToken = AccessTokenFixture.만료된_토큰(기기_공개_식별자);

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();

        // then
        인증_필요를_검증한다(result);
    }

    @Test
    void 유효한_토큰으로_v2_목록과_단건을_조회할_수_있다() {
        // given
        String accessToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        Long emotionId = 한숨을_등록한다(accessToken);

        // when
        RestTestClient.ResponseSpec listResult = client.get()
                .uri("/api/v1/emotions" + 지도_영역_질의)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
        RestTestClient.ResponseSpec detailResult = client.get()
                .uri("/api/v1/emotions/" + emotionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();

        // then
        listResult.expectStatus().isOk().expectBody().jsonPath("$.items[0].id").isEqualTo(emotionId.intValue());
        detailResult.expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(emotionId.intValue());
    }

    @Test
    void 본인_감정만_수정과_소프트_삭제할_수_있다() {
        // given
        String ownerToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        String otherToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        Long id = 한숨을_등록한다(ownerToken);
        String uri = "/api/v1/emotions/" + id;
        String body = "{\"state\":\"ANGRY\",\"contentType\":\"MEMO\",\"memo\":\"수정한 메모\"}";

        // when / then
        client.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isNotFound();
        client.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .exchange().expectStatus().isNotFound();
        client.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isNoContent();
        client.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.properties.state").isEqualTo("ANGRY")
                .jsonPath("$.properties.memo").isEqualTo("수정한 메모");
        for (int retry = 0; retry < 2; retry++) {
            client.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                    .exchange().expectStatus().isNoContent();
        }
        client.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .exchange().expectStatus().isNotFound();
        client.put().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/emotions" + 지도_영역_질의).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
        assertThat(emotionRepository.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void 유효한_토큰으로_없는_한숨을_조회하면_404를_반환한다() {
        // given
        String accessToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());

        // when
        RestTestClient.ResponseSpec result = client.get()
                .uri("/api/v1/emotions/" + Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();

        // then
        result.expectStatus().isNotFound()
                .expectBody()
                .json("""
                        {"code":"EMOTION-002","message":"감정을 찾을 수 없습니다."}
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void 등록되지_않은_기기의_토큰으로_신고하면_401_기기_없음을_반환한다() {
        // given
        String accessToken = AccessTokenFixture.유효한_토큰(기기_공개_식별자);
        String registeredDeviceToken = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        Long emotionId = 한숨을_등록한다(registeredDeviceToken);

        // when
        RestTestClient.ResponseSpec result = 신고한다(accessToken, emotionId);

        // then
        result.expectStatus().isUnauthorized()
                .expectBody()
                .json("""
                        {"code":"DEVICE-004","message":"인증 정보를 사용할 수 없습니다."}
                        """, JsonCompareMode.STRICT);
        assertThat(emotionReportRepository.count()).isZero();
    }

    @Test
    void 인증한_기기가_등록한_한숨은_작성자를_저장하고_응답에는_내보내지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        String accessToken = 기기를_등록하고_토큰을_받는다(requestId);
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();

        // when
        RestTestClient.ResponseSpec result = client.post()
                .uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange();

        // then
        String 응답_본문 = result.expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(응답_본문)
                .doesNotContain(device.getPublicId().toString())
                .doesNotContain("device");
        assertThat(한숨_속성_이름들(응답_본문))
                .containsExactly("id");
        assertThat(작성자_기기_식별자()).isEqualTo(device.getId());
    }

    @Test
    void 인증한_기기는_감정_차단과_사용자_차단을_등록하고_조회하고_해제할_수_있다() {
        // given
        String 차단자_토큰 = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        String 작성자_토큰 = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        Long emotionId = 한숨을_등록한다(작성자_토큰);

        // when / then
        차단한다(차단자_토큰, "/api/v2/blocks/emotions", emotionId).expectStatus().isCreated();
        차단_목록을_조회한다(차단자_토큰, "/api/v2/blocks/emotions")
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        {"items": [{"emotionId": %d}], "hasNext": false, "nextCursor": null}
                        """.formatted(emotionId), JsonCompareMode.LENIENT);
        해제한다(차단자_토큰, "/api/v2/blocks/emotions/" + emotionId).expectStatus().isNoContent();

        차단한다(차단자_토큰, "/api/v2/blocks/devices", emotionId).expectStatus().isCreated();
        Long blockId = jdbcClient.sql("SELECT id FROM device_blocks").query(Long.class).single();
        차단_목록을_조회한다(차단자_토큰, "/api/v2/blocks/devices")
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        {"items": [{"blockId": %d, "emotionId": %d}], "hasNext": false, "nextCursor": null}
                        """.formatted(blockId, emotionId), JsonCompareMode.LENIENT);
        해제한다(차단자_토큰, "/api/v2/blocks/devices/" + blockId).expectStatus().isNoContent();

        assertThat(차단_행_수()).isZero();
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
    void 닉네임_API_문서는_v3_경로만_제공한다() throws JsonProcessingException {
        // given / when
        String body = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode paths = new ObjectMapper().readTree(body).path("paths");

        // then
        assertThat(paths.path(내_닉네임_경로).has("get")).isTrue();
        assertThat(paths.path(내_닉네임_경로).has("put")).isTrue();
        assertThat(paths.path(닉네임_조회_경로).has("get")).isTrue();
        assertThat(paths.has("/api/v2/devices/me/nickname")).isFalse();
        assertThat(paths.has("/api/v2/devices/nicknames/availability")).isFalse();
        assertThat(paths.path(내_닉네임_경로).path("put").path("description").asText())
                .contains("GET /api/v3/devices/me/nickname");
    }

    @Test
    void 같은_컨트롤러의_v2와_v3_등록_API_문서는_서로_다른_요청_계약을_유지한다() throws JsonProcessingException {
        // given / when
        String body = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode docs = new ObjectMapper().readTree(body);

        // then
        JsonNode v2 = docs.path("paths").path("/api/v2/devices").path("post");
        JsonNode v3 = docs.path("paths").path("/api/v3/devices").path("post");
        assertThat(v2.path("deprecated").asBoolean()).isTrue();
        assertThat(v2.path("summary").asText()).isEqualTo("기기 등록 (구버전 호환)");
        assertThat(v3.path("deprecated").asBoolean()).isFalse();
        assertThat(v2.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/DeviceCreateRequest");
        assertThat(v3.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/DeviceV3CreateRequest");
        assertThat(v2.path("operationId").asText()).isNotBlank().isNotEqualTo(v3.path("operationId").asText());
        JsonNode schemas = docs.path("components").path("schemas");
        assertThat(schemas.path("DeviceCreateRequest").path("properties").has("nickname")).isFalse();
        assertThat(schemas.path("DeviceV3CreateRequest").path("required").toString()).contains("\"nickname\"");
    }

    @Test
    void 감정_등록_API_문서는_v1과_v3의_익명_선택_계약을_구분한다() throws JsonProcessingException {
        // given / when
        String body = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode docs = new ObjectMapper().readTree(body);
        JsonNode paths = docs.path("paths");
        JsonNode schemas = docs.path("components").path("schemas");

        // then
        assertThat(paths.path("/api/v1/emotions").path("post").path("deprecated").asBoolean()).isTrue();
        assertThat(paths.path("/api/v1/emotions").path("post").path("summary").asText())
                .isEqualTo("감정 등록 (구버전 호환)");
        assertThat(paths.path("/api/v3/emotions").path("post").path("deprecated").asBoolean()).isFalse();
        assertThat(paths.path("/api/v1/emotions").path("get").path("deprecated").asBoolean()).isFalse();
        assertThat(paths.path("/api/v1/emotions").path("post").path("requestBody").path("content")
                .path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/EmotionCreateRequest");
        assertThat(paths.path("/api/v3/emotions").path("post").path("requestBody").path("content")
                .path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/EmotionV3CreateRequest");
        assertThat(schemas.path("EmotionCreateRequest").path("properties").has("anonymous")).isFalse();
        assertThat(schemas.path("EmotionV3CreateRequest").path("properties").has("anonymous")).isTrue();
        assertThat(paths.path("/api/v3/emotions").has("get")).isFalse();
        assertThat(paths.has("/api/v3/emotions/{emotionId}")).isFalse();
    }

    @Test
    void API_문서는_감정_차단_경로와_감정_식별자_필드를_제공한다() throws JsonProcessingException {
        String body = client.get().uri("/v3/api-docs")
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        JsonNode docs = new ObjectMapper().readTree(body);

        assertThat(docs.path("paths").has("/api/v2/blocks/emotions")).isTrue();
        assertThat(docs.path("paths").has("/api/v2/blocks/emotions/{emotionId}")).isTrue();
        assertThat(docs.path("paths").has("/api/v2/blocks/sighs")).isFalse();

        JsonNode schemas = docs.path("components").path("schemas");
        assertThat(schemas.path("BlockCreateRequest").path("properties").has("emotionId")).isTrue();
        assertThat(schemas.path("EmotionBlockResponse").path("properties").has("emotionId")).isTrue();
        assertThat(schemas.path("DeviceBlockResponse").path("properties").has("emotionId")).isTrue();
        assertThat(schemas.has("SighBlockResponse")).isFalse();

        for (String path : List.of("/api/v2/reports", "/api/v2/blocks/emotions", "/api/v2/blocks/devices")) {
            String operation = docs.path("paths").path(path).path("post").toString();
            assertThat(operation).contains("EMOTION-011", "감정을 찾을 수 없습니다.")
                    .doesNotContain("한숨을 찾을 수 없습니다.");
        }
    }

    @Test
    void 인증_없이_무결성_증명_challenge_를_발급받을_수_있다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri(CHALLENGE_경로)
                .exchange();

        // then
        String challenge = result.expectStatus().isOk()
                .expectBody(DeviceChallenge.class)
                .returnResult()
                .getResponseBody()
                .challenge();
        assertThat(challenge).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(deviceChallengeRepository.count()).isOne();
    }

    @Test
    void challenge_응답은_중간_경로에_저장되지_않게_no_store_를_붙인다() {
        // given / when
        RestTestClient.ResponseSpec result = client.post()
                .uri(CHALLENGE_경로)
                .exchange();

        // then
        result.expectStatus().isOk()
                .expectHeader().valueMatches(HttpHeaders.CACHE_CONTROL, ".*no-store.*");
    }

    @Test
    void challenge_경로는_POST_만_열리고_GET_은_인증을_요구한다() {
        // given / when
        RestTestClient.ResponseSpec result = client.get()
                .uri(CHALLENGE_경로)
                .exchange();

        // then
        인증_필요를_검증한다(result);
        assertThat(deviceChallengeRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/devices", "/api/v3/devices"})
    void 무결성_증명을_보낸_등록은_자격증명이_없으면_403으로_거절한다(String path) {
        // given / when
        RestTestClient.ResponseSpec result = 증명을_담아_등록한다(UUID.randomUUID(), null, path);

        // then
        result.expectStatus().isForbidden()
                .expectBody()
                .json(증명_확인_불가_응답, JsonCompareMode.STRICT);
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void challenge_발급과_증명_거절_경로는_challenge_값과_무결성_토큰을_로그에_남기지_않는다() {
        // given
        ListAppender<ILoggingEvent> appender = 개발_프로파일_로그_수집을_시작한다();

        try {
            // when
            String challenge = challenge_를_발급받는다();
            증명을_담아_등록한다(UUID.randomUUID(), challenge).expectStatus().isForbidden();

            // then
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).doesNotContain(challenge);
            assertThat(남은_로그).doesNotContain(무결성_토큰);
        } finally {
            개발_프로파일_로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void 서버_웹_계층_본문_로그를_켜도_challenge_값과_무결성_토큰이_로그에_남지_않는다() {
        // given
        ListAppender<ILoggingEvent> appender = 서버_웹_계층_로그_수집을_시작한다();

        try {
            // when
            String challenge = challenge_를_발급받는다();
            증명을_담아_등록한다(UUID.randomUUID(), challenge).expectStatus().isForbidden();

            // then
            String 남은_로그 = 수집한_로그(appender);
            assertThat(웹_계층_로거().getLevel()).isEqualTo(Level.TRACE);
            assertThat(테스트_클라이언트_로거().getLevel()).isEqualTo(Level.INFO);
            assertThat(남은_로그).contains("DeviceChallengeResponse[challenge=<redacted>, expiresIn=300]");
            assertThat(남은_로그).contains("DeviceAttestationRequest[platform=ANDROID, "
                    + "token=<redacted>, challenge=<redacted>, keyId=<redacted>]");
            assertThat(남은_로그).doesNotContain(challenge);
            assertThat(남은_로그).doesNotContain(무결성_토큰);
        } finally {
            서버_웹_계층_로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void SQL_오류_로그_억제_설정이_실제로_적용되어_있다() {
        // given / when
        Logger hibernateJdbcErrorLogger = (Logger) LoggerFactory.getLogger("org.hibernate.orm.jdbc.error");

        // then
        assertThat(hibernateJdbcErrorLogger.getLevel()).isEqualTo(Level.ERROR);
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
                        {"emotionId": 1, "reason": "광고성 게시물입니다"}
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
                        .uri("/api/v1/emotions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + 토큰)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(한숨_등록_본문())
                        .exchange()
                        .expectStatus().isUnauthorized();
            }

            // then
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).contains("/api/v1/emotions");
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
        Long emotionId = 한숨을_등록한다(기기를_등록하고_토큰을_받는다(UUID.randomUUID()));

        UUID requestId = UUID.randomUUID();
        String accessToken = 기기를_등록하고_토큰을_받는다(requestId);
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();

        // when
        RestTestClient.ResponseSpec 최초_신고 = 신고한다(accessToken, emotionId);
        RestTestClient.ResponseSpec 다시_신고 = 신고한다(accessToken, emotionId);

        // then
        최초_신고.expectStatus().isCreated()
                .expectBody()
                .json("""
                        {"emotionId": %d}
                        """.formatted(emotionId), JsonCompareMode.LENIENT);
        다시_신고.expectStatus().isOk();

        Map<String, Object> 저장된_신고 = jdbcClient.sql("SELECT reporter_device_id FROM emotion_reports")
                .query()
                .singleRow();
        assertThat(저장된_신고.get("reporter_device_id")).isEqualTo(device.getId());
        assertThat(device.getPublicId()).isNotEqualTo(사칭하려는_기기_식별자);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/sighs", "/api/v2/sighs"})
    void 폐기된_한숨_경로는_유효한_토큰으로도_허용하지_않는다(String uri) {
        String token = 기기를_등록하고_토큰을_받는다(UUID.randomUUID());
        client.get().uri(uri + 지도_영역_질의).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isForbidden();
        client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(한숨_등록_본문())
                .exchange().expectStatus().isForbidden();
        assertThat(emotionRepository.count()).isZero();
    }

    private static Stream<Arguments> 인증이_필요한_차단_경로들() {
        return Stream.of(
                Arguments.of("POST", "/api/v2/blocks/emotions"),
                Arguments.of("GET", "/api/v2/blocks/emotions"),
                Arguments.of("DELETE", "/api/v2/blocks/emotions/1"),
                Arguments.of("POST", "/api/v2/blocks/devices"),
                Arguments.of("GET", "/api/v2/blocks/devices"),
                Arguments.of("DELETE", "/api/v2/blocks/devices/1")
        );
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
                {"requestId": "%s", "latitude": 37.5664, "longitude": 126.9780, "memo": "오늘은 조금 지쳤다", "state": "FRUSTRATED", "rotationDegrees": 0, "contentType": "MEMO"}
                """.formatted(UUID.randomUUID());
    }

    private void 인증_필요를_검증한다(RestTestClient.ResponseSpec result) {
        result.expectStatus().isUnauthorized()
                .expectBody()
                .json(인증_필요_응답, JsonCompareMode.STRICT);
    }

    private RestTestClient.ResponseSpec 닉네임을_수정한다(String token, String body) {
        return client.put().uri(내_닉네임_경로).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private String 기기를_등록하고_토큰을_받는다(UUID requestId) {
        String nickname = "기기 " + (char) ('A' + deviceRepository.count());
        DeviceTokens tokens = 닉네임으로_등록한다(requestId, nickname)
                .expectStatus().isCreated()
                .expectBody(DeviceTokens.class)
                .returnResult()
                .getResponseBody();

        return tokens.accessToken();
    }

    private RestTestClient.ResponseSpec 닉네임_없이_등록한다(UUID requestId) {
        return client.post().uri("/api/v2/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", requestId, "attestation", Map.of("platform", "ANDROID")))
                .exchange();
    }

    private RestTestClient.ResponseSpec 닉네임으로_등록한다(UUID requestId, String nickname) {
        return client.post().uri(닉네임_등록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requestId", requestId, "nickname", nickname, "attestation", Map.of("platform", "ANDROID")))
                .exchange();
    }

    private RestTestClient.ResponseSpec 증명을_담아_등록한다(UUID requestId, String challenge) {
        return 증명을_담아_등록한다(requestId, challenge, "/api/v2/devices");
    }

    private RestTestClient.ResponseSpec 증명을_담아_등록한다(UUID requestId, String challenge, String path) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"requestId": "%s", "nickname": "스타크", "attestation": {
                          "platform": "ANDROID", "token": "%s", "challenge": %s
                        }}
                        """.formatted(requestId, 무결성_토큰, challenge == null ? "null" : "\"" + challenge + "\""))
                .exchange();
    }

    private String challenge_를_발급받는다() {
        return client.post()
                .uri(CHALLENGE_경로)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DeviceChallenge.class)
                .returnResult()
                .getResponseBody()
                .challenge();
    }

    private Long 한숨을_등록한다(String accessToken) {
        client.post()
                .uri("/api/v1/emotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(한숨_등록_본문())
                .exchange()
                .expectStatus().isOk();

        return jdbcClient.sql("SELECT id FROM emotions")
                .query(Long.class)
                .single();
    }

    private RestTestClient.ResponseSpec 차단한다(String accessToken, String uri, Long emotionId) {
        return client.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"emotionId": %d}
                        """.formatted(emotionId))
                .exchange();
    }

    private RestTestClient.ResponseSpec 차단_목록을_조회한다(String accessToken, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
    }

    private RestTestClient.ResponseSpec 해제한다(String accessToken, String uri) {
        return client.delete()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
    }

    private Object 작성자_기기_식별자() {
        return jdbcClient.sql("SELECT device_id FROM emotions")
                .query()
                .singleRow()
                .get("device_id");
    }

    private long 차단_행_수() {
        return jdbcClient.sql("SELECT COUNT(*) FROM emotion_blocks").query(Long.class).single()
                + jdbcClient.sql("SELECT COUNT(*) FROM device_blocks").query(Long.class).single();
    }

    private Long 작성자를_모르는_한숨을_넣는다() {
        return jdbcClient.sql("""
                        INSERT INTO emotions (request_id, location, nickname, created_at, updated_at,
                                              region_code, region_classified_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(126.9780, 37.5664), 4326),
                            '외로운 회사원',
                            NOW(),
                            NOW(),
                            '11010530',
                            NOW()
                        )
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID())
                .query(Long.class)
                .single();
    }

    private RestTestClient.ResponseSpec 신고한다(String accessToken, Long emotionId) {
        return client.post()
                .uri("/api/v2/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"emotionId": %d, "deviceId": "%s", "reason": "광고성 게시물입니다"}
                        """.formatted(emotionId, 사칭하려는_기기_식별자))
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

    private ListAppender<ILoggingEvent> 개발_프로파일_로그_수집을_시작한다() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        루트_로거().addAppender(appender);
        개발_프로파일_로거().forEach(logger -> logger.setLevel(Level.DEBUG));
        return appender;
    }

    private void 개발_프로파일_로그_수집을_끝낸다(ListAppender<ILoggingEvent> appender) {
        개발_프로파일_로거().forEach(logger -> logger.setLevel(null));
        루트_로거().detachAppender(appender);
        appender.stop();
    }

    private List<Logger> 개발_프로파일_로거() {
        return List.of(
                (Logger) LoggerFactory.getLogger("com.pheeeew"),
                (Logger) LoggerFactory.getLogger("org.hibernate.SQL")
        );
    }

    private List<Logger> 인증_경로_로거() {
        return List.of(
                (Logger) LoggerFactory.getLogger("com.pheeeew"),
                (Logger) LoggerFactory.getLogger("org.springframework.security"),
                웹_계층_로거(),
                (Logger) LoggerFactory.getLogger("org.hibernate.SQL")
        );
    }

    private ListAppender<ILoggingEvent> 서버_웹_계층_로그_수집을_시작한다() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        루트_로거().addAppender(appender);
        웹_계층_로거().setLevel(Level.TRACE);
        테스트_클라이언트_로거().setLevel(Level.INFO);
        return appender;
    }

    private void 서버_웹_계층_로그_수집을_끝낸다(ListAppender<ILoggingEvent> appender) {
        테스트_클라이언트_로거().setLevel(null);
        웹_계층_로거().setLevel(null);
        루트_로거().detachAppender(appender);
        appender.stop();
    }

    private Logger 웹_계층_로거() {
        return (Logger) LoggerFactory.getLogger("org.springframework.web");
    }

    private Logger 테스트_클라이언트_로거() {
        return (Logger) LoggerFactory.getLogger("org.springframework.web.client");
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

    record DeviceChallenge(String challenge, long expiresIn) {
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

    private Set<String> 한숨_속성_이름들(String 응답_본문) {
        try {
            JsonNode properties = new ObjectMapper().readTree(응답_본문);
            Set<String> 이름들 = new HashSet<>();
            properties.fieldNames().forEachRemaining(이름들::add);
            return 이름들;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("응답 본문을 해석할 수 없습니다.", exception);
        }
    }
}
