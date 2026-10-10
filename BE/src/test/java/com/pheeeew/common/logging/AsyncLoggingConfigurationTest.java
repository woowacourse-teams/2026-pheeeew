package com.pheeeew.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.boot.logging.logback.RollingPolicySystemProperty;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

@Isolated
class AsyncLoggingConfigurationTest {

    @TempDir
    java.nio.file.Path directory;
    private final LoggerContext context = new LoggerContext();
    private final Properties originalProperties = (Properties) System.getProperties().clone();
    private BlockingOutput output;
    private Logger logger;

    private MockEnvironment load(String profile) {
        return load(profile, profile.equals("prod") || profile.equals("dev"));
    }

    private MockEnvironment load(String profile, boolean file) {
        // Boot는 기존 시스템 속성을 덮지 않으므로 앞선 테스트의 로그 설정을 격리한다.
        for (LoggingSystemProperty property : LoggingSystemProperty.values()) {
            System.clearProperty(property.getEnvironmentVariableName());
        }
        for (RollingPolicySystemProperty property : RollingPolicySystemProperty.values()) {
            System.clearProperty(property.getEnvironmentVariableName());
        }
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.config.location", "classpath:application-log.yml")
                .withProperty("spring.application.name", "pheeeew")
                .withProperty("APP_RELEASE", "unknown");
        if (file) {
            environment.withProperty("logging.file.name", directory.resolve("application.json").toString())
                    .withProperty("logging.logback.rollingpolicy.file-name-pattern",
                            directory.resolve("application.%d{yyyy-MM-dd}.%i.json").toString());
        }
        if (!profile.equals("default")) {
            environment.setActiveProfiles(profile);
        }
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        context.setMDCAdapter(MDC.getMDCAdapter());
        // Boot의 실제 설정 로더만 격리 컨텍스트로 연결하고 전역 로거 설정은 건드리지 않는다.
        try (MockedStatic<LoggerFactory> factory = mockStatic(LoggerFactory.class, CALLS_REAL_METHODS)) {
            factory.when(LoggerFactory::getILoggerFactory).thenReturn(context);
            LogbackLoggingSystem system = new LogbackLoggingSystem(getClass().getClassLoader());
            system.getSystemProperties(environment).apply(LogFile.get(environment));
            system.initialize(
                    new LoggingInitializationContext(environment), "classpath:logback-spring.xml",
                    LogFile.get(environment));
        }
        logger = context.getLogger("async-contract");
        return environment;
    }

