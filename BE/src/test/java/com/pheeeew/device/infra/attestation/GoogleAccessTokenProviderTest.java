package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.fixture.PlayIntegrityFixture.서비스_계정_공개키;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.서비스_계정_이메일;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.자격증명이_있는_설정;
import static com.pheeeew.device.fixture.PlayIntegrityFixture.토큰_응답;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.pheeeew.device.fixture.FakeGoogleApiServer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class GoogleAccessTokenProviderTest {

    private static final int 동시_요청_수 = 8;

    private FakeGoogleApiServer 가짜_구글;
    private GoogleAccessTokenProvider provider;

    @BeforeEach
    void setUp() {
        가짜_구글 = FakeGoogleApiServer.시작한다();
        provider = new GoogleAccessTokenProvider(
                RestClient.builder().build(),
                자격증명이_있는_설정(가짜_구글.토큰_엔드포인트())
        );
    }

    @AfterEach
    void tearDown() {
        가짜_구글.close();
    }

    @Test
    void 서비스_계정_자격증명으로_액세스_토큰을_받아온다() {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 3599));

        // when
        String accessToken = provider.accessToken();

        // then
        assertThat(accessToken).isEqualTo("ya29.first");
        assertThat(가짜_구글.토큰_요청_수()).isOne();
    }

    @Test
    void 토큰_요청은_jwt_bearer_그랜트와_RS256_으로_서명한_assertion_을_보낸다() throws Exception {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 3599));

        // when
        provider.accessToken();

        // then
        Map<String, String> form = 폼을_해석한다(가짜_구글.받은_요청들().getFirst().body());
        assertThat(form.get("grant_type")).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");

        SignedJWT assertion = SignedJWT.parse(form.get("assertion"));
        assertThat(assertion.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(assertion.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
        assertThat(assertion.verify(new RSASSAVerifier(서비스_계정_공개키()))).isTrue();
        assertThat(assertion.getJWTClaimsSet().getIssuer()).isEqualTo(서비스_계정_이메일);
        assertThat(assertion.getJWTClaimsSet().getAudience()).containsExactly(가짜_구글.토큰_엔드포인트());
        assertThat(assertion.getJWTClaimsSet().getStringClaim("scope"))
                .isEqualTo("https://www.googleapis.com/auth/playintegrity");
        assertThat(assertion.getJWTClaimsSet().getExpirationTime())
                .isAfter(assertion.getJWTClaimsSet().getIssueTime());
    }

    @Test
    void 토큰_요청_assertion_에는_서비스_계정을_가리키는_sub_클레임을_넣지_않는다() throws Exception {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 3599));

        // when
        provider.accessToken();

        // then
        SignedJWT assertion = SignedJWT.parse(폼을_해석한다(가짜_구글.받은_요청들().getFirst().body()).get("assertion"));
        assertThat(assertion.getJWTClaimsSet().getSubject()).isNull();
        assertThat(assertion.getJWTClaimsSet().getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "aud", "scope", "iat", "exp");
    }

    @Test
    void 만료되기_전_재호출은_캐시한_토큰을_돌려주고_구글을_다시_부르지_않는다() {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 3599));
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.second", 3599));

        // when
        List<String> 토큰들 = List.of(provider.accessToken(), provider.accessToken(), provider.accessToken());

        // then
        assertThat(토큰들).containsExactly("ya29.first", "ya29.first", "ya29.first");
        assertThat(가짜_구글.토큰_요청_수()).isOne();
    }

    @Test
    void 만료_여유_시간을_지나면_토큰을_다시_받아온다() throws Exception {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 61));
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.second", 3599));
        String 처음_토큰 = provider.accessToken();

        // when
        Thread.sleep(Duration.ofMillis(1300));
        String 갱신된_토큰 = provider.accessToken();

        // then
        assertThat(처음_토큰).isEqualTo("ya29.first");
        assertThat(갱신된_토큰).isEqualTo("ya29.second");
        assertThat(가짜_구글.토큰_요청_수()).isEqualTo(2);
    }

    @Test
    void 만료_여유_시간보다_짧은_유효_기간을_받으면_캐시하지_않고_매번_새로_받아온다() {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 30));
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.second", 30));

        // when
        List<String> 토큰들 = List.of(provider.accessToken(), provider.accessToken());

        // then
        assertThat(토큰들).containsExactly("ya29.first", "ya29.second");
        assertThat(가짜_구글.토큰_요청_수()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"access_token\":\"ya29.first\",\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"ya29.first\",\"expires_in\":\"3599\",\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"ya29.first\",\"expires_in\":0,\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"ya29.first\",\"expires_in\":-1,\"token_type\":\"Bearer\"}"
    })
    void 유효_기간을_읽을_수_없으면_대체_유효_기간으로_캐시한다(String 토큰_응답_본문) {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답_본문);
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.second", 3599));

        // when
        List<String> 토큰들 = List.of(provider.accessToken(), provider.accessToken());

        // then
        assertThat(토큰들).containsExactly("ya29.first", "ya29.first");
        assertThat(가짜_구글.토큰_요청_수()).isOne();
    }

    @Test
    void 동시에_들어온_요청들은_토큰을_한_번만_받아온다() throws Exception {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.only", 3599));
        CountDownLatch ready = new CountDownLatch(동시_요청_수);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<String> 토큰들 = new ArrayList<>();
        try (ExecutorService executorService = Executors.newFixedThreadPool(동시_요청_수)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 동시_요청_수; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return provider.accessToken();
                }));
            }
            boolean 모두_준비됨 = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(모두_준비됨).isTrue();
            for (Future<String> future : futures) {
                토큰들.add(future.get(10, TimeUnit.SECONDS));
            }
        }

        // then
        assertThat(토큰들).hasSize(동시_요청_수).containsOnly("ya29.only");
        assertThat(가짜_구글.토큰_요청_수()).isOne();
    }

    @Test
    void 구글이_4xx_를_주면_우리_쪽_장애로_올리고_깨진_토큰을_캐시에_남기지_않는다() {
        // given
        가짜_구글.토큰_응답을_넣는다(400, "{\"error\":\"invalid_grant\"}");
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.recovered", 3599));

        // when
        Throwable throwable = catchThrowable(() -> provider.accessToken());

        // then
        assertThat(throwable).isInstanceOf(PlayIntegrityUnavailableException.class);
        assertThat(provider.accessToken()).isEqualTo("ya29.recovered");
    }

    @Test
    void 구글이_5xx_를_주면_우리_쪽_장애로_올리고_다음_호출에_다시_시도한다() {
        // given
        가짜_구글.토큰_응답을_넣는다(503, "{\"error\":\"unavailable\"}");
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.recovered", 3599));

        // when
        Throwable throwable = catchThrowable(() -> provider.accessToken());

        // then
        assertThat(throwable).isInstanceOf(PlayIntegrityUnavailableException.class);
        assertThat(provider.accessToken()).isEqualTo("ya29.recovered");
        assertThat(가짜_구글.토큰_요청_수()).isEqualTo(2);
    }

    @Test
    void 액세스_토큰이_없는_응답은_캐시를_오염시키지_않는다() {
        // given
        가짜_구글.토큰_응답을_넣는다(200, "{\"token_type\":\"Bearer\",\"expires_in\":3599}");
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.recovered", 3599));

        // when
        Throwable throwable = catchThrowable(() -> provider.accessToken());

        // then
        assertThat(throwable).isInstanceOf(PlayIntegrityUnavailableException.class);
        assertThat(provider.accessToken()).isEqualTo("ya29.recovered");
    }

    @Test
    void 캐시가_만료된_뒤_갱신이_실패하면_옛_토큰을_돌려주지_않는다() throws Exception {
        // given
        가짜_구글.토큰_응답을_넣는다(200, 토큰_응답("ya29.first", 61));
        가짜_구글.토큰_응답을_넣는다(503, "{\"error\":\"unavailable\"}");
        String 처음_토큰 = provider.accessToken();

        // when
        Thread.sleep(Duration.ofMillis(1300));
        Throwable throwable = catchThrowable(() -> provider.accessToken());

        // then
        assertThat(처음_토큰).isEqualTo("ya29.first");
        assertThat(throwable).isInstanceOf(PlayIntegrityUnavailableException.class);
    }

    @Test
    void 실패_예외_메시지에_서비스_계정_개인키와_assertion_이_들어가지_않는다() {
        // given
        가짜_구글.토큰_응답을_넣는다(400, "{\"error\":\"invalid_grant\"}");

        // when
        Throwable throwable = catchThrowable(() -> provider.accessToken());

        // then
        String 예외_사슬 = 예외_사슬을_모은다(throwable);
        assertThat(예외_사슬).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(예외_사슬).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
        assertThat(throwable).hasMessage("Play Integrity 접근 토큰을 발급받지 못했습니다.");
    }

    private Map<String, String> 폼을_해석한다(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            );
        }
        return form;
    }

    private String 예외_사슬을_모은다(Throwable throwable) {
        StringBuilder collected = new StringBuilder();
        Set<Throwable> 방문한_예외 = new HashSet<>();
        Throwable current = throwable;
        while (current != null && 방문한_예외.add(current)) {
            collected.append(current.getMessage()).append('\n');
            collected.append(Arrays.toString(current.getStackTrace())).append('\n');
            current = current.getCause();
        }
        return collected.toString();
    }
}
