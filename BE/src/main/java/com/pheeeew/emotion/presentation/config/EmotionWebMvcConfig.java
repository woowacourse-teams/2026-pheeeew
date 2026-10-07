package com.pheeeew.emotion.presentation.config;

import com.pheeeew.emotion.infra.ratelimit.EmotionCreateRateLimiter;
import com.pheeeew.emotion.presentation.EmotionCreateRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@ConditionalOnProperty(name = "pheeeew.rate-limit.emotion-create.enabled", havingValue = "true")
@Configuration
public class EmotionWebMvcConfig implements WebMvcConfigurer {

    private final EmotionCreateRateLimiter emotionCreateRateLimiter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new EmotionCreateRateLimitInterceptor(emotionCreateRateLimiter))
                .addPathPatterns("/api/v1/emotions", "/api/v3/emotions");
    }
}
