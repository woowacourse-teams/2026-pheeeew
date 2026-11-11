package com.pheeeew.pressrace.v2.application;

import com.pheeeew.device.application.event.DeviceNicknameUpdatedEvent;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@ConditionalOnProperty(name = "pheeeew.press-race.write-mode", havingValue = "REDIS")
@Component
public class PressNicknameSynchronizer {

    private final PressRedisStore pressRedisStore;
    private final Counter failures;

    public PressNicknameSynchronizer(PressRedisStore pressRedisStore, MeterRegistry meterRegistry) {
        this.pressRedisStore = pressRedisStore;
        this.failures = Counter.builder("pheeeew.press.redis.nickname.failures").register(meterRegistry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void synchronize(DeviceNicknameUpdatedEvent event) {
        try {
            pressRedisStore.changeNickname(event.devicePublicId(), event.nickname());
        } catch (DataAccessException exception) {
            failures.increment();
            log.warn("press nickname sync failed device={}", event.devicePublicId(), exception);
        }
    }
}
