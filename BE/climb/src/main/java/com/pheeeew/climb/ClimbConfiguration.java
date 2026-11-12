package com.pheeeew.climb;

import com.pheeeew.climb.application.ClimbGuestService;
import com.pheeeew.climb.application.ClimbRankingService;
import com.pheeeew.climb.domain.ClimbErrorCode;
import com.pheeeew.climb.infra.ClimbGuestRepository;
import com.pheeeew.climb.infra.ClimbRunRepository;
import com.pheeeew.climb.presentation.ClimbGuestCookieFilter;
import com.pheeeew.climb.presentation.ClimbWebSocketHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 피유 등반 모듈의 빈. pheeeew.climb.enabled 가 true 일 때만 올라온다.
 * 본체를 참조하지 않고, 본체도 이 모듈을 직접 부르지 않는다.
 */
@ConditionalOnProperty(prefix = "pheeeew.climb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ClimbProperties.class)
@Configuration(proxyBeanMethods = false)
public class ClimbConfiguration {

    static final String PATHS = "/api/v1/climb/**";

    @Bean
    ClimbGuestRepository climbGuestRepository(JdbcClient jdbc) {
        return new ClimbGuestRepository(jdbc);
    }

    @Bean
    ClimbRunRepository climbRunRepository(JdbcClient jdbc) {
        return new ClimbRunRepository(jdbc);
    }

    @Bean
    ClimbRankingService climbRankingService(ClimbProperties properties, ClimbRunRepository runs) {
        ClimbRankingService ranking = new ClimbRankingService(properties.climbMaps(), runs);
        ranking.load();
        return ranking;
    }

    @Bean
    ClimbGuestService climbGuestService(ClimbGuestRepository guests, ClimbRunRepository runs,
            ClimbRankingService ranking, ClimbProperties properties) {
        return new ClimbGuestService(guests, runs, ranking, properties.guestCreatesPerHour());
    }

    @Bean
    ClimbWebSocketHandler climbWebSocketHandler(ClimbRankingService ranking, ClimbRunRepository runs,
            ClimbGuestService guests) {
        return new ClimbWebSocketHandler(ranking, runs, guests);
    }

    /** /api/v1/climb/** 전용 체인. 본체 체인보다 먼저 보고, JWT 대신 손님 쿠키로 확인한다. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain climbSecurityFilterChain(HttpSecurity http, ClimbGuestService guests,
            ClimbProperties properties) throws Exception {
        if (properties.allowedOrigin() == null || properties.allowedOrigin().isBlank()) {
            throw new IllegalStateException("pheeeew.climb.allowed-origin 이 필요하다.");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(properties.allowedOrigin()));
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(PATHS, cors);

        http
                .securityMatcher(PATHS)
                // 쿠키는 SameSite=Lax 이고, 상태를 바꾸는 요청은 JSON 이라 사전 요청에서 게임 출처만 통과한다.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(configurer -> configurer.configurationSource(source))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ClimbGuestCookieFilter(guests), AnonymousAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, failure) ->
                                writeError(response, ClimbErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, failure) ->
                                writeError(response, ClimbErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/climb/guests").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/climb/ranking").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/climb/me", "/api/v1/climb/ws").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/climb/me").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ClimbErrorCode code) throws IOException {
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.message() + "\"}");
    }
}
