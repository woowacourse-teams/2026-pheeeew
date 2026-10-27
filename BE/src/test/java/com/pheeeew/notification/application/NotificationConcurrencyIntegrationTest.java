package com.pheeeew.notification.application;

import static com.pheeeew.collection.fixture.DeviceDailyStampFixture.일별_스탬프를_저장한다;
import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.DeviceDailyPressFixture.개인_프레스를_저장한다;
import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIMABLE;
import static com.pheeeew.notification.domain.NotificationOutboxType.COLLECTION_CLAIM_REMINDER;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.기기의_작업들;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.날짜의_회차들;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.알림_데이터를_지운다;
import static com.pheeeew.notification.fixture.NotificationOutboxFixture.회차의_스냅샷;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.notification.domain.NotificationOutboxType;
import com.pheeeew.notification.domain.PushSendResult;
import com.pheeeew.notification.domain.repository.DailyPressCountRepository;
import com.pheeeew.notification.domain.repository.DeviceNotificationSettingRepository;
import com.pheeeew.notification.domain.repository.DevicePushTokenRepository;
import com.pheeeew.notification.domain.repository.NotificationOutboxRepository;
import com.pheeeew.notification.domain.repository.NotificationRankRoundRepository;
import com.pheeeew.notification.fixture.NotificationOutboxFixture.작업;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RankChangeNotificationService.class, CollectionNotificationService.class, NotificationDispatcher.class,
        NotificationOutboxRepository.class, NotificationRankRoundRepository.class, DailyPressCountRepository.class,
        CollectionProgressRepository.class, DevicePushTokenRepository.class, DeviceNotificationSettingRepository.class})
class NotificationConcurrencyIntegrationTest {

    private static final ZoneId 서울 = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final Instant 오전_10시 = 한국_시각("2026-11-02T10:00:00");
    private static final Instant 오전_10시_5분 = 한국_시각("2026-11-02T10:05:00");
    private static final Duration 대기_상한 = Duration.ofSeconds(10);

    @Autowired
    private RankChangeNotificationService rankService;

    @Autowired
    private CollectionNotificationService collectionService;

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private DevicePushTokenRepository pushTokens;

    @Autowired
    private JdbcClient jdbc;

    @MockitoSpyBean
    private NotificationRankRoundRepository rounds;

    @MockitoSpyBean
    private NotificationOutboxRepository outboxes;

    @MockitoBean
    private PushSender pushSender;

    @MockitoBean
    private Clock clock;

