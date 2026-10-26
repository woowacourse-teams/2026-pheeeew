package com.pheeeew.pressrace.presentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@EnableWebSocket
@Configuration
public class PressRaceWebSocketConfiguration implements WebSocketConfigurer {

    private final PressRaceWebSocketHandler handler;
    private final String[] allowedOrigins;

    public PressRaceWebSocketConfiguration(
            PressRaceWebSocketHandler handler,
            @Value("${pheeeew.press-race.allowed-origins:}") String[] allowedOrigins
    ) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/press-race").setAllowedOrigins(allowedOrigins);
    }

    @Bean
    public ServletServerContainerFactoryBean pressRaceWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(16 * 1024);
        container.setMaxBinaryMessageBufferSize(16 * 1024);
        return container;
    }
}
