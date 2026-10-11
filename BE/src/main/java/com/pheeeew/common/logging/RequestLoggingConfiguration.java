package com.pheeeew.common.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RequestLoggingConfiguration {

    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter(new RequestLogWriter(new PheeeewRequestLogPolicy()));
    }
}
