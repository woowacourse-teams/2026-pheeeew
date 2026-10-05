package com.pheeeew.common.config;

import com.pheeeew.common.logging.ScheduledTaskErrorHandler;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskSchedulerCustomizer scheduledTaskErrorHandlerCustomizer() {
        return taskScheduler -> taskScheduler.setErrorHandler(new ScheduledTaskErrorHandler());
    }
}