    @AfterEach
    void tearDown() {
        if (output != null) {
            output.release.countDown();
        }
        context.stop();
        MDC.clear();
        System.setProperties(originalProperties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASYNC_CONSOLE", "ASYNC_FILE"})
    void 운영_출력은_비동기이며_요청_정리_후에도_JSON_필드를_보존한다(String name) throws Exception {
        load("prod");
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(root.iteratorForAppenders()).toIterable()
                .extracting(appender -> appender.getName()).containsExactly("ASYNC_CONSOLE", "ASYNC_FILE");
        AsyncAppender async = blockOutput(name);
        logger.warn("output-blocker");
        assertThat(output.entered.await(2, TimeUnit.SECONDS)).isTrue();
        MDC.put("correlationId", "request-one");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/emotions/map/regions");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, request.getRequestURI());
        RequestTiming.begin(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTiming.record(RequestTiming.Stage.REGIONS_INTERSECTION, 20_000_000);
        Logger writer = (Logger) LoggerFactory.getLogger(RequestLogWriter.class);
        boolean additive = writer.isAdditive();
        writer.setAdditive(false);
        writer.addAppender(async);
        try {
            new RequestLogWriter().write(request, new MockHttpServletResponse(), 1200, null);
        } finally {
            writer.detachAppender(async);
            writer.setAdditive(additive);
            RequestTiming.restore(request, null);
            RequestContextHolder.resetRequestAttributes();
        }
        MDC.clear();
        output.release.countDown();
        async.stop();
        assertThat(output.json).hasSize(2);
        Map<String, Object> emitted = JsonParserFactory.getJsonParser().parseMap(List.copyOf(output.json).get(1));
        assertThat(emitted).containsEntry("correlationId", "request-one")
                .containsEntry("event", "http_request_slow").containsEntry("status", 200)
                .containsEntry("durationMs", 1200).containsEntry("timings", Map.of("regions_intersection",
                        Map.of("count", 1, "totalMs", 20.0, "maxMs", 20.0)))
                .containsEntry("application", "pheeeew").containsEntry("environment", "prod")
                .containsEntry("release", "unknown");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASYNC_CONSOLE", "ASYNC_FILE"})
    void 출력이_막히면_낮은_수준부터_버리고_포화시_오류도_버리며_종료_대기를_제한한다(String name) throws Exception {
        load("prod");
        AsyncAppender async = blockOutput(name);
        logger.warn("output-blocker");
        assertThat(output.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int index = 0; index < 206; index++) {
                logger.info("queued");
            }
            logger.info("discarded-info");
            assertThat(async.getNumberOfElementsInQueue()).isEqualTo(206);
            for (int index = 0; index < 50; index++) {
                logger.warn("queued");
            }
            logger.warn("discarded-warn");
            logger.error("discarded-error");
        });
        assertThat(async.getNumberOfElementsInQueue()).isEqualTo(256);
        assertTimeoutPreemptively(Duration.ofSeconds(2), async::stop);
        assertThat(async.isStarted()).isFalse();
        output.release.countDown();
        assertThat(output.drained.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(output.json).hasSize(257).allSatisfy(json -> assertThat(json).doesNotContain("discarded-"));
    }

    @Test
    void 개발_콘솔은_DEBUG를_보이고_파일은_DEBUG가_큐를_채우기_전에_거른다() throws Exception {
        MockEnvironment environment = load("dev");
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        AsyncAppender console = (AsyncAppender) root.getAppender("ASYNC_CONSOLE");
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        ((OutputStreamAppender<ILoggingEvent>) console.iteratorForAppenders().next()).setOutputStream(text);
        AsyncAppender file = blockOutput("ASYNC_FILE", false);
        assertThat(environment.getProperty("logging.level.com.pheeeew")).isEqualTo("DEBUG");
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logger.warn("output-blocker");
        assertThat(output.entered.await(2, TimeUnit.SECONDS)).isTrue();
        logger.debug("debug-probe");
        root.detachAppender(console);
        console.stop();
        assertThat(text.toString(StandardCharsets.UTF_8)).contains("DEBUG", "debug-probe")
                .doesNotContain("\"@timestamp\"");
        for (int index = 0; index < 1000; index++) {
            logger.debug("file-debug-excluded");
        }
        assertThat(file.getNumberOfElementsInQueue()).isZero();
        logger.info("file-info");
        logger.warn("file-warn");
        logger.error("file-error");
        assertThat(file.getNumberOfElementsInQueue()).isEqualTo(3);
        output.release.countDown();
        file.stop();
        assertThat(output.json).hasSize(4).allSatisfy(json ->
                assertThat(JsonParserFactory.getJsonParser().parseMap(json)).containsEntry("environment", "dev"));
        assertThat(output.json).allSatisfy(json -> assertThat(json).doesNotContain("debug"));
    }

    @ParameterizedTest
    @CsvSource({"prod,7,100MB", "dev,3,50MB"})
    void 운영과_개발의_JSON_파일과_회전_설정을_유지한다(String profile, int history, String cap) throws Exception {
        load(profile);
        AsyncAppender async = (AsyncAppender) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_FILE");
        RollingFileAppender<ILoggingEvent> file = (RollingFileAppender<ILoggingEvent>)
                async.iteratorForAppenders().next();
        SizeAndTimeBasedRollingPolicy<?> policy = (SizeAndTimeBasedRollingPolicy<?>) file.getRollingPolicy();
        assertThat(policy.getMaxHistory()).isEqualTo(history);
        assertThat(policy.isCleanHistoryOnStart()).isTrue();
        assertThat(policy.getFileNamePattern()).isEqualTo(
                directory.resolve("application.%d{yyyy-MM-dd}.%i.json").toString());
        assertThat(((FileSize) ReflectionTestUtils.getField(policy, "maxFileSize")).getSize())
                .isEqualTo(FileSize.valueOf("10MB").getSize());
        assertThat(((FileSize) ReflectionTestUtils.getField(policy, "totalSizeCap")).getSize())
                .isEqualTo(FileSize.valueOf(cap).getSize());
        // 출력만 막은 테스트와 별도로 실제 JSON 파일 쓰기·배출 경로도 확인한다.
        logger.warn("file-probe");
        context.stop();
        assertThat(Files.readAllLines(directory.resolve("application.json"))).singleElement().satisfies(json ->
                assertThat(JsonParserFactory.getJsonParser().parseMap(json))
                        .containsEntry("environment", profile).containsEntry("message", "file-probe"));
    }

    @ParameterizedTest
    @CsvSource({"default,false", "local,false", "test,false", "test,true"})
    void 기본과_로컬과_테스트_프로필은_기존_텍스트_출력을_유지한다(String profile, boolean file) throws Exception {
        load(profile, file);
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(root.getAppender("CONSOLE")).isInstanceOf(ch.qos.logback.core.ConsoleAppender.class);
        assertThat(root.getAppender("ASYNC_CONSOLE")).isNull();
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        ((OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE")).setOutputStream(text);
        logger.warn("fallback-probe");
        assertThat(text.toString(StandardCharsets.UTF_8)).contains("WARN", "fallback-probe")
                .doesNotContain("\"@timestamp\"");
        if (file) {
            context.stop();
            assertThat(Files.readString(directory.resolve("application.json"))).contains("fallback-probe")
                    .doesNotContain("\"@timestamp\"");
        } else {
            assertThat(root.getAppender("FILE")).isNull();
        }
    }

    private AsyncAppender blockOutput(String name) {
        return blockOutput(name, true);
    }

    private AsyncAppender blockOutput(String name, boolean exclusive) {
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        AsyncAppender async = (AsyncAppender) root.getAppender(name);
        OutputStreamAppender<ILoggingEvent> target =
                (OutputStreamAppender<ILoggingEvent>) async.iteratorForAppenders().next();
        // 실제 XML의 큐·필터·인코더는 그대로 두고 출력 끝만 지연시킨다.
        output = new BlockingOutput();
        target.setOutputStream(output);
        root.iteratorForAppenders().forEachRemaining(appender -> {
            if (exclusive && appender != async) {
                root.detachAppender(appender);
                appender.stop();
            }
        });
        return async;
    }

    private static class BlockingOutput extends OutputStream {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch drained = new CountDownLatch(1);
        private final ConcurrentLinkedQueue<String> json = new ConcurrentLinkedQueue<>();

        @Override
        public void write(int value) {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // 출력 장애를 흉내 내어 stop의 인터럽트로도 풀리지 않게 한다.
                }
            }
            json.add(new String(bytes, offset, length, StandardCharsets.UTF_8));
            if (json.size() == 257) {
                drained.countDown();
            }
        }
    }
}
