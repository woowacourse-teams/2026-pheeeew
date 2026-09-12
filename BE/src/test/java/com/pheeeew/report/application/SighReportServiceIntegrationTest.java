package com.pheeeew.report.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.report.fixture.SighReportFixture.기본_신고_사유;
import static com.pheeeew.report.fixture.SighReportFixture.없는_기기_공개_식별자;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.domain.SighReport;
import com.pheeeew.report.domain.repository.SighReportRepository;
import com.pheeeew.report.exception.SighReportErrorCode;
import com.pheeeew.report.exception.SighReportException;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SighReportServiceIntegrationTest {

    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;
    private static final Long NOT_EXISTING_SIGH_ID = Long.MAX_VALUE;
    private static final Long NOT_EXISTING_DEVICE_ID = Long.MAX_VALUE;
    private static final String REJECTED_REASON = "저장이 거부되는 사유";

    @Autowired
    private SighReportService sighReportService;

    @Autowired
    private SighReportRepository sighReportRepository;

    @Autowired
    private SighRepository sighRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        sighReportRepository.deleteAll();
        sighRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void 삭제된_한숨의_신고_기록은_그대로_남는다() {
        // given
        Long sighId = insertSigh();
        Device device = insertDevice();
        sighReportService.save(sighId, device.getPublicId(), 기본_신고_사유());

        // when
        jdbcClient.sql("UPDATE sighs SET deleted_at = NOW() WHERE id = :id")
                .param("id", sighId)
                .update();

        // then
        assertThat(sighReportRepository.findBySighIdAndReporterDeviceId(sighId, device.getId()))
                .isPresent();
        assertThat(sighReportRepository.count()).isOne();
    }

    @Test
    void 처음_신고하는_기기의_신고를_저장한다() {
        // given
        Long sighId = insertSigh();
        Device device = insertDevice();

        // when
        SighReportResult result = sighReportService.save(sighId, device.getPublicId(), "  광고성 게시물입니다  ");

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.id()).isPositive();
        assertThat(result.sighId()).isEqualTo(sighId);
        assertThat(result.reason()).isEqualTo("광고성 게시물입니다");
        assertThat(result.createdAt()).isNotNull();

        SighReport saved = sighReportRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getReporterDeviceId()).isEqualTo(device.getId());
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(sighReportRepository.count()).isOne();
    }

    @Test
    void 같은_기기가_같은_한숨을_다시_신고하면_최초_신고를_반환한다() {
        // given
        Long sighId = insertSigh();
        Device device = insertDevice();
        SighReportResult first = sighReportService.save(sighId, device.getPublicId(), 기본_신고_사유());

        // when
        SighReportResult retried = sighReportService.save(sighId, device.getPublicId(), "나중에 바꾼 사유입니다");

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(retried.reason()).isEqualTo(기본_신고_사유());
        assertThat(sighReportRepository.count()).isOne();
    }

    @Test
    void 다른_기기가_같은_한숨을_신고하면_신고를_따로_저장한다() {
        // given
        Long sighId = insertSigh();
        Device device = insertDevice();
        Device otherDevice = insertDevice();
        SighReportResult first = sighReportService.save(sighId, device.getPublicId(), 기본_신고_사유());

        // when
        SighReportResult second = sighReportService.save(sighId, otherDevice.getPublicId(), 기본_신고_사유());

        // then
        assertThat(second.created()).isTrue();
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(sighReportRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_기기가_다른_한숨을_신고하면_신고를_따로_저장한다() {
        // given
        Long sighId = insertSigh();
        Long otherSighId = insertSigh();
        Device device = insertDevice();
        SighReportResult first = sighReportService.save(sighId, device.getPublicId(), 기본_신고_사유());

        // when
        SighReportResult second = sighReportService.save(otherSighId, device.getPublicId(), 기본_신고_사유());

        // then
        assertThat(second.created()).isTrue();
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(sighReportRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_기기가_같은_한숨을_동시에_신고해도_한_건만_저장한다() throws Exception {
        // given
        int requestCount = 6;
        Long sighId = insertSigh();
        Device device = insertDevice();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<SighReportResult> results = executeConcurrently(
                requestCount,
                sighId,
                device.getPublicId(),
                ready,
                start
        );

        // then
        assertThat(results)
                .extracting(SighReportResult::id)
                .containsOnly(results.getFirst().id());
        assertThat(results).filteredOn(SighReportResult::created).hasSize(1);
        assertThat(sighReportRepository.count()).isOne();
    }

    @Test
    void 존재하지_않는_한숨을_신고하면_한숨_없음_예외가_발생한다() {
        // given
        Device device = insertDevice();

        // when
        Throwable throwable = catchThrowable(
                () -> sighReportService.save(NOT_EXISTING_SIGH_ID, device.getPublicId(), 기본_신고_사유())
        );

        // then
        assertThat(throwable)
                .isInstanceOf(SighException.class)
                .hasMessage("한숨을 찾을 수 없습니다.");
        assertThat(((SighException) throwable).getErrorCode())
                .isEqualTo(SighErrorCode.SIGH_NOT_FOUND);
        assertThat(sighReportRepository.count()).isZero();
    }

    @Test
    void 등록되지_않은_기기로_신고하면_기기_없음_예외가_발생한다() {
        // given
        Long sighId = insertSigh();

        // when
        Throwable throwable = catchThrowable(
                () -> sighReportService.save(sighId, 없는_기기_공개_식별자(), 기본_신고_사유())
        );

        // then
        assertThat(throwable)
                .isInstanceOf(DeviceException.class)
                .hasMessage("인증 정보를 사용할 수 없습니다.");
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
        assertThat(sighReportRepository.count()).isZero();
    }

    @Test
    void 존재하지_않는_기기를_신고자로_저장하면_외래_키가_막는다() {
        // given
        Long sighId = insertSigh();
        SighReport report = SighReport.builder()
                .sighId(sighId)
                .reporterDeviceId(NOT_EXISTING_DEVICE_ID)
                .reason(기본_신고_사유())
                .build();

        // when
        Throwable throwable = catchThrowable(() -> sighReportRepository.saveAndFlush(report));

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(예외_사슬의_메시지(throwable)).contains("fk_sigh_reports_device");
        assertThat(sighReportRepository.count()).isZero();
    }

    @Test
    void 중복_신고의_제약_위반은_신고자_기기를_로그에_남기지_않는다() throws Exception {
        // given
        Long sighId = insertSigh();
        Device device = insertDevice();
        ListAppender<ILoggingEvent> appender = 로그_수집을_시작한다();

        try {
            // when
            executeConcurrently(6, sighId, device.getPublicId(), new CountDownLatch(6), new CountDownLatch(1));

            // then
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).contains("sigh_reports");
            assertThat(남은_로그)
                    .doesNotContain("uk_sigh_reports_sigh_reporter")
                    .doesNotContain("reporter_device_id)=(")
                    .doesNotContain(device.getPublicId().toString());
            assertThat(sighReportRepository.count()).isOne();
        } finally {
            로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void 저장_무결성_오류는_신고_도메인_예외로_변환한다() {
        // given
        Long sighId = insertSigh();
        Device device = insertDevice();
        addRejectedReasonConstraint();

        try {
            // when
            Throwable throwable = catchThrowable(
                    () -> sighReportService.save(sighId, device.getPublicId(), REJECTED_REASON)
            );

            // then
            assertThat(throwable)
                    .isInstanceOf(SighReportException.class)
                    .hasMessage("신고를 저장하지 못했습니다.")
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);
            assertThat(((SighReportException) throwable).getErrorCode())
                    .isEqualTo(SighReportErrorCode.SIGH_REPORT_SAVE_FAILED);
        } finally {
            removeRejectedReasonConstraint();
        }
    }

    private Long insertSigh() {
        return jdbcClient.sql("""
                        INSERT INTO sighs (request_id, location, nickname, created_at, updated_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            '외로운 회사원',
                            NOW(),
                            NOW()
                        )
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID())
                .param("longitude", SEOUL_CITY_HALL_LONGITUDE)
                .param("latitude", SEOUL_CITY_HALL_LATITUDE)
                .query(Long.class)
                .single();
    }

    private Device insertDevice() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().build());
    }

    private List<SighReportResult> executeConcurrently(
            int requestCount,
            Long sighId,
            UUID devicePublicId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        try (ExecutorService executorService = Executors.newFixedThreadPool(requestCount)) {
            List<Future<SighReportResult>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sighReportService.save(sighId, devicePublicId, 기본_신고_사유());
                }));
            }

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            List<SighReportResult> results = new ArrayList<>();
            for (Future<SighReportResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private void addRejectedReasonConstraint() {
        jdbcClient.sql("""
                        ALTER TABLE sigh_reports
                        ADD CONSTRAINT ck_sigh_reports_reject_test_reason
                        CHECK (reason <> '저장이 거부되는 사유')
                        """)
                .update();
    }

    private String 예외_사슬의_메시지(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
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

    private void removeRejectedReasonConstraint() {
        jdbcClient.sql("""
                        ALTER TABLE sigh_reports
                        DROP CONSTRAINT IF EXISTS ck_sigh_reports_reject_test_reason
                        """)
                .update();
    }
}
