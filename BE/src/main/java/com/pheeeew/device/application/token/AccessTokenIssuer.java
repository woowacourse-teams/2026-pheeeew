package com.pheeeew.device.application.token;

import com.pheeeew.auth.infra.jwt.JwtTokenEncoder;
import com.pheeeew.auth.infra.jwt.TokenProperties;
import com.pheeeew.device.application.dto.AccessTokenResult;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AccessTokenIssuer {

    private final JwtTokenEncoder jwtTokenEncoder;
    private final TokenProperties tokenProperties;

    public AccessTokenResult issue(UUID devicePublicId) {
        Duration accessTtl = tokenProperties.accessTtl();
        String accessToken = jwtTokenEncoder.encodeAccessToken(devicePublicId.toString(), accessTtl);

        return AccessTokenResult.of(accessToken, accessTtl.toSeconds());
    }
}
