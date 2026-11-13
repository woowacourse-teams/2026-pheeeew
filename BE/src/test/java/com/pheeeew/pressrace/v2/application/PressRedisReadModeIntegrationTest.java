package com.pheeeew.pressrace.v2.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.benmanes.caffeine.cache.Ticker;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.application.token.AccessTokenIssuer;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import com.pheeeew.emotion.application.query.EmotionPressQueryService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.application.PressSnapshotService;
import com.pheeeew.pressrace.v2.domain.PressReadStatus;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressTodayRead;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import com.pheeeew.pressrace.v2.infra.PressRedisStore;
import com.pheeeew.support.SharedJwtTestConfiguration;
import com.pheeeew.support.SharedPostgisTestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** REDIS 모드로 뜬 실제 애플리케이션 컨텍스트(실제 Redis, 공유 PostGIS)의 배선과 읽기 출처를 본다. */
@Import({
        SharedPostgisTestConfiguration.class,
        SharedJwtTestConfiguration.class,
        PressRedisReadModeIntegrationTest.RedisReadModeTestConfiguration.class
})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "pheeeew.s3.bucket=pheeeew-test",
        "pheeeew.s3.key-prefix=pheeeew/test/", "pheeeew.s3.region=ap-northeast-2",
        "pheeeew.press-race.write-mode=REDIS", "pheeeew.press-race.flush-interval-ms=3600000"
})
@DirtiesContext
class PressRedisReadModeIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private PressSnapshotService pressSnapshotService;
    @Autowired
    private PressRedisTodayBoard pressRedisTodayBoard;
    @Autowired
    private PressRedisStore pressRedisStore;
    @Autowired
    private PressRedisSeeder pressRedisSeeder;
    @Autowired
    private PressBufferRepository pressBufferRepository;
    @Autowired
    private Clock clock;
    @Autowired
    private EmotionPressService emotionPressService;
    @Autowired
    private EmotionPressQueryService emotionPressQueryService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private AccessTokenIssuer accessTokenIssuer;
    @LocalServerPort
    private int port;

    private final List<Device> devices = new ArrayList<>();
    private LocalDate today;

    @BeforeEach
    void setUp() {
        pressRedisTodayBoard.stop();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        today = LocalDate.now(KST);
        pressRedisStore.markSeeded(today);
        jdbc.sql("""
                UPDATE press_race_control SET enabled = TRUE,
                  activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).update();
    }

    @AfterEach
    void tearDown() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        for (Device device : devices) {
            jdbc.sql("DELETE FROM device_daily_presses WHERE device_id = :device").param("device", device.getId()).update();
            jdbc.sql("DELETE FROM press_daily_scores WHERE device_id = :device").param("device", device.getId()).update();
            deviceRepository.deleteById(device.getId());
        }
        jdbc.sql("UPDATE press_race_control SET enabled = FALSE, activation_date = NULL").update();
    }

    @Test
    void REDIS_모드로_뜬_컨텍스트는_호출_스레드와_보드_틱_모두_DB_연결_없이_Redis_입력으로_공통_순위를_만든다() {
        // given
        String nickname = 닉네임();
        Device device = 참가한_기기(nickname);
        Device other = 기기(닉네임());
        pressRedisTodayBoard.refresh();
        emotionPressService.press(other.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        pressRedisStore.fillNicknames(Map.of(other.getPublicId(), other.getNickname()));
        double fullReads = meterRegistry.counter("pheeeew.press.redis.read.full").count();
        double unchangedReads = meterRegistry.counter("pheeeew.press.redis.read.unchanged").count();

        // when
        CountingDataSource.watch();
        pressRedisTodayBoard.refresh();
        pressRedisTodayBoard.refresh();
        PressSnapshot snapshot = pressSnapshotService.findTodaySnapshot();
        int acquired = CountingDataSource.stopWatching();
        CountingDataSource.watch();
        emotionPressQueryService.findMyDailyPresses(device.getPublicId(), 1);
        int acquiredByDatabasePath = CountingDataSource.stopWatching();

        // then
        assertThat(acquiredByDatabasePath).isPositive();
        assertThat(acquired).isZero();
        assertThat(meterRegistry.counter("pheeeew.press.redis.read.full").count()).isEqualTo(fullReads + 1);
        assertThat(meterRegistry.counter("pheeeew.press.redis.read.unchanged").count()).isEqualTo(unchangedReads + 1);
        assertThat(snapshot.findScreen(device.getPublicId(), com.pheeeew.pressrace.v2.domain.PressView.PRESS).me()
                .score()).isEqualTo(1);
        assertThat(snapshot.findScreen(other.getPublicId(), com.pheeeew.pressrace.v2.domain.PressView.PRESS).me()
                .score()).isEqualTo(1);
        assertThat(DB_오늘_합계_행_수(device)).isZero();
    }

    @Test
    void 사본이_있는_정상_상태의_보드_틱과_채우기_스레드는_DB를_읽지_않고_사본이_없는_참가자가_올_때만_채우기_스레드가_읽는다()
            throws Exception {
        // given
        ExecutorService filler = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("press-redis-nickname").daemon().factory());
        PressRedisTodayBoard board = new PressRedisTodayBoard(pressRedisStore, pressRedisSeeder, pressBufferRepository,
                clock, new SimpleMeterRegistry(), Ticker.systemTicker(), filler);
        Device named = 참가한_기기(닉네임());
        board.refresh();
        Device another = 참가한_기기(닉네임());
        Device unnamed = 기기(닉네임());

        try {
            // when
            CountingDataSource.watch();
            board.refresh();
            board.refresh();
            filler.submit(() -> { }).get(10, TimeUnit.SECONDS);
            int steady = CountingDataSource.stopWatching();
            emotionPressService.press(unnamed.getPublicId(), Map.of(EmotionState.ANGRY, 1));
            CountingDataSource.watch();
            board.refresh();
            filler.submit(() -> { }).get(10, TimeUnit.SECONDS);
            int filling = CountingDataSource.stopWatching();

            // then
            assertThat(steady).isZero();
            assertThat(filling).isPositive();
            assertThat(redis.opsForValue().get(닉네임_키(unnamed))).isEqualTo(unnamed.getNickname());
            assertThat(board.current().nicknames()).containsEntry(named.getPublicId(), named.getNickname())
                    .containsEntry(another.getPublicId(), another.getNickname());
        } finally {
            filler.shutdownNow();
        }
    }

    @Test
    void REDIS_모드의_오늘_내_개수와_총합은_DB가_아니라_POST_응답과_같은_Redis_값이다() {
        // given
        Device device = 기기(닉네임());
        Device other = 기기(닉네임());
        long totalBefore = emotionPressQueryService.findDailyTotal(0).total();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 3, EmotionState.EXHAUSTED, 1));
        EmotionPressResult pressed = emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        emotionPressService.press(other.getPublicId(), Map.of(EmotionState.IRRITATED, 2));

        // when
        EmotionPressDailyResult mine = emotionPressQueryService.findMyDailyPresses(device.getPublicId(), 0);
        EmotionPressTotalResult total = emotionPressQueryService.findDailyTotal(0);

        // then
        assertThat(mine.counts()).isEqualTo(pressed.counts());
        assertThat(mine.total()).isEqualTo(pressed.total()).isEqualTo(5);
        assertThat(mine.pressDate()).isEqualTo(today);
        assertThat(total.total()).isEqualTo(totalBefore + 7)
                .isEqualTo(pressRedisStore.readToday(today, "", -1, -1).participants().stream()
                        .mapToLong(participant -> participant.total()).sum());
        assertThat(DB_오늘_합계_행_수(device)).isZero();
    }

    @Test
    void 닉네임_변경은_커밋된_뒤에만_Redis_사본과_닉네임_버전에_반영되어_다음_읽기에_새_닉네임이_보인다() {
        // given
        String before = 닉네임();
        String after = 닉네임();
        Device device = 참가한_기기(before);
        PressTodayRead observed = pressRedisStore.readToday(today, "", -1, -1);
        List<PressTodayRead> insideTransaction = new ArrayList<>();

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deviceService.updateNickname(device.getPublicId(), after);
            insideTransaction.add(다음_읽기(observed));
        });
        PressTodayRead committed = 다음_읽기(observed);

        // then
        assertThat(insideTransaction).singleElement()
                .satisfies(read -> assertThat(read.status()).isEqualTo(PressReadStatus.UNCHANGED));
        assertThat(committed.status()).isEqualTo(PressReadStatus.READ);
        assertThat(committed.nicknameVersion()).isEqualTo(observed.nicknameVersion() + 1);
        assertThat(committed.participants()).filteredOn(participant -> participant.devicePublicId().equals(device.getPublicId()))
                .singleElement().satisfies(participant -> assertThat(participant.nickname()).isEqualTo(after));
    }

    @Test
    void 바깥_트랜잭션이_롤백되면_닉네임_변경을_Redis에_반영하지_않는다() {
        // given
        String before = 닉네임();
        Device device = 참가한_기기(before);
        PressTodayRead observed = pressRedisStore.readToday(today, "", -1, -1);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deviceService.updateNickname(device.getPublicId(), 닉네임());
            status.setRollbackOnly();
        });

        // then
        assertThat(다음_읽기(observed).status()).isEqualTo(PressReadStatus.UNCHANGED);
        assertThat(redis.opsForValue().get(닉네임_키(device))).isEqualTo(before);
    }

    @Test
    void 중복_닉네임으로_변경이_실패하면_Redis_사본과_닉네임_버전이_그대로다() {
        // given
        String taken = 닉네임();
        기기(taken);
        String before = 닉네임();
        Device device = 참가한_기기(before);
        PressTodayRead observed = pressRedisStore.readToday(today, "", -1, -1);

        // when
        Throwable thrown = catchThrowable(() -> deviceService.updateNickname(device.getPublicId(), taken));

        // then
        assertThat(thrown).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) thrown).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_DUPLICATED);
        assertThat(다음_읽기(observed).status()).isEqualTo(PressReadStatus.UNCHANGED);
        assertThat(redis.opsForValue().get(닉네임_키(device))).isEqualTo(before);
    }

    @Test
    void Redis가_멈춰도_닉네임_변경은_재시도_없이_시간_제한_한_번_안에_DB_커밋과_함께_성공한다() {
        // given
        String after = 닉네임();
        Device device = 기기(닉네임());
        double failuresBefore = meterRegistry.counter("pheeeew.press.redis.nickname.failures").count();

        // when
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        Throwable thrown;
        long elapsedNanos;
        try {
            long startedAt = System.nanoTime();
            thrown = catchThrowable(() -> deviceService.updateNickname(device.getPublicId(), after));
            elapsedNanos = System.nanoTime() - startedAt;
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        // then
        assertThat(thrown).isNull();
        assertThat(Duration.ofNanos(elapsedNanos)).isLessThan(Duration.ofMillis(1800));
        assertThat(deviceService.findNickname(device.getPublicId())).isEqualTo(after);
        assertThat(meterRegistry.counter("pheeeew.press.redis.nickname.failures").count()).isEqualTo(failuresBefore + 1);
    }

    @Test
    void 보드가_멈춘_동안에도_기본_폴링은_입력을_바로_보이고_SSE와_캐시_폴링이_쓰는_보드_스냅샷은_그대로다() {
        // given
        Device device = 참가한_기기(닉네임());
        pressRedisTodayBoard.refresh();
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 2));

        // when
        PressScreen polled = pressSnapshotService.findSnapshot(device.getPublicId(),
                com.pheeeew.pressrace.v2.domain.PressView.PRESS);
        PressScreen streamed = pressSnapshotService.findTodaySnapshot().findScreen(device.getPublicId(),
                com.pheeeew.pressrace.v2.domain.PressView.PRESS);
        PressScreen cached = pressSnapshotService.findSnapshotOn(today).findScreen(device.getPublicId(),
                com.pheeeew.pressrace.v2.domain.PressView.PRESS);

        // then
        assertThat(polled.me().score()).isEqualTo(3);
        assertThat(streamed.me().score()).isEqualTo(1);
        assertThat(cached.me().score()).isEqualTo(1);
    }

    @Test
    void 기본_폴링_응답_본문은_일별_공개_ID만_싣고_기기_ID와_순위표_멤버_문자열은_싣지_않는다() throws Exception {
        // given
        Device device = 참가한_기기(닉네임());
        Device other = 참가한_기기(닉네임());
        String publicId = 일별_공개_ID(device);
        String otherPublicId = 일별_공개_ID(other);

        // when
        HttpResponse<String> response = 기본_폴링을_요청한다(device, "?view=ALL");

        // then
        JsonNode body = JsonMapper.builder().build().readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("me").get("publicId").asString()).isEqualTo(publicId);
        assertThat(body.get("top100").findValues("publicId")).extracting(JsonNode::asString)
                .containsExactlyInAnyOrder(publicId, otherPublicId);
        assertThat(response.body()).doesNotContain(device.getPublicId().toString(), other.getPublicId().toString());
    }

    @Test
    void Redis가_멈추면_기본_폴링은_보드_유예_없이_503_STORE_UNAVAILABLE과_Retry_After_1초다() throws Exception {
        // given
        Device device = 참가한_기기(닉네임());
        pressRedisTodayBoard.refresh();

        // when
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        HttpResponse<String> response;
        try {
            response = 기본_폴링을_요청한다(device, "");
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        // then
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Retry-After")).hasValue("1");
        assertThat(JsonMapper.builder().build().readTree(response.body()).get("code").asString())
                .isEqualTo("STORE_UNAVAILABLE");
    }

    private Device 참가한_기기(String nickname) {
        Device device = 기기(nickname);
        emotionPressService.press(device.getPublicId(), Map.of(EmotionState.ANGRY, 1));
        pressRedisStore.fillNicknames(Map.of(device.getPublicId(), nickname));
        return device;
    }

    private HttpResponse<String> 기본_폴링을_요청한다(Device device, String query) throws Exception {
        String token = accessTokenIssuer.issue(device.getPublicId()).accessToken();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/api/v1/press-race/snapshot" + query))
                    .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private String 일별_공개_ID(Device device) {
        return redis.opsForHash().get("{press}:device:" + today + ":" + device.getPublicId(), "public_id").toString();
    }

    private PressTodayRead 다음_읽기(PressTodayRead observed) {
        return pressRedisStore.readToday(today, observed.token(), observed.version(), observed.nicknameVersion());
    }

    private String 닉네임_키(Device device) {
        return "{press}:nickname:" + device.getPublicId();
    }

    private long DB_오늘_합계_행_수(Device device) {
        return jdbc.sql("SELECT COUNT(*) FROM press_daily_scores WHERE device_id = :device AND press_date = :date")
                .param("device", device.getId()).param("date", today).query(Long.class).single();
    }

    private Device 기기(String nickname) {
        Device device = deviceRepository.save(기본_기기_빌더().nickname(nickname).build());
        devices.add(device);
        return device;
    }

    private String 닉네임() {
        return "n" + UUID.randomUUID().toString().substring(0, 8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisReadModeTestConfiguration {

        @Bean
        DynamicPropertyRegistrar redisProperties() {
            if (!REDIS.isRunning()) {
                REDIS.start();
            }
            return registry -> {
                registry.add("spring.data.redis.host", REDIS::getHost);
                registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
            };
        }

        @Bean
        static BeanPostProcessor countingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource && !(bean instanceof CountingDataSource)) {
                        return new CountingDataSource(dataSource);
                    }
                    return bean;
                }
            };
        }
    }

    /**
     * 지켜보는 동안 호출 스레드와 보드 스레드(press-redis-*)가 DB 연결을 얻은 횟수를 센다.
     * 플러셔, 경주 제어 캐시 비동기 갱신 등 다른 스레드의 연결은 세지 않는다.
     */
    static final class CountingDataSource extends DelegatingDataSource {

        private static final String BOARD_THREAD_PREFIX = "press-redis-";

        private static volatile Thread watched;
        private static final AtomicInteger acquired = new AtomicInteger();

        private CountingDataSource(DataSource target) {
            super(target);
        }

        static void watch() {
            acquired.set(0);
            watched = Thread.currentThread();
        }

        static int stopWatching() {
            watched = null;
            return acquired.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            count();
            return super.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            count();
            return super.getConnection(username, password);
        }

        private void count() {
            Thread current = Thread.currentThread();
            if (watched != null && (current == watched || current.getName().startsWith(BOARD_THREAD_PREFIX))) {
                acquired.incrementAndGet();
            }
        }
    }
}
