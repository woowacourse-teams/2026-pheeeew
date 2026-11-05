package com.pheeeew.pressrace.v2.presentation;

import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.pressrace.v2.application.PressStreamPublisher;
import com.pheeeew.pressrace.v2.application.PressStreamSubscription;
import com.pheeeew.pressrace.v2.domain.PressView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
@RequestMapping("/api/v1/press-race")
@RestController
public class PressStreamController implements PressStreamControllerApi {

    private static final String PROXY_BUFFERING_HEADER = "X-Accel-Buffering";

    private final PressStreamPublisher pressStreamPublisher;
    private final Clock clock;

    @Override
    @GetMapping("/stream")
    public ResponseEntity<SseEmitter> subscribe(
            @RequestParam(defaultValue = "PRESS") PressView view,
            @CurrentDevice UUID devicePublicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Instant expiresAt = AccessTokenClaims.from(jwt).expiresAt();
        long timeoutMillis = Math.max(1, Duration.between(clock.instant(), expiresAt).toMillis());
        SseEmitter emitter = new SseEmitter(timeoutMillis);

        PressStreamSubscription subscription = pressStreamPublisher.subscribe(devicePublicId, view, expiresAt,
                new SseEmitterPressStreamListener(emitter));
        emitter.onCompletion(subscription::cancel);
        emitter.onError(error -> subscription.cancel());
        emitter.onTimeout(() -> {
            subscription.cancel();
            emitter.complete();
        });

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(PROXY_BUFFERING_HEADER, "no")
                .body(emitter);
    }
}
