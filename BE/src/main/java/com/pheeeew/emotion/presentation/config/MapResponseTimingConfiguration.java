package com.pheeeew.emotion.presentation.config;

import com.pheeeew.emotion.infra.metrics.TimedMapJsonMessageConverter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MapResponseTimingConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<MeterRegistry> meters;

    public MapResponseTimingConfiguration(ObjectProvider<MeterRegistry> meters) {
        this.meters = meters;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> {
            MeterRegistry registry = meters.getIfAvailable();
            if (registry == null) {
                return;
            }

            converters.replaceAll(converter -> converter.getClass() == JacksonJsonHttpMessageConverter.class
                    ? new TimedMapJsonMessageConverter((JacksonJsonHttpMessageConverter) converter, registry)
                    : converter);
        });
    }
}
