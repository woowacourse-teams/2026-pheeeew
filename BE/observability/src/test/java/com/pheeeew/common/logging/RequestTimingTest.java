package com.pheeeew.common.logging;

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
        RequestTiming.record(TestStage.OPERATION, 10);
        RequestTiming.record(TestStage.OPERATION, 30);
        Map<RequestTimingStage, Measurement> snapshot = RequestTiming.snapshot(request);
        // when
        RequestTiming.record(TestStage.OPERATION, 20);
        RequestTiming.record(TestStage.OPERATION, -1);
        RequestContextHolder.resetRequestAttributes();
        // then: 현재 문맥이 해제된 필터에서도 request로 최종 값을 읽는다.
        assertThat(snapshot).containsExactlyEntriesOf(Map.of(TestStage.OPERATION, Measurement.of(2, 40, 30)));
        assertThat(RequestTiming.snapshot(request)).containsExactlyEntriesOf(Map.of(TestStage.OPERATION, Measurement.of(3, 60, 30)));
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 다른_enum의_동명_단계는_독립적으로_누적하고_이전_snapshot을_유지한다() {
        // given
        MockHttpServletRequest request = bindRequest();
        RequestTiming.record(TestStage.OPERATION, 10);
        RequestTiming.record(OtherStage.OPERATION, 20);
        RequestTiming.record(OtherStage.OPERATION, 30);
        Map<RequestTimingStage, Measurement> snapshot = RequestTiming.snapshot(request);

        // when
        RequestTiming.record(OtherStage.OPERATION, 40);

        // then
        assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(Map.of(
                TestStage.OPERATION, Measurement.of(1, 10, 10),
                OtherStage.OPERATION, Measurement.of(2, 50, 30)));
        assertThat(RequestTiming.snapshot(request)).containsExactlyInAnyOrderEntriesOf(Map.of(
                TestStage.OPERATION, Measurement.of(1, 10, 10),
                OtherStage.OPERATION, Measurement.of(3, 90, 40)));
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 수집_문맥이_있을_때만_null_단계를_즉시_거부하고_snapshot을_보존한다() {
        // given
        RequestTiming.record((OtherStage) null, 10);
        MockHttpServletRequest request = bindRequest();
        RequestTiming.record(OtherStage.OPERATION, 20);

        // when / then
        assertThatThrownBy(() -> RequestTiming.record((OtherStage) null, 10))
                .isInstanceOf(NullPointerException.class);
        assertThat(RequestTiming.snapshot(request)).containsExactlyEntriesOf(
                Map.of(OtherStage.OPERATION, Measurement.of(1, 20, 20)));
    }

    @Test
    void 요청_문맥이나_수집_속성이_없으면_기록하지_않는다() {
        // given / when
        RequestTiming.record(TestStage.OPERATION, 10);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTiming.record(TestStage.OPERATION, 10);
        RequestAttributes nonServlet = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(nonServlet);
        RequestTiming.record(TestStage.OPERATION, 10);
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
            assertThat(first.get(5, TimeUnit.SECONDS))
                    .containsEntry(TestStage.OPERATION, Measurement.of(2, 20, 10))
                    .containsEntry(OtherStage.OPERATION, Measurement.of(2, 20, 10));
            assertThat(second.get(5, TimeUnit.SECONDS))
                    .containsEntry(TestStage.OPERATION, Measurement.of(2, 60, 30))
                    .containsEntry(OtherStage.OPERATION, Measurement.of(2, 60, 30));
        }
    }

    private Map<RequestTimingStage, Measurement> collect(long nanos, CountDownLatch ready, CountDownLatch start) throws Exception {
        MockHttpServletRequest request = bindRequest();
        try {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            RequestTiming.record(TestStage.OPERATION, nanos);
            RequestTiming.record(TestStage.OPERATION, nanos);
            RequestTiming.record(OtherStage.OPERATION, nanos);
            RequestTiming.record(OtherStage.OPERATION, nanos);
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

    private enum TestStage implements RequestTimingStage {
        OPERATION
    }

    private enum OtherStage implements RequestTimingStage {
        OPERATION
    }
}
