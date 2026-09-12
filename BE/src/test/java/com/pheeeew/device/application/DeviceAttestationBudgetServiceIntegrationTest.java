package com.pheeeew.device.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.device.domain.repository.DeviceAttestationBudgetRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.device.infra.attestation.PlayIntegrityProperties;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceAttestationBudgetServiceIntegrationTest {

    private static final int 동시_요청_수 = 8;
    private static final int 남긴_예산 = 3;

    @Autowired
    private DeviceAttestationBudgetService deviceAttestationBudgetService;

    @Autowired
    private DeviceAttestationBudgetRepository deviceAttestationBudgetRepository;

    @Autowired
    private PlayIntegrityProperties playIntegrityProperties;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        deviceAttestationBudgetRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        deviceAttestationBudgetRepository.deleteAllInBatch();
    }

    @Test
    void 첫_호출은_오늘_UTC_날짜의_행을_만들고_하나를_센다() {
        // given / when
        deviceAttestationBudgetService.consumeCall();

        // then
        assertThat(deviceAttestationBudgetRepository.count()).isOne();
        assertThat(저장된_날짜()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(오늘_호출_수()).isOne();
    }

    @Test
    void 같은_날_호출은_새_행을_만들지_않고_같은_행을_늘린다() {
        // given / when
        deviceAttestationBudgetService.consumeCall();
        deviceAttestationBudgetService.consumeCall();
        deviceAttestationBudgetService.consumeCall();

        // then
        assertThat(deviceAttestationBudgetRepository.count()).isOne();
        assertThat(오늘_호출_수()).isEqualTo(3);
    }

    @Test
    void 예산을_다_쓰면_다음_UTC_자정_뒤_재시도를_알리는_증명_불가로_거절하고_카운터는_상한에_머문다() {
        // given
        오늘_호출_수를_맞춘다(일일_예산());

        // when
        Throwable throwable = catchThrowable(() -> deviceAttestationBudgetService.consumeCall());

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        DeviceException 거절 = (DeviceException) throwable;
        assertThat(거절.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE);
        assertThat(거절.getErrorCode().getCode()).isEqualTo("DEVICE-007");
        assertThat(거절.getErrorCode().getStatus().value()).isEqualTo(503);
        Instant 다음_예산_날짜 = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(거절.getRetryAfter())
                .isCloseTo(Duration.between(Instant.now(), 다음_예산_날짜), Duration.ofSeconds(10));
        assertThat(오늘_호출_수()).isEqualTo(일일_예산());
    }

    @Test
    void 상한_직전의_마지막_한_건은_쓸_수_있다() {
        // given
        오늘_호출_수를_맞춘다(일일_예산() - 1);

        // when
        deviceAttestationBudgetService.consumeCall();

        // then
        assertThat(오늘_호출_수()).isEqualTo(일일_예산());
    }

    @Test
    void 동시_호출에서도_남은_예산만큼만_성공하고_카운터가_상한을_넘지_않는다() throws Exception {
        // given
        오늘_호출_수를_맞춘다(일일_예산() - 남긴_예산);
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
                    return catchThrowable(() -> deviceAttestationBudgetService.consumeCall());
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
        assertThat(결과들.stream().filter(Objects::isNull)).hasSize(남긴_예산);
        assertThat(결과들.stream().filter(Objects::nonNull).toList())
                .hasSize(동시_요청_수 - 남긴_예산)
                .allSatisfy(this::증명을_지금_확인할_수_없다);
        assertThat(오늘_호출_수()).isEqualTo(일일_예산());
        assertThat(deviceAttestationBudgetRepository.count()).isOne();
    }

    @Test
    void 어제_예산을_다_썼어도_오늘은_새_행에_다시_센다() {
        // given
        어제_호출_수를_맞춘다(일일_예산());

        // when
        deviceAttestationBudgetService.consumeCall();

        // then
        assertThat(deviceAttestationBudgetRepository.count()).isEqualTo(2);
        assertThat(오늘_호출_수()).isOne();
        assertThat(어제_호출_수()).isEqualTo(일일_예산());
    }

    private int 일일_예산() {
        return playIntegrityProperties.dailyCallBudget();
    }

    private void 오늘_호출_수를_맞춘다(int callCount) {
        호출_수를_맞춘다("(NOW() AT TIME ZONE 'UTC')::date", callCount);
    }

    private void 어제_호출_수를_맞춘다(int callCount) {
        호출_수를_맞춘다("(NOW() AT TIME ZONE 'UTC')::date - 1", callCount);
    }

    private void 호출_수를_맞춘다(String budgetDateExpression, int callCount) {
        jdbcClient.sql("""
                        INSERT INTO device_attestation_budgets (budget_date, call_count, created_at, updated_at)
                             VALUES (%s, :callCount, NOW(), NOW())
                        ON CONFLICT (budget_date) DO UPDATE SET call_count = :callCount, updated_at = NOW()
                        """.formatted(budgetDateExpression))
                .param("callCount", callCount)
                .update();
    }

    private LocalDate 저장된_날짜() {
        return jdbcClient.sql("SELECT budget_date FROM device_attestation_budgets")
                .query(LocalDate.class)
                .single();
    }

    private int 오늘_호출_수() {
        return jdbcClient.sql("""
                        SELECT call_count
                          FROM device_attestation_budgets
                         WHERE budget_date = (NOW() AT TIME ZONE 'UTC')::date
                        """)
                .query(Integer.class)
                .single();
    }

    private int 어제_호출_수() {
        return jdbcClient.sql("""
                        SELECT call_count
                          FROM device_attestation_budgets
                         WHERE budget_date = (NOW() AT TIME ZONE 'UTC')::date - 1
                        """)
                .query(Integer.class)
                .single();
    }

    private void 증명을_지금_확인할_수_없다(Throwable throwable) {
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_ATTESTATION_UNAVAILABLE);
    }

    @Test
    void 바깥_트랜잭션이_롤백돼도_예산_사용량은_남는다() {
        // when
        transactionTemplate.executeWithoutResult(status -> {
            deviceAttestationBudgetService.consumeCall();
            status.setRollbackOnly();
        });

        // then
        Integer 사용량 = jdbcClient.sql("SELECT call_count FROM device_attestation_budgets WHERE budget_date = ?")
                .param(LocalDate.now(ZoneOffset.UTC))
                .query(Integer.class)
                .single();
        assertThat(사용량).isOne();
    }
}
