package com.pheeeew.auth.infra.jwt;

import static com.pheeeew.auth.fixture.JwtTestKeys.기본_키_설정;
import static com.pheeeew.auth.fixture.JwtTestKeys.다른_키_설정;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.application.dto.AccessTokenResult;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenCodecTest {

    private static final UUID 기기_공개_식별자 = UUID.fromString("a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa");
    private static final long 액세스_토큰_만료_초 = 1800L;

    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(기본_키_설정());
    private final JwtDecoder jwtDecoder = jwtConfig.jwtDecoder(기본_키_설정(), new AccessTokenJwtValidator());
    private final JwtTokenEncoder jwtTokenEncoder = new JwtTokenEncoder(jwtEncoder);
    private final AccessTokenIssuer accessTokenIssuer = new AccessTokenIssuer(
            jwtTokenEncoder,
            new TokenProperties(Duration.ofMinutes(30), Duration.ofMinutes(5))
    );

    @Test
    void 발급한_액세스_토큰의_대상은_기기_공개_식별자다() {
        // given
        AccessTokenResult result = accessTokenIssuer.issue(기기_공개_식별자);

        // when
        AccessTokenClaims claims = AccessTokenClaims.from(jwtDecoder.decode(result.accessToken()));

        // then
        assertThat(claims.devicePublicId()).isEqualTo(기기_공개_식별자);
    }

    @Test
    void 액세스_토큰의_대상에_순번_기본_키가_들어가지_않는다() {
        // given
        AccessTokenResult result = accessTokenIssuer.issue(기기_공개_식별자);

        // when
        String subject = jwtDecoder.decode(result.accessToken()).getSubject();

        // then
        assertThat(subject).isEqualTo(기기_공개_식별자.toString());
        assertThatThrownBy(() -> Long.parseLong(subject)).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void 액세스_토큰의_유효_시간은_30분이다() {
        // given
        AccessTokenResult result = accessTokenIssuer.issue(기기_공개_식별자);

        // when
        Jwt jwt = jwtDecoder.decode(result.accessToken());

        // then
        assertThat(result.expiresIn()).isEqualTo(액세스_토큰_만료_초);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds())
                .isEqualTo(액세스_토큰_만료_초);
    }

    @Test
    void 액세스_토큰_페이로드에는_약속한_클레임만_들어간다() {
        // given
        AccessTokenResult result = accessTokenIssuer.issue(기기_공개_식별자);

        // when
        Jwt jwt = jwtDecoder.decode(result.accessToken());

        // then
        assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp", "use");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("https://pheeeew.com");
        assertThat(jwt.getClaimAsString("use")).isEqualTo("ACCESS");
        assertThat(jwt.getHeaders())
                .containsEntry("alg", "RS256")
                .containsEntry("typ", "JWT");
    }

    @Test
    void 페이로드를_바꾼_토큰은_디코딩할_수_없다() {
        // given
        String accessToken = accessTokenIssuer.issue(기기_공개_식별자).accessToken();
        String[] parts = accessToken.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("A") ? "B" : "A") + "." + parts[2];

        // when / then
        assertThatThrownBy(() -> jwtDecoder.decode(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_키로_서명한_토큰은_디코딩할_수_없다() {
        // given
        JwtTokenEncoder otherEncoder = new JwtTokenEncoder(jwtConfig.jwtEncoder(다른_키_설정()));
        String accessToken = otherEncoder.encodeAccessToken(기기_공개_식별자.toString(), Duration.ofMinutes(30));

        // when / then
        assertThatThrownBy(() -> jwtDecoder.decode(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_디코딩할_수_없다() {
        // given
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String accessToken = 토큰을_발급한다(issuedAt, issuedAt.plus(Duration.ofMinutes(30)), "ACCESS");

        // when / then
        assertThatThrownBy(() -> jwtDecoder.decode(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 용도가_액세스가_아닌_토큰은_액세스_토큰으로_쓸_수_없다() {
        // given
        Instant issuedAt = Instant.now();
        String refreshUseToken = 토큰을_발급한다(issuedAt, issuedAt.plus(Duration.ofMinutes(30)), "REFRESH");

        // when / then
        assertThatThrownBy(() -> jwtDecoder.decode(refreshUseToken))
                .isInstanceOf(JwtException.class);
    }

    private String 토큰을_발급한다(Instant issuedAt, Instant expiresAt, String tokenUse) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(AccessTokenContract.TOKEN_TYPE)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AccessTokenContract.ISSUER)
                .subject(기기_공개_식별자.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AccessTokenContract.TOKEN_USE_CLAIM, tokenUse)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
