package com.pheeeew.auth.fixture;

import com.pheeeew.auth.infra.jwt.AccessTokenContract;
import com.pheeeew.auth.infra.jwt.JwtConfig;
import com.pheeeew.auth.infra.jwt.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AccessTokenFixture {

    private static final JwtConfig JWT_CONFIG = new JwtConfig();
    private static final Duration 기본_유효_시간 = Duration.ofMinutes(30);
    private static final Duration 시계_오차를_넘는_시간 = Duration.ofMinutes(10);

    private AccessTokenFixture() {
    }

    public static String 유효한_토큰(UUID devicePublicId) {
        Instant issuedAt = Instant.now();
        return 서명한다(
                JwtTestKeys.기본_키_설정(),
                devicePublicId.toString(),
                AccessTokenContract.ACCESS_TOKEN_USE,
                issuedAt,
                issuedAt.plus(기본_유효_시간)
        );
    }

    public static String 만료된_토큰(UUID devicePublicId) {
        Instant expiresAt = Instant.now().minus(시계_오차를_넘는_시간);
        return 서명한다(
                JwtTestKeys.기본_키_설정(),
                devicePublicId.toString(),
                AccessTokenContract.ACCESS_TOKEN_USE,
                expiresAt.minus(기본_유효_시간),
                expiresAt
        );
    }

    public static String 다른_키로_서명한_토큰(UUID devicePublicId) {
        Instant issuedAt = Instant.now();
        return 서명한다(
                JwtTestKeys.다른_키_설정(),
                devicePublicId.toString(),
                AccessTokenContract.ACCESS_TOKEN_USE,
                issuedAt,
                issuedAt.plus(기본_유효_시간)
        );
    }

    public static String 용도가_액세스가_아닌_토큰(UUID devicePublicId) {
        Instant issuedAt = Instant.now();
        return 서명한다(
                JwtTestKeys.기본_키_설정(),
                devicePublicId.toString(),
                "REFRESH",
                issuedAt,
                issuedAt.plus(기본_유효_시간)
        );
    }

    public static String 대상이_기기_공개_식별자가_아닌_토큰() {
        Instant issuedAt = Instant.now();
        return 서명한다(
                JwtTestKeys.기본_키_설정(),
                "1",
                AccessTokenContract.ACCESS_TOKEN_USE,
                issuedAt,
                issuedAt.plus(기본_유효_시간)
        );
    }

    public static Jwt 액세스_토큰_클레임(UUID devicePublicId) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(유효한_토큰(devicePublicId))
                .header("alg", SignatureAlgorithm.RS256.getName())
                .header("typ", AccessTokenContract.TOKEN_TYPE)
                .issuer(AccessTokenContract.ISSUER)
                .subject(devicePublicId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(기본_유효_시간))
                .claim(AccessTokenContract.TOKEN_USE_CLAIM, AccessTokenContract.ACCESS_TOKEN_USE)
                .build();
    }

    public static JwtAuthenticationToken 인증된_기기(UUID devicePublicId) {
        return new JwtAuthenticationToken(액세스_토큰_클레임(devicePublicId), List.of());
    }

    public static Jwt 용도가_액세스가_아닌_클레임(UUID devicePublicId) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", SignatureAlgorithm.RS256.getName())
                .issuer(AccessTokenContract.ISSUER)
                .subject(devicePublicId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(기본_유효_시간))
                .claim(AccessTokenContract.TOKEN_USE_CLAIM, "REFRESH")
                .build();
    }

    public static Jwt 용도가_없는_클레임(UUID devicePublicId) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", SignatureAlgorithm.RS256.getName())
                .issuer(AccessTokenContract.ISSUER)
                .subject(devicePublicId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(기본_유효_시간))
                .build();
    }

    public static Jwt 대상이_기기_공개_식별자가_아닌_클레임(String subject) {
        Instant issuedAt = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", SignatureAlgorithm.RS256.getName())
                .issuer(AccessTokenContract.ISSUER)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(기본_유효_시간))
                .claim(AccessTokenContract.TOKEN_USE_CLAIM, AccessTokenContract.ACCESS_TOKEN_USE);
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    private static String 서명한다(
            JwtProperties keys,
            String subject,
            String tokenUse,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtEncoder jwtEncoder = JWT_CONFIG.jwtEncoder(keys);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(AccessTokenContract.TOKEN_TYPE)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AccessTokenContract.ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AccessTokenContract.TOKEN_USE_CLAIM, tokenUse)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
