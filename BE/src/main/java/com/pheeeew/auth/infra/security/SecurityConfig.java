package com.pheeeew.auth.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationErrorHandler authenticationErrorHandler
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationErrorHandler)
                        .accessDeniedHandler(authenticationErrorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v2/devices").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/devices/tokens").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/devices/challenge").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sighs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v2/sighs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v2/sighs/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sighs").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/sighs").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v2/reports").authenticated()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationErrorHandler)
                        .accessDeniedHandler(authenticationErrorHandler)
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }
}
