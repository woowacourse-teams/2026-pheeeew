package com.pheeeew.auth.infra.jwt;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class JwtTokenEncoder {

    private final JwtEncoder jwtEncoder;

    public String encodeAccessToken(String subject, Duration ttl) {
        Instant issuedAt = Instant.now();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type(AccessTokenContract.TOKEN_TYPE)
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AccessTokenContract.ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim(AccessTokenContract.TOKEN_USE_CLAIM, AccessTokenContract.ACCESS_TOKEN_USE)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
