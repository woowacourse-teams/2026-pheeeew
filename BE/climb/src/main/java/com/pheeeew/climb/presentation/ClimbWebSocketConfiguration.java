package com.pheeeew.climb.presentation;

import com.pheeeew.climb.ClimbProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 메시지 크기 제한(16KB) 컨테이너 설정은 본체가 이미 등록해서 따로 두지 않는다. */
@ConditionalOnProperty(prefix = "pheeeew.climb", name = "enabled", havingValue = "true")
@EnableWebSocket
@Configuration(proxyBeanMethods = false)
public class ClimbWebSocketConfiguration implements WebSocketConfigurer {

    private final ClimbWebSocketHandler handler;
    private final ClimbProperties properties;

    public ClimbWebSocketConfiguration(ClimbWebSocketHandler handler, ClimbProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/climb/ws").setAllowedOrigins(properties.allowedOrigin());
    }
}
