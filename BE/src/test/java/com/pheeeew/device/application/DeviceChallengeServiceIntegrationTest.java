package com.pheeeew.device.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.application.dto.DeviceChallengeResult;
import com.pheeeew.device.domain.DeviceChallenge;
import com.pheeeew.device.domain.repository.DeviceChallengeRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceChallengeServiceIntegrationTest {

    private static final int 동시_요청_수 = 6;
    private static final int 동시_시도_수 = 12;
    private static final String 발급하지_않은_challenge = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private DeviceChallengeService deviceChallengeService;

    @Autowired
    private DeviceChallengeRepository deviceChallengeRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        deviceChallengeRepository.deleteAllInBatch();
    }

    @Test
    void 발급하면_미소모_행이_하나_생기고_만료가_5분_뒤다() {
        // given
        Instant 발급_전 = Instant.now();

        // when
        DeviceChallengeResult result = deviceChallengeService.save();

        // then
        assertThat(deviceChallengeRepository.count()).isOne();
        DeviceChallenge 저장된_challenge = deviceChallengeRepository.findAll().getFirst();
        assertThat(저장된_challenge.getChallenge()).isEqualTo(result.challenge());
        assertThat(저장된_challenge.getConsumedAt()).isNull();
        assertThat(저장된_challenge.getExpiresAt())
                .isBetween(발급_전.plus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Test
    void 발급한_challenge_는_URL_안전_문자_43자이고_만료_시간을_300초로_알린다() {
        // given / when
        DeviceChallengeResult result = deviceChallengeService.save();

        // then
        assertThat(result.challenge()).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(result.expiresIn()).isEqualTo(300L);
    }

    @Test
    void 두_번_발급하면_서로_다른_값을_준다() {
        // given / when
        DeviceChallengeResult 첫_발급 = deviceChallengeService.save();
        DeviceChallengeResult 두번째_발급 = deviceChallengeService.save();

        // then
        assertThat(첫_발급.challenge()).isNotEqualTo(두번째_발급.challenge());
        assertThat(deviceChallengeRepository.count()).isEqualTo(2);
    }

    @Test
    void 소모하면_소모_시각이_채워진다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();

        // when
        deviceChallengeService.consume(발급.challenge());

        // then
        assertThat(deviceChallengeRepository.findAll().getFirst().getConsumedAt()).isNotNull();
    }

    @Test
    void 같은_challenge_를_두_번_소모하면_두_번째가_실패한다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        deviceChallengeService.consume(발급.challenge());

        // when
        Throwable throwable = catchThrowable(() -> deviceChallengeService.consume(발급.challenge()));

        // then
        challenge_를_사용할_수_없다(throwable);
        assertThat(deviceChallengeRepository.count()).isOne();
    }

    @Test
    void 같은_challenge_를_동시에_소모하면_정확히_한_건만_성공한다() throws Exception {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        CountDownLatch ready = new CountDownLatch(동시_요청_수);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<Throwable> 결과들 = new ArrayList<>();
        try (ExecutorService executorService = Executors.newFixedThreadPool(동시_요청_수)) {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int index = 0; index < 동시_요청_수; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return catchThrowable(() -> deviceChallengeService.consume(발급.challenge()));
                }));
            }
            boolean 모두_준비됨 = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(모두_준비됨).isTrue();
            for (Future<Throwable> future : futures) {
                결과들.add(future.get(10, TimeUnit.SECONDS));
            }
        }

        // then
        assertThat(결과들).hasSize(동시_요청_수);
        assertThat(결과들.stream().filter(Objects::isNull)).hasSize(1);
        assertThat(결과들.stream().filter(Objects::nonNull).toList())
                .hasSize(동시_요청_수 - 1)
                .allSatisfy(this::challenge_를_사용할_수_없다);
        assertThat(deviceChallengeRepository.findAll().getFirst().getConsumedAt()).isNotNull();
    }

    @Test
    void 시도는_상한까지_소모할_수_있고_상한을_넘는_시도는_거절된다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();

        // when
        for (int 시도 = 0; 시도 < DeviceChallenge.MAX_ATTEMPTS; 시도++) {
            deviceChallengeService.consumeAttempt(발급.challenge());
        }
        Throwable throwable = catchThrowable(() -> deviceChallengeService.consumeAttempt(발급.challenge()));

        // then
        challenge_를_사용할_수_없다(throwable);
        DeviceChallenge 저장된_challenge = deviceChallengeRepository.findAll().getFirst();
        assertThat(저장된_challenge.getAttemptCount()).isEqualTo(DeviceChallenge.MAX_ATTEMPTS);
        assertThat(저장된_challenge.getConsumedAt()).isNull();
    }

    @Test
    void 없음과_만료와_이미_소모된_challenge_는_시도를_소모할_수_없다() {
        // given
        DeviceChallengeResult 소모한_challenge = deviceChallengeService.save();
        deviceChallengeService.consume(소모한_challenge.challenge());
        DeviceChallengeResult 만료한_challenge = deviceChallengeService.save();
        발급_시각을_민다(만료한_challenge.challenge(), "5 minutes 1 second");

        // when
        List<Throwable> 실패들 = List.of(
                catchThrowable(() -> deviceChallengeService.consumeAttempt(발급하지_않은_challenge)),
                catchThrowable(() -> deviceChallengeService.consumeAttempt(만료한_challenge.challenge())),
                catchThrowable(() -> deviceChallengeService.consumeAttempt(소모한_challenge.challenge()))
        );

        // then
        assertThat(실패들).allSatisfy(this::challenge_를_사용할_수_없다);
        assertThat(deviceChallengeRepository.findAll())
                .extracting(DeviceChallenge::getAttemptCount)
                .containsOnly(0);
    }

    @Test
    void 같은_challenge_에_동시에_시도를_소모해도_상한을_넘지_않는다() throws Exception {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        CountDownLatch ready = new CountDownLatch(동시_시도_수);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<Throwable> 결과들 = new ArrayList<>();
        try (ExecutorService executorService = Executors.newFixedThreadPool(동시_시도_수)) {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int index = 0; index < 동시_시도_수; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return catchThrowable(() -> deviceChallengeService.consumeAttempt(발급.challenge()));
                }));
            }
            boolean 모두_준비됨 = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(모두_준비됨).isTrue();
            for (Future<Throwable> future : futures) {
                결과들.add(future.get(10, TimeUnit.SECONDS));
            }
        }

        // then
        assertThat(결과들.stream().filter(Objects::isNull)).hasSize(DeviceChallenge.MAX_ATTEMPTS);
        assertThat(결과들.stream().filter(Objects::nonNull).toList())
                .hasSize(동시_시도_수 - DeviceChallenge.MAX_ATTEMPTS)
                .allSatisfy(this::challenge_를_사용할_수_없다);
        assertThat(deviceChallengeRepository.findAll().getFirst().getAttemptCount())
                .isEqualTo(DeviceChallenge.MAX_ATTEMPTS);
    }

    @Test
    void 만료된_challenge_는_소모할_수_없다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        발급_시각을_민다(발급.challenge(), "5 minutes 1 second");

        // when
        Throwable throwable = catchThrowable(() -> deviceChallengeService.consume(발급.challenge()));

        // then
        challenge_를_사용할_수_없다(throwable);
        assertThat(deviceChallengeRepository.findAll().getFirst().getConsumedAt()).isNull();
    }

    @Test
    void 만료_직전의_challenge_는_소모할_수_있다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        발급_시각을_민다(발급.challenge(), "4 minutes 59 seconds");

        // when
        deviceChallengeService.consume(발급.challenge());

        // then
        assertThat(deviceChallengeRepository.findAll().getFirst().getConsumedAt()).isNotNull();
    }

    @Test
    void 발급하지_않은_challenge_는_소모할_수_없다() {
        // given / when
        Throwable throwable = catchThrowable(() -> deviceChallengeService.consume(발급하지_않은_challenge));

        // then
        challenge_를_사용할_수_없다(throwable);
    }

    @Test
    void 없음과_만료와_이미_소모의_실패가_모두_같은_코드와_메시지다() {
        // given
        DeviceChallengeResult 소모할_challenge = deviceChallengeService.save();
        deviceChallengeService.consume(소모할_challenge.challenge());
        DeviceChallengeResult 만료할_challenge = deviceChallengeService.save();
        발급_시각을_민다(만료할_challenge.challenge(), "5 minutes 1 second");

        // when
        List<Throwable> 실패들 = List.of(
                catchThrowable(() -> deviceChallengeService.consume(발급하지_않은_challenge)),
                catchThrowable(() -> deviceChallengeService.consume(만료할_challenge.challenge())),
                catchThrowable(() -> deviceChallengeService.consume(소모할_challenge.challenge()))
        );

        // then
        assertThat(실패들).allSatisfy(this::challenge_를_사용할_수_없다);
        assertThat(실패들).extracting(Throwable::getMessage)
                .containsOnly("무결성 증명 요청 값을 사용할 수 없습니다.");
    }

    @Test
    void 실패_메시지에_제출한_challenge_값이_들어가지_않는다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        deviceChallengeService.consume(발급.challenge());

        // when
        Throwable throwable = catchThrowable(() -> deviceChallengeService.consume(발급.challenge()));

        // then
        assertThat(throwable.getMessage()).doesNotContain(발급.challenge());
    }

    @Test
    void 정리는_만료된_행만_지우고_살아_있는_행을_남긴다() {
        // given
        DeviceChallengeResult 만료된_미소모 = deviceChallengeService.save();
        발급_시각을_민다(만료된_미소모.challenge(), "6 minutes");
        DeviceChallengeResult 만료된_소모됨 = deviceChallengeService.save();
        deviceChallengeService.consume(만료된_소모됨.challenge());
        발급_시각을_민다(만료된_소모됨.challenge(), "6 minutes");
        DeviceChallengeResult 살아_있는_challenge = deviceChallengeService.save();

        // when
        int 지운_수 = deviceChallengeService.deleteExpired();

        // then
        assertThat(지운_수).isEqualTo(2);
        assertThat(deviceChallengeRepository.findAll())
                .extracting(DeviceChallenge::getChallenge)
                .containsExactly(살아_있는_challenge.challenge());
    }

    @Test
    void 소모된_challenge_는_정리되기_전에도_다시_쓸_수_없다() {
        // given
        DeviceChallengeResult 발급 = deviceChallengeService.save();
        deviceChallengeService.consume(발급.challenge());

        // when
        int 지운_수 = deviceChallengeService.deleteExpired();
        Throwable throwable = catchThrowable(() -> deviceChallengeService.consume(발급.challenge()));

        // then
        assertThat(지운_수).isZero();
        assertThat(deviceChallengeRepository.count()).isOne();
        challenge_를_사용할_수_없다(throwable);
    }

    @Test
    void 저장한_행에는_기기_식별자를_담을_자리가_없다() {
        // given
        deviceChallengeService.save();

        // when
        Map<String, Object> 저장된_행 = jdbcClient.sql("SELECT * FROM device_challenges")
                .query()
                .singleRow();

        // then
        assertThat(저장된_행.keySet())
                .containsExactlyInAnyOrder(
                        "id", "challenge", "expires_at", "consumed_at", "attempt_count", "created_at", "updated_at"
                );
    }

    private void 발급_시각을_민다(String challenge, String interval) {
        jdbcClient.sql("""
                        UPDATE device_challenges
                        SET expires_at = NOW() - CAST(:interval AS INTERVAL) + INTERVAL '5 minutes'
                        WHERE challenge = :challenge
                        """)
                .param("interval", interval)
                .param("challenge", challenge)
                .update();
    }

    private void challenge_를_사용할_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_CHALLENGE_INVALID);
    }

    @Test
    void 바깥_트랜잭션이_롤백돼도_시도_수는_남는다() {
        // given
        String challenge = deviceChallengeService.save().challenge();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            deviceChallengeService.consumeAttempt(challenge);
            status.setRollbackOnly();
        });

        // then
        Integer 시도_수 = jdbcClient.sql("SELECT attempt_count FROM device_challenges WHERE challenge = ?")
                .param(challenge)
                .query(Integer.class)
                .single();
        assertThat(시도_수).isOne();
    }

    @Test
    void 바깥_트랜잭션이_롤백돼도_소모_표시는_남는다() {
        // given
        String challenge = deviceChallengeService.save().challenge();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            deviceChallengeService.consume(challenge);
            status.setRollbackOnly();
        });

        // then
        Instant 소모_시각 = jdbcClient.sql("SELECT consumed_at FROM device_challenges WHERE challenge = ?")
                .param(challenge)
                .query(Instant.class)
                .single();
        assertThat(소모_시각).isNotNull();
    }
}
