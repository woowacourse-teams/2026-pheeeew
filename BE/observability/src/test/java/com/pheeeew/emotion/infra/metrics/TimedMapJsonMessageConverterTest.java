package com.pheeeew.emotion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class TimedMapJsonMessageConverterTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 지도_JSON_쓰기의_타입과_힌트를_그대로_위임하고_성공_시간을_기록한다() throws Exception {
        // given
        SmartHttpMessageConverter<Object> delegate = mock(SmartHttpMessageConverter.class);
        var meters = new SimpleMeterRegistry();
        var now = new AtomicLong();
        var converter = new TimedMapJsonMessageConverter(delegate, meters, () -> now.getAndAdd(3_000_000));
        bind("GET", "/api/v2/emotions/map");
        var body = Map.of("items", "한숨");
        var type = ResolvableType.forInstance(body);
        var hints = Map.<String, Object>of("custom", "preserved");
        var output = new MockHttpOutputMessage();

        // when
        converter.write(body, type, MediaType.APPLICATION_JSON, output, hints);

        // then
        verify(delegate).write(body, type, MediaType.APPLICATION_JSON, output, hints);
        Timer timer = meters.get("pheeeew.map.response.write").tags("route", "pins", "outcome", "success").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(3);
    }

    @Test
    void 출력_실패는_같은_예외를_전파하고_실패로_기록한다() throws Exception {
        // given
        SmartHttpMessageConverter<Object> delegate = mock(SmartHttpMessageConverter.class);
        var meters = new SimpleMeterRegistry();
        var converter = new TimedMapJsonMessageConverter(delegate, meters);
        bind("GET", "/api/v2/emotions/map/regions");
        var output = new MockHttpOutputMessage();
        var failure = new IOException("client disconnected");
        doThrow(failure).when(delegate).write("body", MediaType.APPLICATION_JSON, output);

        // when / then
        assertThatThrownBy(() -> converter.write("body", MediaType.APPLICATION_JSON, output)).isSameAs(failure);
        assertThat(meters.get("pheeeew.map.response.write").tags("route", "regions", "outcome", "error")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void 다른_경로나_메서드의_JSON_쓰기는_계측하지_않는다() throws Exception {
        // given
        SmartHttpMessageConverter<Object> delegate = mock(SmartHttpMessageConverter.class);
        var meters = new SimpleMeterRegistry();
        var converter = new TimedMapJsonMessageConverter(delegate, meters);
        var output = new MockHttpOutputMessage();

        // when
        bind("GET", "/api/v1/emotions");
        converter.write("body", MediaType.APPLICATION_JSON, output);
        bind("POST", "/api/v2/emotions/map");
        converter.write("body", MediaType.APPLICATION_JSON, output);
        RequestContextHolder.resetRequestAttributes();
        converter.write("body", MediaType.APPLICATION_JSON, output);

        // then
        assertThat(meters.getMeters().stream().filter(Timer.class::isInstance).map(Timer.class::cast))
                .allMatch(timer -> timer.count() == 0);
    }

    @Test
    void 계측_기록이_실패해도_정상_응답_쓰기는_성공한다() throws Exception {
        // given
        SmartHttpMessageConverter<Object> delegate = mock(SmartHttpMessageConverter.class);
        var meters = new SimpleMeterRegistry() {
            @Override
            protected Timer newTimer(io.micrometer.core.instrument.Meter.Id id,
                                     io.micrometer.core.instrument.distribution.DistributionStatisticConfig config,
                                     io.micrometer.core.instrument.distribution.pause.PauseDetector pauseDetector) {
                throw new IllegalStateException("metrics unavailable");
            }
        };
        bind("GET", "/api/v2/emotions/map");
        var converter = new TimedMapJsonMessageConverter(delegate, meters);
        var output = new MockHttpOutputMessage();

        // when
        converter.write("body", MediaType.APPLICATION_JSON, output);

        // then
        verify(delegate).write("body", MediaType.APPLICATION_JSON, output);
    }

    private void bind(String method, String path) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(method, path)));
    }
}
