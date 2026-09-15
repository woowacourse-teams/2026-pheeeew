package com.pheeeew.appversion.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AppVersionMetricsFilterTest {

    @Test
    void 처리되지_않은_예외가_발생하면_기본_200_응답을_성공으로_집계하지_않는다() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AppVersionMetricsFilter filter = new AppVersionMetricsFilter(registry);
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
}