    private final List<Long> 만든_기기들 = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(서울);
        when(pushSender.isAvailable()).thenReturn(true);
        when(pushSender.send(anyString(), any())).thenReturn(PushSendResult.SENT);
    }

    @AfterEach
    void tearDown() {
        알림_데이터를_지운다(jdbc, List.of(DAY), 만든_기기들);
    }

    @Test
    void 두_서버가_같은_회차를_동시에_비교하면_회차_선점으로_한_번만_처리한다() throws Exception {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        비교한다(오전_10시);
        개인_프레스를_저장한다(jdbc, 기기들.get(2).getId(), DAY, EmotionState.ANGRY, 250);
        when(clock.instant()).thenReturn(오전_10시_5분.plusSeconds(1));
        CountDownLatch 먼저_온_서버가_회차를_넣고_멈춤 = new CountDownLatch(1);
        CountDownLatch 커밋_허용 = new CountDownLatch(1);
        AtomicBoolean 처음 = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object previous = invocation.callRealMethod();
            if (처음.compareAndSet(true, false)) {
                먼저_온_서버가_회차를_넣고_멈춤.countDown();
                커밋_허용.await(대기_상한.toSeconds(), TimeUnit.SECONDS);
            }
            return previous;
        }).when(rounds).findPreviousSnapshot(any(LocalDate.class), any(Instant.class));

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> 먼저 = executor.submit(rankService::compareRanks);
            assertThat(먼저_온_서버가_회차를_넣고_멈춤.await(대기_상한.toSeconds(), TimeUnit.SECONDS)).isTrue();
            Future<?> 나중 = executor.submit(rankService::compareRanks);
            잠금을_기다리는_세션이_생길_때까지_기다린다("INSERT INTO notification_rank_rounds");
            커밋_허용.countDown();
            먼저.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
            나중.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
        }

        // then
        assertThat(날짜의_회차들(jdbc, DAY)).containsExactly(오전_10시, 오전_10시_5분);
        assertThat(회차의_스냅샷(jdbc, 오전_10시_5분)).hasSize(3);
        verify(rounds, times(3)).saveRound(any(Instant.class), any(LocalDate.class), any(Instant.class));
        verify(outboxes, times(2)).saveRank(anyLong(), any(LocalDate.class), any(Instant.class), anyInt(), anyInt(),
                any(Instant.class), any(Instant.class));
        assertThat(기기들).extracting(device -> 기기의_작업들(jdbc, device.getId()).size()).containsExactly(1, 1, 0);
    }

    @Test
    void 작업_저장이_실패하면_회차와_스냅샷도_남지_않아_다음_실행이_같은_회차를_다시_처리한다() {
        // given
        List<Device> 기기들 = 순서대로_누른_기기들(300, 200, 100);
        비교한다(오전_10시);
        개인_프레스를_저장한다(jdbc, 기기들.get(2).getId(), DAY, EmotionState.ANGRY, 250);
        doThrow(new IllegalStateException("작업 저장 실패")).when(outboxes).saveRank(anyLong(), any(LocalDate.class),
                any(Instant.class), anyInt(), anyInt(), any(Instant.class), any(Instant.class));
        when(clock.instant()).thenReturn(오전_10시_5분);

        // when
        assertThatThrownBy(rankService::compareRanks).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(날짜의_회차들(jdbc, DAY)).containsExactly(오전_10시);
        assertThat(회차의_스냅샷(jdbc, 오전_10시_5분)).isEmpty();
        assertThat(기기들).allSatisfy(device -> assertThat(기기의_작업들(jdbc, device.getId())).isEmpty());
        doCallRealMethod().when(outboxes).saveRank(anyLong(), any(LocalDate.class), any(Instant.class), anyInt(),
                anyInt(), any(Instant.class), any(Instant.class));
        rankService.compareRanks();
        List<Integer> 다시_처리한_이전_순위들 = 기기들.stream()
                .flatMap(device -> 기기의_작업들(jdbc, device.getId()).stream())
                .map(작업::previousRank)
                .toList();
        assertThat(다시_처리한_이전_순위들).containsExactly(1, 2);
    }

    @Test
    void 두_발송기가_동시에_선점해도_작업마다_한_번만_보낸다() throws Exception {
        // given
        int 작업_수 = 20;
        Instant 지금 = 한국_시각("2026-11-02T23:00:00");
        for (int index = 0; index < 작업_수; index++) {
            Device device = 토큰이_있는_기기("token-" + index);
            outboxes.saveCollection(COLLECTION_CLAIM_REMINDER, device.getId(), DAY, 지금, 지금, 다음_날_자정());
        }
        when(clock.instant()).thenReturn(지금);
        Map<String, AtomicInteger> 보낸_횟수 = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            보낸_횟수.computeIfAbsent(invocation.getArgument(0), token -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(20);
            return PushSendResult.SENT;
        }).when(pushSender).send(anyString(), any());
        CyclicBarrier 동시_시작 = new CyclicBarrier(2);

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> 실행들 = List.of(
                    executor.submit(() -> {
                        동시_시작.await();
                        dispatcher.dispatch();
                        return null;
                    }),
                    executor.submit(() -> {
                        동시_시작.await();
                        dispatcher.dispatch();
                        return null;
                    }));
            for (Future<?> 실행 : 실행들) {
                실행.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
            }
        }

        // then
        assertThat(보낸_횟수).hasSize(작업_수);
        assertThat(보낸_횟수.values()).allSatisfy(count -> assertThat(count.get()).isOne());
        assertThat(만든_기기들).allSatisfy(deviceId -> assertThat(기기의_작업들(jdbc, deviceId))
                .extracting(작업::status, 작업::attemptCount)
                .containsExactly(tuple("SENT", 1)));
    }

    @Test
    void 두_서버가_동시에_발급_가능을_탐색해도_기기마다_작업은_하나다() throws Exception {
        // given
        Device device = 토큰이_있는_기기("claimable");
        일별_스탬프를_저장한다(jdbc, device.getId(), DAY, EmotionState.ANGRY, 1);
        개인_프레스를_저장한다(jdbc, device.getId(), DAY, EmotionState.ANGRY, 100);
        when(clock.instant()).thenReturn(오전_10시);
        CountDownLatch 먼저_온_서버가_작업을_넣고_멈춤 = new CountDownLatch(1);
        CountDownLatch 커밋_허용 = new CountDownLatch(1);
        AtomicBoolean 처음 = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (처음.compareAndSet(true, false)) {
                먼저_온_서버가_작업을_넣고_멈춤.countDown();
                커밋_허용.await(대기_상한.toSeconds(), TimeUnit.SECONDS);
            }
            return null;
        }).when(outboxes).saveCollection(eq(COLLECTION_CLAIMABLE), anyLong(), any(LocalDate.class), any(Instant.class),
                any(Instant.class), any(Instant.class));

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> 먼저 = executor.submit(collectionService::saveClaimableOutbox);
            assertThat(먼저_온_서버가_작업을_넣고_멈춤.await(대기_상한.toSeconds(), TimeUnit.SECONDS)).isTrue();
            when(clock.instant()).thenReturn(오전_10시.plusSeconds(4));
            Future<?> 나중 = executor.submit(collectionService::saveClaimableOutbox);
            잠금을_기다리는_세션이_생길_때까지_기다린다("INSERT INTO notification_outbox");
            커밋_허용.countDown();
            먼저.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
            나중.get(대기_상한.toSeconds(), TimeUnit.SECONDS);
        }

        // then
        assertThat(기기의_작업들(jdbc, device.getId())).extracting(작업::type, 작업::discoveredAt)
                .containsExactly(tuple(NotificationOutboxType.COLLECTION_CLAIMABLE.name(), 오전_10시));
    }

    private Device 토큰이_있는_기기(String token) {
        Device device = devices.saveAndFlush(기본_기기_빌더().build());
        만든_기기들.add(device.getId());
        pushTokens.save(device.getId(), token, 오전_10시);
        return device;
    }

    private List<Device> 순서대로_누른_기기들(long... counts) {
        List<Device> 기기들 = new ArrayList<>();
        for (int index = 0; index < counts.length; index++) {
            Device device = 토큰이_있는_기기("rank-" + (index + 1));
            개인_프레스를_저장한다(jdbc, device.getId(), DAY, EmotionState.ANGRY, counts[index]);
            기기들.add(device);
        }
        return 기기들;
    }

    private void 비교한다(Instant 지금) {
        when(clock.instant()).thenReturn(지금);
        rankService.compareRanks();
    }

    private Instant 다음_날_자정() {
        return DAY.plusDays(1).atStartOfDay(서울).toInstant();
    }

    private void 잠금을_기다리는_세션이_생길_때까지_기다린다(String 문장) throws InterruptedException {
        Instant 기한 = Instant.now().plus(대기_상한);
        while (Instant.now().isBefore(기한)) {
            Integer 기다리는_세션_수 = jdbc.sql("""
                    SELECT count(*)
                      FROM pg_stat_activity
                     WHERE datname = current_database()
                       AND wait_event_type = 'Lock'
                       AND query LIKE :pattern
                    """).param("pattern", "%" + 문장 + "%").query(Integer.class).single();
            if (기다리는_세션_수 > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("나중에 온 서버의 문장이 잠금에서 기다리지 않았다: " + 문장);
    }

    private static Instant 한국_시각(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(서울).toInstant();
    }
}
