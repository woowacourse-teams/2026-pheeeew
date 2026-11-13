package com.pheeeew.pressrace.v2.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.application.PressRedisTodayBoard;
import com.pheeeew.pressrace.v2.application.PressRedisTodayBoardFixture;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@PostgisDataJpaTest
class PressStreamStoreUnavailableIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long 초 = TimeUnit.SECONDS.toNanos(1);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    @Autowired
    private PressBufferRepository pressBufferRepository;
    @Autowired
    private JdbcClient jdbc;

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final UUID leader = UUID.randomUUID();
    private final UUID observer = UUID.randomUUID();
    private PressSnapshotService pressSnapshotService;
    private PressStreamPublisher publisher;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stopRedis() {
        connections.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        PressRedisStore store = new PressRedisStore(redis);
        LocalDate today = LocalDate.now(KST);
        store.markSeeded(today);
        store.write(leader, today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());
        store.fillNicknames(Map.of(leader, "별"));
        PressRedisTodayBoard board = PressRedisTodayBoardFixture.보드(store, pressBufferRepository, Clock.system(KST),
                new SimpleMeterRegistry(), ticker);
        PressRedisTodayBoardFixture.틱(board);
        pressSnapshotService = new PressSnapshotService(
                new PressDatabaseSnapshotService(new PressSnapshotRepository(jdbc)), Optional.of(board), Optional.empty());
        publisher = new PressStreamPublisher(pressSnapshotService, Clock.system(KST), Duration.ofSeconds(1),
                Duration.ofSeconds(15), PressStreamSendMode.SEQUENTIAL, 1, 1, Duration.ofSeconds(5));
    }

    @Test
    void 보드_관찰이_멈춰도_3초_동안은_마지막_화면을_유지하고_넘으면_모든_구독을_닫는다() {
        // given
        RecordingListener leaderListener = 구독한다(leader);
        RecordingListener observerListener = 구독한다(observer);

        // when
        nanos.set(3 * 초 - 1);
        publisher.publish();
        int closedBeforeLimit = leaderListener.closed + observerListener.closed;
        nanos.set(3 * 초);
        publisher.publish();

        // then
        assertThat(closedBeforeLimit).isZero();
        assertThat(leaderListener.snapshots).singleElement()
                .satisfies(screen -> assertThat(screen.me().score()).isEqualTo(2));
        assertThat(observerListener.snapshots).hasSize(1);
        assertThat(leaderListener.closed).isEqualTo(1);
        assertThat(observerListener.closed).isEqualTo(1);
        assertThat(publisher.subscriptionCount(leader)).isZero();
        assertThat(publisher.subscriptionCount(observer)).isZero();
    }

    @Test
    void 보드_관찰이_3초를_넘기면_순위_조회와_SSE_첫_화면이_저장소_사용_불가다() {
        // given
        nanos.set(3 * 초);

        // when, then
        assertThatThrownBy(() -> pressSnapshotService.findSnapshot(leader, PressView.PRESS))
                .isInstanceOfSatisfying(PressRaceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE");
                    assertThat(exception.retryAfterSeconds()).isEqualTo(1);
                });
        assertThatThrownBy(() -> 구독한다(leader))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("STORE_UNAVAILABLE"));
        assertThat(publisher.subscriptionCount(leader)).isZero();
    }

    private RecordingListener 구독한다(UUID device) {
        RecordingListener listener = new RecordingListener();
        publisher.subscribe(device, PressView.PRESS, Clock.system(KST).instant().plus(Duration.ofMinutes(30)), listener);
        return listener;
    }

    private static final class RecordingListener implements PressStreamListener {

        private final List<PressScreen> snapshots = new ArrayList<>();
        private int closed;

        @Override
        public void sendSnapshot(PressScreen screen) {
            snapshots.add(screen);
        }

        @Override
        public void sendHeartbeat() {
        }

        @Override
        public void close() {
            closed++;
        }
    }
}
