package com.pheeeew.auth.infra.jwt;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenJwtValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (hasAccessTokenUse(jwt) && hasDevicePublicIdSubject(jwt)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(BearerTokenErrorCodes.INVALID_TOKEN));
    }

    private boolean hasAccessTokenUse(Jwt jwt) {
        String tokenUse = jwt.getClaimAsString(AccessTokenContract.TOKEN_USE_CLAIM);
        return AccessTokenContract.ACCESS_TOKEN_USE.equals(tokenUse);
    }

    private boolean hasDevicePublicIdSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return false;
        }
        try {
            UUID.fromString(subject);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
