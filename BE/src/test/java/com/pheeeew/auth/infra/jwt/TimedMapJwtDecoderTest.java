package com.pheeeew.auth.infra.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.emotion.infra.metrics.MapReadObservation;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TimedMapJwtDecoderTest {

    @Test
    void 계측_실패에도_원래_decoder를_한번_호출하고_반환과_예외를_보존한다() {
        // given
        var delegate = mock(JwtDecoder.class);
        var decoder = new TimedMapJwtDecoder(delegate);
        var expected = Jwt.withTokenValue("valid").header("alg", "RS256").claim("sub", "test").build();
        var rejected = new JwtException("original rejection");
        when(delegate.decode("valid")).thenReturn(expected);
        when(delegate.decode("invalid")).thenThrow(rejected);
        var registry = new SimpleMeterRegistry() {
            @Override
            protected Timer newTimer(io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig config,
                    io.micrometer.core.instrument.distribution.pause.PauseDetector detector) {
                throw new IllegalStateException("metric unavailable");
            }
        };

        // when / then
        try (var observation = MapReadObservation.open(new MockHttpServletRequest("GET", "/api/v4/emotions/map"), registry)) {
            assertThat(decoder.decode("valid")).isSameAs(expected);
            assertThatThrownBy(() -> decoder.decode("invalid")).isSameAs(rejected);
        }
        verify(delegate, times(1)).decode("valid");
        verify(delegate, times(1)).decode("invalid");
        assertThat(MapReadObservation.active()).isFalse();
    }
}
