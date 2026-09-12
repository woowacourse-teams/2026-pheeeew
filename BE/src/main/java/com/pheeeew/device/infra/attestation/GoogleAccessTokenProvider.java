package com.pheeeew.device.infra.attestation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class GoogleAccessTokenProvider {

    private static final String SCOPE = "https://www.googleapis.com/auth/playintegrity";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final String GRANT_TYPE_PARAMETER = "grant_type";
    private static final String ASSERTION_PARAMETER = "assertion";
    private static final String SCOPE_CLAIM = "scope";
    private static final String ACCESS_TOKEN_FIELD = "access_token";
    private static final String EXPIRES_IN_FIELD = "expires_in";
    private static final Duration ASSERTION_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);
    private static final Duration FALLBACK_TOKEN_TTL = Duration.ofMinutes(5);

    private final RestClient googleApiRestClient;
    private final PlayIntegrityProperties playIntegrityProperties;

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

    public synchronized String accessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(cachedAccessTokenExpiresAt)) {
            return cachedAccessToken;
        }

        GoogleServiceAccountKey serviceAccountKey =
                GoogleServiceAccountKey.from(playIntegrityProperties.serviceAccountBase64());
        Map<String, Object> tokenResponse = exchange(serviceAccountKey, now);

        cachedAccessToken = requireAccessToken(tokenResponse);
        cachedAccessTokenExpiresAt = now.plus(tokenTtl(tokenResponse)).minus(REFRESH_MARGIN);

        return cachedAccessToken;
    }

    private Map<String, Object> exchange(GoogleServiceAccountKey serviceAccountKey, Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(GRANT_TYPE_PARAMETER, GRANT_TYPE);
        form.add(ASSERTION_PARAMETER, signAssertion(serviceAccountKey, now));

        try {
            return googleApiRestClient.post()
                    .uri(URI.create(serviceAccountKey.tokenUri()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException exception) {
            throw new PlayIntegrityUnavailableException("Play Integrity 접근 토큰을 발급받지 못했습니다.", exception);
        }
    }

    private String signAssertion(GoogleServiceAccountKey serviceAccountKey, Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(serviceAccountKey.clientEmail())
                .audience(serviceAccountKey.tokenUri())
                .claim(SCOPE_CLAIM, SCOPE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ASSERTION_TTL)))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT assertion = new SignedJWT(header, claims);

        try {
            assertion.sign(new RSASSASigner(serviceAccountKey.privateKey()));
        } catch (JOSEException exception) {
            throw new PlayIntegrityUnavailableException("Play Integrity 접근 토큰 요청에 서명하지 못했습니다.", exception);
        }

        return assertion.serialize();
    }

    private String requireAccessToken(Map<String, Object> tokenResponse) {
        Object accessToken = tokenResponse == null ? null : tokenResponse.get(ACCESS_TOKEN_FIELD);
        if (!(accessToken instanceof String text) || text.isBlank()) {
            throw new PlayIntegrityUnavailableException("Play Integrity 접근 토큰 응답을 해석할 수 없습니다.", null);
        }
        return text;
    }

    private Duration tokenTtl(Map<String, Object> tokenResponse) {
        Object expiresIn = tokenResponse.get(EXPIRES_IN_FIELD);
        if (expiresIn instanceof Number seconds && seconds.longValue() > 0L) {
            return Duration.ofSeconds(seconds.longValue());
        }
        return FALLBACK_TOKEN_TTL;
    }
}
