package com.pheeeew.appversion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AppVersionMetricsFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AppVersionMetricsFilter filter = new AppVersionMetricsFilter(new AppVersionMetrics(registry));

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void 처리되지_않은_예외가_발생하면_기본_200_응답을_성공으로_집계하지_않는다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/app/version");
        request.setParameter("platform", "android");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException exception = new ServletException("request processing failed");
        FilterChain chain = (req, res) -> {
            throw exception;
        };

        // when / then
        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(exception);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(registry.get("pheeeew.app.version.checks").tag("platform", "android").counter().count())
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({"199, 0", "200, 1", "204, 1", "299, 1", "300, 0", "400, 0", "500, 0"})
    void 컨텍스트_경로를_포함한_GET은_정상_완료한_2xx만_집계한다(int status, double expected) throws Exception {
        // given
        var request = new MockHttpServletRequest("GET", "/service/api/v2/app/version");
        request.setContextPath("/service");
        request.setParameter("platform", "iOs");
        var response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (req, res) -> response.setStatus(status));

        // then
        assertThat(registry.get("pheeeew.app.version.checks").tag("platform", "ios").counter().count())
                .isEqualTo(expected);
        assertThat(registry.get("pheeeew.app.version.checks").tag("platform", "android").counter().count())
                .isZero();
    }
}
