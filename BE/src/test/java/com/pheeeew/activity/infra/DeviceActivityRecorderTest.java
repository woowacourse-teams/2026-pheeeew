package com.pheeeew.activity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.activity.application.DeviceDailyActivityService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class DeviceActivityRecorderTest {

    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T14:59:59Z");
    private final DeviceDailyActivityService service = mock(DeviceDailyActivityService.class);
    private final Clock clock = mock(Clock.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ThreadPoolTaskExecutor executor = new DeviceActivityAsyncConfig().deviceActivityExecutor();
    private final DeviceActivityRecorder recorder = new DeviceActivityRecorder(service, executor, registry, clock);
    private final Logger logger = (Logger) LoggerFactory.getLogger(DeviceActivityRecorder.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        executor.initialize();
        when(clock.instant()).thenReturn(OCCURRED_AT.plusSeconds(120));
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        executor.destroy();
        logger.detachAppender(logs);
        logs.stop();
        registry.close();
    }

    @Test
    void 저장을_기다리지_않고_대기열이_가득_차도_호출_스레드에서_저장하지_않는다() throws Exception {
        // given
        Thread caller = Thread.currentThread();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(257);
        doAnswer(invocation -> {
            assertThat(Thread.currentThread()).isNotSameAs(caller);
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            completed.countDown();
            return null;
        }).when(service).save(any(UUID.class), eq(OCCURRED_AT));
        UUID rejectedDeviceId = UUID.randomUUID();
        try {
            // when
            recorder.record(DEVICE_ID, OCCURRED_AT);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            recorder.record(DEVICE_ID, OCCURRED_AT); // 저장 중인 같은 활동은 대기열을 차지하지 않는다.
            for (int index = 0; index < 256; index++) {
                recorder.record(UUID.randomUUID(), OCCURRED_AT);
            }
            assertThatCode(() -> recorder.record(rejectedDeviceId, OCCURRED_AT)).doesNotThrowAnyException();

            // then
            assertThat(failures("queue_rejected")).isOne();
            assertThat(failures("save_failed")).isZero();
            assertThat(logs.list).singleElement().satisfies(event ->
                    assertThat(event.getKeyValuePairs().toString()).contains("queue_rejected"));
        } finally {
            release.countDown();
        }
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        recorder.record(DEVICE_ID, OCCURRED_AT); // 저장 완료된 활동도 다시 제출하지 않는다.
        recorder.record(rejectedDeviceId, OCCURRED_AT); // 거절됐던 활동은 다시 제출할 수 있다.
        awaitIdle();
        verify(service, times(258)).save(any(UUID.class), eq(OCCURRED_AT));
        verify(service).save(DEVICE_ID, OCCURRED_AT);
        verify(service).save(rejectedDeviceId, OCCURRED_AT);
    }

    @Test
    void 저장_실패는_계속_집계하고_민감정보_없는_오류_로그는_분당_한번만_남긴다() throws Exception {
        // given
        RuntimeException failure = new IllegalStateException("token-secret " + DEVICE_ID,
                new IllegalArgumentException("private-memo"));
        doThrow(failure).when(service).save(DEVICE_ID, OCCURRED_AT);

        // when
        recorder.record(DEVICE_ID, OCCURRED_AT);
        awaitIdle();
        recorder.record(DEVICE_ID, OCCURRED_AT);
        awaitIdle();

        // then
        assertThat(failures("save_failed")).isEqualTo(2);
        assertThat(failures("queue_rejected")).isZero();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                    .contains("device_activity_record_failed", "save_failed", "IllegalStateException",
                            "IllegalArgumentException", "DeviceActivityRecorderTest.java")
                    .doesNotContain("token-secret", "private-memo", DEVICE_ID.toString());
        });
        when(clock.instant()).thenReturn(OCCURRED_AT.plusSeconds(180));
        recorder.record(DEVICE_ID, OCCURRED_AT);
        awaitIdle();
        assertThat(failures("save_failed")).isEqualTo(3);
        assertThat(logs.list).hasSize(2);
    }

    @Test
    void 종료된_작업자의_등록_거절도_호출자에게_전파하지_않는다() {
        // given
        executor.shutdown();

        // when / then
        assertThatCode(() -> recorder.record(DEVICE_ID, OCCURRED_AT)).doesNotThrowAnyException();
        assertThat(failures("queue_rejected")).isOne();
        verifyNoInteractions(service);
    }

    private void awaitIdle() throws Exception {
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private double failures(String reason) {
        return registry.get("pheeeew.activity.record.failures").tag("reason", reason).counter().count();
    }
}
