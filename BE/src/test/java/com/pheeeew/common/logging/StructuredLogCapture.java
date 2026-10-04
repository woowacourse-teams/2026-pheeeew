package com.pheeeew.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

final class StructuredLogCapture {

    private final Logger logger;
    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final LoggerContext encoderContext = new LoggerContext();
    private final StructuredLogEncoder encoder = new StructuredLogEncoder();
    private final AppenderBase<ILoggingEvent> appender;

    private StructuredLogCapture(String loggerName) {
        logger = (Logger) LoggerFactory.getLogger(loggerName);
        encoderContext.putObject(Environment.class.getName(), new MockEnvironment());
        encoder.setContext(encoderContext);
        encoder.setFormat("logstash");
        encoder.start();
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
            }
        };
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    static StructuredLogCapture attach(Class<?> loggerClass) {
        return new StructuredLogCapture(loggerClass.getName());
    }

    static StructuredLogCapture attach(String loggerName) {
        return new StructuredLogCapture(loggerName);
    }

    List<String> lines() {
        return lines;
    }

    Map<String, Object> onlyEvent() {
        if (lines.size() != 1) {
            throw new AssertionError("로그가 정확히 한 줄이어야 하지만 " + lines.size() + "줄입니다: " + lines);
        }
        return JsonParserFactory.getJsonParser().parseMap(lines.getFirst());
    }

    void detach() {
        logger.detachAppender(appender);
        appender.stop();
        encoder.stop();
        encoderContext.stop();
    }
}
