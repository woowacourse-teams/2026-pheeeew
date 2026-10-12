package com.pheeeew.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingConfigurationTest {

    @Test
    void 앱이_필터를_기존_순서로_한_번_등록하고_앱_정책으로_기록한다() {
        new ApplicationContextRunner().withUserConfiguration(RequestLoggingConfiguration.class).run(context -> {
            // given: 모듈 필터의 스캔 등록과 앱의 명시적 등록이 겹치면 안 된다.
            assertThat(AnnotatedElementUtils.hasAnnotation(RequestLoggingFilter.class, Component.class)).isFalse();
            assertThat(context.getBeansOfType(RequestLoggingFilter.class)).containsOnlyKeys("requestLoggingFilter");
            var registrations = new ServletContextInitializerBeans(context.getSourceApplicationContext().getBeanFactory())
                    .stream().filter(FilterRegistrationBean.class::isInstance)
                    .map(initializer -> (FilterRegistrationBean<?>) initializer)
                    .filter(registration -> registration.getFilter() instanceof RequestLoggingFilter).toList();
            assertThat(registrations).hasSize(1);
            var registration = registrations.getFirst();
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
            assertThat(registration.getFilter()).isSameAs(context.getBean("requestLoggingFilter"));
            var request = new MockHttpServletRequest("GET", "/groups/7");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/groups/{groupId}");
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("groupId", "7"));
            request.setQueryString("latitude=37.1234567");
            var response = new MockHttpServletResponse();
            var calls = new AtomicInteger();
            var capture = StructuredLogCapture.attach(RequestLogWriter.class);
            MDC.put("correlationId", "outer-context");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            try {
                // when
                registration.getFilter().doFilter(request, response, (req, res) -> {
                    calls.incrementAndGet();
                    RequestTiming.record(PheeeewRequestTimingStage.PRESS_INCREASE, 2_000_000);
                    response.setStatus(500);
                });

                // then
                assertThat(calls).hasValue(1);
                assertThat(capture.onlyEvent()).containsEntry("event", "http_request_failed")
                        .containsEntry("logger_name", RequestLogWriter.class.getName())
                        .containsEntry("route", "/groups/{groupId}")
                        .containsEntry("params", Map.of("groupId", "7"))
                        .containsEntry("correlationId", response.getHeader("X-Correlation-ID"))
                        .containsEntry("timings", Map.of("press_increase", Map.of("count", 1, "totalMs", 2.0, "maxMs", 2.0)));
                assertThat(capture.lines().getFirst()).doesNotContain("37.1234567");
                assertThat(request.getAttribute(RequestTiming.class.getName())).isNull();
                assertThat(MDC.get("correlationId")).isEqualTo("outer-context");
            } finally {
                capture.detach();
                RequestContextHolder.resetRequestAttributes();
                MDC.remove("correlationId");
            }
        });
    }
}
