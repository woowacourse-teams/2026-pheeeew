package com.pheeeew.pressrace.v2.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressView;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 기기와 무관한 오늘의 공통 순위를 유효시간 동안 JVM 에 보관하고, 내 화면은 요청마다 계산한다.
 * 같은 날짜를 동시에 적재하면 한 요청만 DB 를 조회하고 나머지는 그 결과를 기다린다.
 * 적중하면 DB 연결을 잡지 않도록 이 클래스에는 트랜잭션을 두지 않는다.
 */
@Service
public class PressCachedSnapshotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PressSnapshotService pressSnapshotService;
    private final Clock clock;
    private final Cache<LocalDate, PressSnapshot> snapshots;

    @Autowired
    public PressCachedSnapshotService(PressSnapshotService pressSnapshotService, Clock clock, MeterRegistry meters,
            @Value("${pheeeew.press-race.snapshot-cache-ttl-millis:1000}") long ttlMillis) {
        this(pressSnapshotService, clock, meters, Duration.ofMillis(ttlMillis), Ticker.systemTicker());
    }

    PressCachedSnapshotService(PressSnapshotService pressSnapshotService, Clock clock, MeterRegistry meters,
            Duration ttl, Ticker ticker) {
        this.pressSnapshotService = pressSnapshotService;
        this.clock = clock;
        this.snapshots = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1)
                .ticker(ticker)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meters, snapshots, "press_race_snapshots");
    }

    public PressScreen findSnapshot(UUID devicePublicId, PressView view) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        PressSnapshot snapshot = snapshots.get(today, pressSnapshotService::findSnapshotOn);

        return snapshot.findScreen(devicePublicId, view);
    }
}
