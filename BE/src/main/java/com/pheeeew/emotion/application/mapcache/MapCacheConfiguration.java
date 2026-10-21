package com.pheeeew.emotion.application.mapcache;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration(proxyBeanMethods = false)
public class MapCacheConfiguration {

    private ScheduledExecutorService newExecutor() {
        var executor = new ScheduledThreadPoolExecutor(1, work -> {
            Thread thread = new Thread(work, "pheeeew-map-cache-updates");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean
    MapCacheUpdateScheduler mapCacheUpdateScheduler(Clock clock,
            @Qualifier("mapCacheExecutor")
            ObjectProvider<ScheduledExecutorService> executors) {
        return new MapCacheUpdateScheduler(clock, executors.getIfAvailable(this::newExecutor));
    }

}
