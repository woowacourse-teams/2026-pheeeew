package com.pheeeew.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.common.config.SchedulingConfig;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.scheduling.annotation.Scheduled;

class ScheduledTaskErrorHandlerTest {

    private final ScheduledTaskErrorHandler handler = new ScheduledTaskErrorHandler();
    private StructuredLogCapture output;

    @BeforeEach
    void setUp() {
        output = StructuredLogCapture.attach(ScheduledTaskErrorHandler.class);
    }

    @AfterEach
    void tearDown() {
        output.detach();
    }

    @Test
    void 예약_작업_실패는_예외_종류와_발생_위치와_메시지를_한_번_남긴다() {
        // given
        Throwable failure = new IllegalStateException("활동 집계 상태가 초기화되지 않았습니다.",
                new IllegalArgumentException("원인 설명"));

        // when
        handler.handleError(failure);

        // then
        Map<String, Object> event = output.onlyEvent();
        assertThat(event).containsEntry("event", "scheduled_task_failed")
                .containsEntry("level", "ERROR")
                .containsEntry("logger_name", "com.pheeeew.common.logging.ScheduledTaskErrorHandler")
                .containsEntry("exceptionType", "IllegalArgumentException")
                .containsEntry("errorMessages", List.of(
                        "java.lang.IllegalStateException: 활동 집계 상태가 초기화되지 않았습니다.",
                        "java.lang.IllegalArgumentException: 원인 설명"))
                .doesNotContainKey("sqlState");
        assertThat(event.get("origin").toString()).matches("ScheduledTaskErrorHandlerTest\\.\\S+:\\d+");
        assertThat(event.get("errorStack").toString())
                .contains("java.lang.IllegalStateException", "Caused by: java.lang.IllegalArgumentException");
    }

    @Test
    void 예약_작업의_DB_원인_실패는_메시지_없이_SQL_상태만_남긴다() {
        // given
        Throwable failure = new IllegalStateException("could not execute statement [row-secret]",
                new SQLException("ERROR: relation \"device_challenges\" does not exist row-secret", "42P01"));

        // when
        handler.handleError(failure);

        // then
        assertThat(output.onlyEvent()).containsEntry("sqlState", "42P01")
                .containsEntry("exceptionType", "SQLException")
                .doesNotContainKey("errorMessages");
        assertThat(output.lines().getFirst()).doesNotContain("row-secret", "device_challenges", "could not execute");
    }

    @Test
    void 실제_스케줄러에서_실패한_예약_작업은_실패마다_한_번_기록되고_다음_실행이_이어진다() {
        // given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingConfig.class)
                .withBean(FailingTask.class, () -> new FailingTask(() -> new IllegalStateException("scheduled-failure")));

        // when
        contextRunner.run(세_번째_실행을_기다린다());

        // then
        assertThat(output.lines()).hasSize(2).allSatisfy(line -> assertThat(line)
                .contains("\"event\":\"scheduled_task_failed\"", "\"exceptionType\":\"IllegalStateException\"",
                        "scheduled-failure"));
    }

    @Test
    void 로그를_만들다_실패해도_예약_작업의_다음_실행이_이어지고_건너뜀_경고만_남는다() {
        // given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingConfig.class)
                .withBean(FailingTask.class, () -> new FailingTask(MessageFailingException::new));

        // when
        contextRunner.run(세_번째_실행을_기다린다());

        // then
        assertThat(output.lines()).hasSize(2).allSatisfy(line -> assertThat(line)
                .contains("\"event\":\"scheduled_task_log_skipped\"", "\"level\":\"WARN\"",
                        "\"skippedBy\":\"java.lang.UnsupportedOperationException\"")
                .doesNotContain("scheduled_task_failed", "message-read-secret"));
    }

    private ContextConsumer<AssertableApplicationContext> 세_번째_실행을_기다린다() {
        return context -> {
            assertThat(context).hasNotFailed();
            FailingTask task = context.getBean(FailingTask.class);
            assertThat(task.thirdRunStarted.await(10, TimeUnit.SECONDS)).isTrue();
        };
    }

    static class MessageFailingException extends RuntimeException {

        MessageFailingException() {
            super("original-message");
        }

        @Override
        public String getMessage() {
            throw new UnsupportedOperationException("message-read-secret");
        }
    }

    static class FailingTask {

        private final AtomicInteger runs = new AtomicInteger();
        private final CountDownLatch thirdRunStarted = new CountDownLatch(1);
        private final Supplier<RuntimeException> failure;

        FailingTask(Supplier<RuntimeException> failure) {
            this.failure = failure;
        }

        @Scheduled(fixedDelay = 10)
        void run() {
            if (runs.incrementAndGet() > 2) {
                thirdRunStarted.countDown();
                return;
            }
            throw failure.get();
        }
    }
}
