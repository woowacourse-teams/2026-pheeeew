package com.pheeeew.climb;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.climb.presentation.ClimbGuestCookieFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class StairsSecurityTest {
    @Test void stairsPostPassesRealSecurityChainWithoutCookieOrCsrfToken() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Security.class); context.refresh();
            var filter = context.getBean(FilterChainProxy.class);
            for (boolean cookie : new boolean[]{false, true}) {
                var request = new MockHttpServletRequest("POST", "/api/v1/climb/stairs/runs");
                request.setServletPath("/api/v1/climb/stairs/runs");
                request.setContentType("application/json");
                if (cookie) request.setCookies(new Cookie(ClimbGuestCookieFilter.COOKIE_NAME, "ignored"));
                var response = new MockHttpServletResponse();
                boolean[] reached = {false};
                filter.doFilter(request, response, (req, res) -> reached[0] = true);
                assertThat(reached[0]).isTrue();
                assertThat(response.getHeader("Set-Cookie")).isNull();
            }
            var me = new MockHttpServletRequest("GET", "/api/v1/climb/me");
            me.setServletPath("/api/v1/climb/me");
            var response = new MockHttpServletResponse();
            filter.doFilter(me, response, (req, res) -> { throw new AssertionError("me requires a guest"); });
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class Security {
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            var properties = new ClimbProperties(true, "https://game.example", 10, null, null, "", 0);
            var guests = new com.pheeeew.climb.application.ClimbGuestService(null, null, null, 10) {
                @Override public java.util.Optional<com.pheeeew.climb.domain.ClimbGuest> authenticate(String token) {
                    throw new AssertionError("stairs must not authenticate guest cookies");
                }
            };
            return new ClimbConfiguration().climbSecurityFilterChain(http, guests, properties);
        }
    }
}
