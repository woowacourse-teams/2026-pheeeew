package com.pheeeew.activity.infra;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DeviceActivityAsyncConfig {

    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor deviceActivityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("device-activity-");

        // 활동 기록이 DB와 메모리에 주는 부담을 제한한다: 동시 실행 1개, 대기 최대 256개.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(256);
        // 작업을 받지 못하면 요청 스레드에서 대신 실행하지 않고, Recorder가 처리할 거절 예외를 던진다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        // 종료 시 이미 받은 작업의 완료를 최대 5초 기다린다. 시간이 지나도 작업을 강제 중단하지는 않는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);

        return executor;
    }
}
