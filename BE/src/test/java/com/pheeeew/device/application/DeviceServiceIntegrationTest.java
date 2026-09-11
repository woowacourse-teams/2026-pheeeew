package com.pheeeew.device.application;

import static com.pheeeew.device.fixture.DeviceFixture.토큰_해시;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceServiceIntegrationTest {

    private static final int 동시_요청_수 = 6;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        deviceRefreshTokenRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 최초_등록은_기기와_리프레시_토큰을_하나씩_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, DevicePlatform.ANDROID);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresIn()).isEqualTo(1800L);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 같은_요청_식별자로_다시_등록해도_기기는_하나만_생긴다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult first = deviceService.save(requestId, DevicePlatform.ANDROID);

        // when
        DeviceSaveResult retried = deviceService.save(requestId, DevicePlatform.IOS);

        // then
        assertThat(retried.created()).isFalse();
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRepository.findByRequestId(requestId).orElseThrow().getPlatform())
                .isEqualTo(DevicePlatform.ANDROID);
        assertThat(retried.refreshToken()).isNotEqualTo(first.refreshToken());
    }

    @Test
    void 재시도로_받은_두_리프레시_토큰_모두_재발급에_쓸_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult first = deviceService.save(requestId, DevicePlatform.ANDROID);
        DeviceSaveResult retried = deviceService.save(requestId, DevicePlatform.ANDROID);

        // when
        String firstSubject = 기기_공개_식별자를_뽑는다(deviceTokenService.reissueAccessToken(first.refreshToken())
                .accessToken());
        String retriedSubject = 기기_공개_식별자를_뽑는다(deviceTokenService.reissueAccessToken(retried.refreshToken())
                .accessToken());

        // then
        assertThat(firstSubject).isEqualTo(retriedSubject);
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_요청_식별자로_동시에_등록해도_기기는_하나만_생기고_모두_토큰을_받는다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        List<DeviceSaveResult> results = 동시에_등록한다(requestId);

        // then
        assertThat(results).hasSize(동시_요청_수);
        assertThat(results).allMatch(result -> !result.refreshToken().isBlank());
        assertThat(results).filteredOn(DeviceSaveResult::created).hasSize(1);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(동시_요청_수);
    }

    @Test
    void 동시_등록에서_경합에_진_요청이_받은_토큰도_재발급에_쓸_수_있다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        List<DeviceSaveResult> results = 동시에_등록한다(requestId);

        // when
        List<String> subjects = results.stream()
                .map(result -> deviceTokenService.reissueAccessToken(result.refreshToken()).accessToken())
                .map(this::기기_공개_식별자를_뽑는다)
                .distinct()
                .toList();

        // then
        assertThat(results).hasSize(동시_요청_수);
        assertThat(subjects).hasSize(1);
    }

    @Test
    void 동시_등록의_제약_위반은_요청_식별자를_로그에_남기지_않는다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        ListAppender<ILoggingEvent> appender = 로그_수집을_시작한다();

        try {
            // when
            List<DeviceSaveResult> results = 동시에_등록한다(requestId);

            // then
            Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).doesNotContain(requestId.toString());
            assertThat(남은_로그).doesNotContain(device.getPublicId().toString());
            for (DeviceSaveResult result : results) {
                assertThat(남은_로그).doesNotContain(result.refreshToken());
                assertThat(남은_로그).doesNotContain(result.accessToken());
            }
        } finally {
            로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void 등록_후_5분이_지나기_전_재요청에는_토큰을_다시_발급한다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, DevicePlatform.ANDROID);
        등록_시각을_민다(requestId, "4 minutes 59 seconds");

        // when
        DeviceSaveResult retried = deviceService.save(requestId, DevicePlatform.ANDROID);

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.refreshToken()).isNotBlank();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 등록_후_5분이_지난_재요청에는_토큰을_발급하지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, DevicePlatform.ANDROID);
        등록_시각을_민다(requestId, "5 minutes 1 second");

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(requestId, DevicePlatform.ANDROID));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_REGISTRATION_WINDOW_EXPIRED);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 창_밖_재요청의_예외_메시지에_요청_식별자가_들어가지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, DevicePlatform.ANDROID);
        등록_시각을_민다(requestId, "5 minutes 1 second");

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(requestId, DevicePlatform.ANDROID));

        // then
        assertThat(throwable).hasMessage("기기 등록 재시도 시간이 지났습니다. 새 요청으로 등록해 주세요.");
        assertThat(throwable.getMessage()).doesNotContain(requestId.toString());
    }

    @Test
    void 리프레시_토큰은_해시로만_저장되고_액세스_토큰은_저장되지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, DevicePlatform.ANDROID);

        // then
        Map<String, Object> row = jdbcClient.sql("SELECT * FROM device_refresh_tokens")
                .query()
                .singleRow();
        String 저장된_행 = row.toString();
        assertThat(row.get("token_hash")).isEqualTo(토큰_해시(result.refreshToken()));
        assertThat(저장된_행)
                .doesNotContain(result.refreshToken())
                .doesNotContain(result.accessToken());
        assertThat(row.get("revoked_at")).isNull();
        assertThat(row.get("expires_at")).isNull();
    }

    @Test
    void 발급한_액세스_토큰의_대상은_그_기기의_공개_식별자다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, DevicePlatform.ANDROID);

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        AccessTokenClaims claims = AccessTokenClaims.from(jwtDecoder.decode(result.accessToken()));
        assertThat(claims.devicePublicId()).isEqualTo(device.getPublicId());
        assertThat(device.getId()).isNotNull();
        assertThat(catchThrowable(() -> Long.parseLong(claims.devicePublicId().toString())))
                .isInstanceOf(NumberFormatException.class);
    }

    private List<DeviceSaveResult> 동시에_등록한다(UUID requestId) throws Exception {
        CountDownLatch ready = new CountDownLatch(동시_요청_수);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(동시_요청_수)) {
            List<Future<DeviceSaveResult>> futures = new ArrayList<>();
            for (int index = 0; index < 동시_요청_수; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return deviceService.save(requestId, DevicePlatform.ANDROID);
                }));
            }

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            List<DeviceSaveResult> results = new ArrayList<>();
            for (Future<DeviceSaveResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private void 등록_시각을_민다(UUID requestId, String interval) {
        jdbcClient.sql("""
                        UPDATE devices
                        SET created_at = NOW() - CAST(:interval AS INTERVAL)
                        WHERE request_id = :requestId
                        """)
                .param("interval", interval)
                .param("requestId", requestId)
                .update();
    }

    private String 기기_공개_식별자를_뽑는다(String accessToken) {
        return AccessTokenClaims.from(jwtDecoder.decode(accessToken)).devicePublicId().toString();
    }

    private ListAppender<ILoggingEvent> 로그_수집을_시작한다() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        루트_로거().addAppender(appender);
        개발_프로파일_로거().forEach(logger -> logger.setLevel(Level.DEBUG));
        return appender;
    }

    private void 로그_수집을_끝낸다(ListAppender<ILoggingEvent> appender) {
        개발_프로파일_로거().forEach(logger -> logger.setLevel(null));
        루트_로거().detachAppender(appender);
        appender.stop();
    }

    private List<Logger> 개발_프로파일_로거() {
        return List.of(
                (Logger) LoggerFactory.getLogger("com.pheeeew"),
                (Logger) LoggerFactory.getLogger("org.hibernate.SQL")
        );
    }

    private String 수집한_로그(ListAppender<ILoggingEvent> appender) {
        StringBuilder collected = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            collected.append(event.getLoggerName()).append(' ').append(event.getFormattedMessage()).append('\n');
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            while (throwableProxy != null) {
                collected.append(throwableProxy.getMessage()).append('\n');
                throwableProxy = throwableProxy.getCause();
            }
        }
        return collected.toString();
    }

    private Logger 루트_로거() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
