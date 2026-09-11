package com.pheeeew.device.infra.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class JwtTokenDecoder {

    private final JwtDecoder jwtDecoder;

    public AccessTokenClaims decodeAccessToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        requireAccessTokenUse(jwt);

        return AccessTokenClaims.from(jwt);
    }

    private void requireAccessTokenUse(Jwt jwt) {
        String tokenUse = jwt.getClaimAsString(AccessTokenContract.TOKEN_USE_CLAIM);
        if (!AccessTokenContract.ACCESS_TOKEN_USE.equals(tokenUse)) {
            throw new BadJwtException("액세스 토큰으로 사용할 수 없는 토큰입니다.");
        }
    }
}
