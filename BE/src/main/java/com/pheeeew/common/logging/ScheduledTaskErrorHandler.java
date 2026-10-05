package com.pheeeew.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.util.ErrorHandler;

@Slf4j
public class ScheduledTaskErrorHandler implements ErrorHandler {

    private static final String EVENT_SCHEDULED_TASK_FAILED = "scheduled_task_failed";
    private static final String EVENT_SCHEDULED_TASK_LOG_SKIPPED = "scheduled_task_log_skipped";

    private final ExceptionLogFormatter exceptionLogFormatter = new ExceptionLogFormatter();

    @Override
    public void handleError(Throwable failure) {
        try {
            LoggingEventBuilder event = log.atError().addKeyValue("event", EVENT_SCHEDULED_TASK_FAILED);
            exceptionLogFormatter.addFailureFields(event, failure);
            event.log("예약 작업 실행에 실패했습니다");
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", EVENT_SCHEDULED_TASK_LOG_SKIPPED)
                    .addKeyValue("skippedBy", exception.getClass().getName())
                    .log("예약 작업 실패 로그를 남기지 못했습니다");
        }
    }
}
