package com.pheeeew.auth.presentation.config;

import com.pheeeew.auth.presentation.resolver.CurrentDeviceArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final CurrentDeviceArgumentResolver currentDeviceArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentDeviceArgumentResolver);
    }
}
