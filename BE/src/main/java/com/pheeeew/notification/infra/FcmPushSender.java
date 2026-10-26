package com.pheeeew.notification.infra;

import com.pheeeew.device.infra.attestation.GoogleAccessTokenProvider;
import com.pheeeew.notification.application.PushSender;
import com.pheeeew.notification.domain.PushMessage;
import com.pheeeew.notification.domain.PushSendResult;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.json.JsonParseException;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public class FcmPushSender implements PushSender {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String SEND_URI_TEMPLATE = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNREGISTERED_ERROR_CODE = "UNREGISTERED";

    private final RestClient fcmRestClient;
    private final GoogleAccessTokenProvider accessTokenProvider;
    private final FcmProperties fcmProperties;

    public FcmPushSender(RestClient fcmRestClient, FcmProperties fcmProperties) {
        this.fcmRestClient = fcmRestClient;
        this.accessTokenProvider = new GoogleAccessTokenProvider(
                fcmRestClient, fcmProperties.serviceAccountBase64(), SCOPE);
        this.fcmProperties = fcmProperties;
    }

    @Override
    public boolean isAvailable() {
        return fcmProperties.isConfigured();
    }

    @Override
    public PushSendResult send(String token, PushMessage message) {
        try {
            fcmRestClient.post()
                    .uri(URI.create(SEND_URI_TEMPLATE.formatted(fcmProperties.projectId())))
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessTokenProvider.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(token, message))
                    .retrieve()
                    .toBodilessEntity();
            return PushSendResult.SENT;
        } catch (HttpClientErrorException exception) {
            if (isUnregistered(exception)) {
                return PushSendResult.UNREGISTERED;
            }
            throw exception;
        }
    }

    private Map<String, Object> requestBody(String token, PushMessage message) {
        return Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", message.title(), "body", message.body()),
                "data", message.data()
        ));
    }

    private boolean isUnregistered(HttpClientErrorException exception) {
        if (!exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return false;
        }
        try {
            Map<String, Object> response = JsonParserFactory.getJsonParser()
                    .parseMap(exception.getResponseBodyAsString());
            return response.get("error") instanceof Map<?, ?> error
                    && error.get("details") instanceof List<?> details
                    && details.stream().anyMatch(detail -> detail instanceof Map<?, ?> fields
                            && UNREGISTERED_ERROR_CODE.equals(fields.get("errorCode")));
        } catch (JsonParseException invalidBody) {
            return false;
        }
    }
}
