package com.pheeeew.common.logging;

import static com.pheeeew.common.logging.PheeeewRequestTimingStage.DB_CONNECTION_ACQUIRE_FAILED;
import static com.pheeeew.common.logging.PheeeewRequestTimingStage.DB_CONNECTION_ACQUIRE_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.common.logging.RequestTiming.Measurement;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestTimingDataSourceTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 두_획득_경로의_성공과_실패_시간을_분리하고_연결과_예외를_그대로_전달한다() throws Exception {
        // given
        var request = bindRequest();
        var target = mock(DataSource.class);
        var connection = mock(Connection.class);
        var clock = new AtomicLong();
        var failure = new SQLTransientConnectionException("pool timeout");
        when(target.getConnection()).thenAnswer(invocation -> {
            clock.addAndGet(10);
            return connection;
        });
        when(target.getConnection("user", "secret")).thenAnswer(invocation -> {
            clock.addAndGet(30);
            throw failure;
        }).thenAnswer(invocation -> {
            clock.addAndGet(20);
            return connection;
        });
        var source = new RequestTimingDataSource(target, clock::get);

        // when
        assertThat(source.getConnection()).isSameAs(connection);
        assertThatThrownBy(() -> source.getConnection("user", "secret")).isSameAs(failure);
        assertThat(source.getConnection("user", "secret")).isSameAs(connection);

        // then: 성공과 실패는 겹치지 않으며, 획득 단계가 연결을 사용하거나 닫지 않는다.
        assertThat(RequestTiming.snapshot(request)).containsExactlyInAnyOrderEntriesOf(Map.of(
                DB_CONNECTION_ACQUIRE_SUCCESS, Measurement.of(2, 30, 20),
                DB_CONNECTION_ACQUIRE_FAILED, Measurement.of(1, 30, 30)));
        verifyNoInteractions(connection);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 획득_예외를_재시도하지_않고_실패한_호출_시간을_기록한다(boolean runtimeFailure) throws Exception {
        // given
        var request = bindRequest();
        var target = mock(DataSource.class);
        var clock = new AtomicLong();
        Exception failure = runtimeFailure ? new IllegalStateException("closed pool")
                : new SQLTransientConnectionException("pool timeout");
        when(target.getConnection()).thenAnswer(invocation -> {
            clock.addAndGet(30_000_000_000L);
            throw failure;
        });
        var source = new RequestTimingDataSource(target, clock::get);

        // when / then
        assertThatThrownBy(source::getConnection).isSameAs(failure);
        assertThat(RequestTiming.snapshot(request)).containsExactlyEntriesOf(Map.of(
                DB_CONNECTION_ACQUIRE_FAILED, Measurement.of(1, 30_000_000_000L, 30_000_000_000L)));
        verify(target).getConnection();
        verifyNoMoreInteractions(target);
    }

    @ParameterizedTest
    @CsvSource({"START,false", "START,true", "END,false", "END,true", "RECORD,false", "RECORD,true"})
    void 계측이_실패해도_원래_결과와_예외를_보존한다(String timingFailure, boolean acquisitionFailure) throws Exception {
        // given
        var request = mock(MockHttpServletRequest.class);
        if (timingFailure.equals("RECORD")) {
            when(request.getAttribute(RequestTiming.class.getName())).thenThrow(new IllegalStateException("record failure"));
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var target = mock(DataSource.class);
        var connection = mock(Connection.class);
        var failure = new SQLTransientConnectionException("original timeout");
        when(target.getConnection()).thenAnswer(invocation -> {
            if (acquisitionFailure) {
                throw failure;
            }
            return connection;
        });
        var calls = new AtomicLong();
        LongSupplier clock = () -> {
            long call = calls.incrementAndGet();
            if ((timingFailure.equals("START") && call == 1) || (timingFailure.equals("END") && call == 2)) {
                throw new IllegalStateException("clock failure");
            }
            return call;
        };
        var source = new RequestTimingDataSource(target, clock);

        // when / then
        if (acquisitionFailure) {
            assertThatThrownBy(source::getConnection).isSameAs(failure);
        } else {
            assertThat(source.getConnection()).isSameAs(connection);
        }
        verify(target).getConnection();
        verifyNoMoreInteractions(target);
        verifyNoInteractions(connection);
    }

    @Test
    void 요청_문맥이_없어도_연결을_그대로_전달한다() throws Exception {
        // given
        var target = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(target.getConnection()).thenReturn(connection);
        var source = new RequestTimingDataSource(target);

        // when / then
        assertThat(source.getConnection()).isSameAs(connection);
        verify(target).getConnection();
        verifyNoMoreInteractions(target);
        verifyNoInteractions(connection);
    }

    private MockHttpServletRequest bindRequest() {
        var request = new MockHttpServletRequest();
        RequestTiming.begin(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
