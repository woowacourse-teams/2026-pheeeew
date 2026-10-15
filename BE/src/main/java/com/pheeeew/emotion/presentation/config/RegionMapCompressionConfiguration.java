package com.pheeeew.emotion.presentation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Configuration(proxyBeanMethods = false)
public class RegionMapCompressionConfiguration {

    private static final String REGION_MAP_PATH = "/api/v2/emotions/map/regions";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> regionMapResponseBuffer() {
        var filter = new OncePerRequestFilter() {

            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !"GET".equals(request.getMethod());
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                // 동기식 JSON 응답의 실제 바이트 길이를 확정한 뒤 Tomcat의 최소 압축 크기를 적용한다.
                var buffered = new ContentCachingResponseWrapper(response);
                chain.doFilter(request, buffered);
                buffered.copyBodyToResponse();
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setName("regionMapResponseBuffer");
        registration.addUrlPatterns(REGION_MAP_PATH);
        registration.setAsyncSupported(false);
        return registration;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> regionMapCompressionCustomizer() {
        return factory -> factory.setProtocol(RegionMapProtocol.class.getName());
    }

    // 압축 협상과 출력은 Tomcat에 맡기고 다른 JSON API로 적용 범위가 넓어지는 것만 막는다.
    public static class RegionMapProtocol extends Http11NioProtocol {

        @Override
        public boolean useCompression(Request request, Response response) {
            return "GET".equals(request.getMethod())
                    && request.requestURI().equals(REGION_MAP_PATH)
                    && response.getStatus() == 200
                    && super.useCompression(request, response);
        }
    }
}
