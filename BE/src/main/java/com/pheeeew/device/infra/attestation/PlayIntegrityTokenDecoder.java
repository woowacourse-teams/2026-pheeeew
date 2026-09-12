package com.pheeeew.device.infra.attestation;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_ATTESTATION_INVALID;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE;

import com.pheeeew.device.exception.DeviceException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class PlayIntegrityTokenDecoder {

    private static final String DECODE_URI_TEMPLATE =
            "https://playintegrity.googleapis.com/v1/%s:decodeIntegrityToken";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTEGRITY_TOKEN_FIELD = "integrity_token";
    private static final String TOKEN_PAYLOAD_FIELD = "tokenPayloadExternal";
    private static final String REQUEST_DETAILS_FIELD = "requestDetails";
    private static final String REQUEST_PACKAGE_NAME_FIELD = "requestPackageName";
    private static final String CHALLENGE_FIELD = "nonce";
    private static final String APP_INTEGRITY_FIELD = "appIntegrity";
    private static final String APP_RECOGNITION_VERDICT_FIELD = "appRecognitionVerdict";
    private static final String DEVICE_INTEGRITY_FIELD = "deviceIntegrity";
    private static final String DEVICE_RECOGNITION_VERDICT_FIELD = "deviceRecognitionVerdict";
    private static final Duration QUOTA_RETRY_AFTER = Duration.ofMinutes(1);

    private final RestClient googleApiRestClient;
    private final GoogleAccessTokenProvider googleAccessTokenProvider;
    private final PlayIntegrityProperties playIntegrityProperties;

    public PlayIntegrityPayload decode(String integrityToken) {
        Map<String, Object> response = requestDecode(integrityToken);
        Map<String, Object> payload = childOf(response, TOKEN_PAYLOAD_FIELD);
        Map<String, Object> requestDetails = childOf(payload, REQUEST_DETAILS_FIELD);
        Map<String, Object> appIntegrity = childOf(payload, APP_INTEGRITY_FIELD);
        Map<String, Object> deviceIntegrity = childOf(payload, DEVICE_INTEGRITY_FIELD);

        return PlayIntegrityPayload.of(
                textOf(requestDetails, REQUEST_PACKAGE_NAME_FIELD),
                textOf(requestDetails, CHALLENGE_FIELD),
                textOf(appIntegrity, APP_RECOGNITION_VERDICT_FIELD),
                textsOf(deviceIntegrity, DEVICE_RECOGNITION_VERDICT_FIELD)
        );
    }

    private Map<String, Object> requestDecode(String integrityToken) {
        URI decodeUri = URI.create(
                DECODE_URI_TEMPLATE.formatted(playIntegrityProperties.androidPackageName())
        );

        try {
            return googleApiRestClient.post()
                    .uri(decodeUri)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + googleAccessTokenProvider.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(INTEGRITY_TOKEN_FIELD, integrityToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toFailure(response.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException exception) {
            throw new PlayIntegrityUnavailableException("무결성 증명 토큰을 복호화하지 못했습니다.", exception);
        }
    }

    private RuntimeException toFailure(HttpStatusCode statusCode) {
        if (statusCode.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return new DeviceException(DEVICE_ATTESTATION_INVALID);
        }
        if (statusCode.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return new DeviceException(DEVICE_ATTESTATION_UNAVAILABLE, null, QUOTA_RETRY_AFTER);
        }
        return new PlayIntegrityUnavailableException(
                "무결성 증명 복호화 요청이 " + statusCode.value() + " 로 실패했습니다.",
                null
        );
    }

    private Map<String, Object> childOf(Map<String, Object> parent, String field) {
        Object child = parent == null ? null : parent.get(field);
        if (child instanceof Map<?, ?> childMap) {
            return castToStringKeyedMap(childMap);
        }
        throw new DeviceException(DEVICE_ATTESTATION_INVALID);
    }

    private String textOf(Map<String, Object> parent, String field) {
        Object value = parent.get(field);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new DeviceException(DEVICE_ATTESTATION_INVALID);
    }

    private List<String> textsOf(Map<String, Object> parent, String field) {
        Object value = parent.get(field);
        if (!(value instanceof List<?> values)) {
            throw new DeviceException(DEVICE_ATTESTATION_INVALID);
        }

        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringKeyedMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }
}
