package com.pheeeew.auth.infra.jwt;

import com.pheeeew.emotion.infra.metrics.MapReadMetrics.Stage;
import com.pheeeew.emotion.infra.metrics.MapReadObservation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** 서명만이 아니라 원래 decoder의 클레임 검증까지 포함한다. */
final class TimedMapJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;

    TimedMapJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) {
        if (!MapReadObservation.active()) {
            return delegate.decode(token);
        }
        long started = System.nanoTime();
        boolean succeeded = false;
        try {
            Jwt jwt = delegate.decode(token);
            succeeded = true;
            return jwt;
        } finally {
            MapReadObservation.recordStage(Stage.MAP_JWT_DECODE, System.nanoTime() - started, succeeded);
        }
    }
}
