package com.pheeeew.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MessageConverterConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(
                converters -> converters.removeIf(MessageConverterConfig::supportsCbor)
        );
    }

    private static boolean supportsCbor(HttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes().contains(MediaType.APPLICATION_CBOR);
    }
}
