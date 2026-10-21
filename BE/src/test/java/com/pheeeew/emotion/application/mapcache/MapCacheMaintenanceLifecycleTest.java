package com.pheeeew.emotion.application.mapcache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class MapCacheMaintenanceLifecycleTest {

    @Test
    void 컴포넌트_종료는_실행기를_닫기_전에도_보정_예약을_취소한다() {
        // given
        var executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        var updates = new MapCacheUpdateScheduler(Clock.systemUTC(), executor);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("pheeeew.map-cache.maintenance.automatic-start", "false")));
            context.registerBean(SimpleMeterRegistry.class);
            context.registerBean(MapCacheUpdateScheduler.class, () -> updates,
                    definition -> definition.setDestroyMethodName(""));
            context.registerBean(EmotionMapCacheCoordinator.class, () -> new EmotionMapCacheCoordinator(
                    context.getBeanProvider(EmotionMapCacheListener.class),
                    context.getBeanProvider(MapCacheMaintenance.class), context.getBean(SimpleMeterRegistry.class)));
            context.register(MapCacheMaintenance.class);
            context.refresh();
            var maintenance = context.getBean(MapCacheMaintenance.class);
            maintenance.start();
            assertThat(executor.getQueue()).isNotEmpty();

            // when
            context.close();
            maintenance.requestRebuild();

            // then
            assertThat(updates.available()).isTrue();
            assertThat(executor.getQueue()).isEmpty();
        } finally {
            updates.close();
        }
    }
}
