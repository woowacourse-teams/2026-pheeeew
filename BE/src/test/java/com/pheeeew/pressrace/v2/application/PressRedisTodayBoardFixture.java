package com.pheeeew.pressrace.v2.application;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.Executor;

/** 스케줄을 띄우지 않은 보드를 만들고 틱을 직접 돌린다. 다른 패키지의 테스트가 package-private 생성자와 갱신에 닿는 통로다. */
public final class PressRedisTodayBoardFixture {

    private PressRedisTodayBoardFixture() {
    }

    public static PressRedisTodayBoard 보드(
            PressRedisStore store,
            PressBufferRepository repository,
            Clock clock,
            MeterRegistry meterRegistry,
            Ticker ticker
    ) {
        return 보드(store, repository, clock, meterRegistry, ticker, Runnable::run);
    }

    public static PressRedisTodayBoard 보드(
            PressRedisStore store,
            PressBufferRepository repository,
            Clock clock,
            MeterRegistry meterRegistry,
            Ticker ticker,
            Executor nicknameFiller
    ) {
        return new PressRedisTodayBoard(store, new PressRedisSeeder(repository, store, clock), repository, clock,
                meterRegistry, ticker, nicknameFiller);
    }

    public static void 틱(PressRedisTodayBoard board) {
        board.refresh();
    }
}
