package com.pheeeew.climb.presentation;

import com.pheeeew.climb.ClimbProperties;
import com.pheeeew.climb.domain.ClimbAdminSessions;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.WebUtils;

/** 메시지 크기 제한(16KB) 컨테이너 설정은 본체가 이미 등록해서 따로 두지 않는다. */
@ConditionalOnProperty(prefix = "pheeeew.climb", name = "enabled", havingValue = "true")
@EnableWebSocket
@Configuration(proxyBeanMethods = false)
public class ClimbWebSocketConfiguration implements WebSocketConfigurer {

    private final ClimbWebSocketHandler handler;
    private final ClimbProperties properties;
    private final ClimbAdminSessions admins;

    public ClimbWebSocketConfiguration(ClimbWebSocketHandler handler, ClimbProperties properties,
            ClimbAdminSessions admins) {
        this.handler = handler;
        this.properties = properties;
        this.admins = admins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/climb/ws").setAllowedOrigins(properties.allowedOrigin())
                .addInterceptors(new AdminMarker());
    }

    /** 유효한 관리자 쿠키가 있으면 세션 속성에 표시한다. 없어도 손님 연결로 그대로 받는다. */
    private final class AdminMarker implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servlet) {
                Cookie cookie = WebUtils.getCookie(servlet.getServletRequest(), ClimbController.ADMIN_COOKIE);
                if (cookie != null && admins.valid(cookie.getValue(), Instant.now())) {
                    attributes.put(ClimbWebSocketHandler.ADMIN, true);
                }
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
