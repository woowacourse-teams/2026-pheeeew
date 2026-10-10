package com.pheeeew.common.logging;

import static com.pheeeew.common.logging.RequestTiming.Stage.PRESS_INCREASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pheeeew.common.logging.RequestTiming.Measurement;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestTimingTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 호출_수와_총시간과_최대시간을_모으고_이전_snapshot을_유지한다() {
        // given
        MockHttpServletRequest request = bindRequest();
        RequestTiming.record(PRESS_INCREASE, 10);
        RequestTiming.record(PRESS_INCREASE, 30);
        Map<RequestTiming.Stage, Measurement> snapshot = RequestTiming.snapshot(request);
        // when
        RequestTiming.record(PRESS_INCREASE, 20);
        RequestTiming.record(PRESS_INCREASE, -1);
        RequestContextHolder.resetRequestAttributes();
        // then: 현재 문맥이 해제된 필터에서도 request로 최종 값을 읽는다.
        assertThat(snapshot).containsExactlyEntriesOf(Map.of(PRESS_INCREASE, Measurement.of(2, 40, 30)));
        assertThat(RequestTiming.snapshot(request)).containsExactlyEntriesOf(Map.of(PRESS_INCREASE, Measurement.of(3, 60, 30)));
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 요청_문맥이나_수집_속성이_없으면_기록하지_않는다() {
        // given / when
        RequestTiming.record(PRESS_INCREASE, 10);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTiming.record(PRESS_INCREASE, 10);
        RequestAttributes nonServlet = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(nonServlet);
        RequestTiming.record(PRESS_INCREASE, 10);
        // then
        assertThat(RequestTiming.snapshot(request)).isEmpty();
        verifyNoInteractions(nonServlet);
    }

    @Test
    void 별도_스레드에서_겹쳐_처리하는_요청의_값이_섞이지_않는다() throws Exception {
        // given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> collect(10, ready, start));
            var second = executor.submit(() -> collect(30, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            // when
            start.countDown();
            // then
            assertThat(first.get(5, TimeUnit.SECONDS)).containsEntry(PRESS_INCREASE, Measurement.of(2, 20, 10));
            assertThat(second.get(5, TimeUnit.SECONDS)).containsEntry(PRESS_INCREASE, Measurement.of(2, 60, 30));
        }
    }

    private Map<RequestTiming.Stage, Measurement> collect(long nanos, CountDownLatch ready, CountDownLatch start) throws Exception {
        MockHttpServletRequest request = bindRequest();
        try {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            RequestTiming.record(PRESS_INCREASE, nanos);
            RequestTiming.record(PRESS_INCREASE, nanos);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        return RequestTiming.snapshot(request);
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestTiming.begin(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
