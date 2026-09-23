package com.pheeeew.report.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.exception.BlockErrorCode;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
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
class DeviceBlockServiceIntegrationTest {

    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;
    private static final Long 없는_한숨_식별자 = Long.MAX_VALUE;
    private static final Long 없는_차단_식별자 = Long.MAX_VALUE;

    private int 등록_순번;

    @Autowired
    private DeviceBlockService deviceBlockService;

    @Autowired
    private DeviceBlockRepository deviceBlockRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        deviceBlockRepository.deleteAll();
        emotionRepository.deleteAll();
        deviceRepository.deleteAll();
        등록_순번 = 0;
    }

    @Test
    void 처음_차단하면_근거가_된_한숨의_닉네임과_메모를_반환한다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 근거_한숨 = 한숨을_저장한다(차단_대상.getId(), "오늘은 조금 지쳤다");

        // when
        BlockSaveResult result = deviceBlockService.save(근거_한숨, 차단자.getPublicId());

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.block().blockId()).isPositive();
        assertThat(result.block().emotionId()).isEqualTo(근거_한숨);
        assertThat(result.block().nickname()).isEqualTo("외로운 회사원");
        assertThat(result.block().memo()).isEqualTo("오늘은 조금 지쳤다");
        assertThat(deviceBlockRepository.count()).isOne();
    }

    @Test
    void 이미_차단한_사용자를_다른_한숨으로_다시_차단하면_최초_근거_한숨을_반환한다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 최초_근거_한숨 = 한숨을_저장한다(차단_대상.getId(), "최초 근거");
        Long 나중에_보낸_한숨 = 한숨을_저장한다(차단_대상.getId(), "나중에 보낸 한숨");
        BlockSaveResult 최초 = deviceBlockService.save(최초_근거_한숨, 차단자.getPublicId());

        // when
        BlockSaveResult 다시 = deviceBlockService.save(나중에_보낸_한숨, 차단자.getPublicId());

        // then
        assertThat(다시.created()).isFalse();
        assertThat(다시.block().blockId()).isEqualTo(최초.block().blockId());
        assertThat(다시.block().emotionId()).isEqualTo(최초_근거_한숨);
        assertThat(다시.block().memo()).isEqualTo("최초 근거");
        assertThat(deviceBlockRepository.count()).isOne();
    }

    @Test
    void 같은_사용자를_동시에_차단해도_한_건만_저장한다() throws Exception {
        // given
        int requestCount = 6;
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 근거_한숨 = 한숨을_저장한다(차단_대상.getId(), null);

        // when
        List<BlockSaveResult> results = 동시에_차단한다(
                requestCount,
                근거_한숨,
                차단자.getPublicId(),
                new CountDownLatch(requestCount),
                new CountDownLatch(1)
        );

        // then
        assertThat(results).filteredOn(BlockSaveResult::created).hasSize(1);
        assertThat(results)
                .extracting(result -> result.block().blockId())
                .containsOnly(results.getFirst().block().blockId());
        assertThat(deviceBlockRepository.count()).isOne();
    }

    @Test
    void 중복_차단의_제약_위반은_기기_식별자를_로그에_남기지_않는다() throws Exception {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 근거_한숨 = 한숨을_저장한다(차단_대상.getId(), null);
        ListAppender<ILoggingEvent> appender = 로그_수집을_시작한다();

        try {
            // when
            동시에_차단한다(
                    6,
                    근거_한숨,
                    차단자.getPublicId(),
                    new CountDownLatch(6),
                    new CountDownLatch(1)
            );

            // then
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).contains("device_blocks");
            assertThat(남은_로그)
                    .doesNotContain("uk_device_blocks_blocker_blocked")
                    .doesNotContain("blocker_device_id)=(")
                    .doesNotContain("blocked_device_id)=(")
                    .doesNotContain(차단자.getPublicId().toString())
                    .doesNotContain(차단_대상.getPublicId().toString());
            assertThat(deviceBlockRepository.count()).isOne();
        } finally {
            로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void 자기_자신을_차단하면_예외가_발생하고_행이_생기지_않는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 내가_쓴_한숨 = 한숨을_저장한다(차단자.getId(), null);

        // when
        Throwable throwable =
                catchThrowable(() -> deviceBlockService.save(내가_쓴_한숨, 차단자.getPublicId()));

        // then
        assertThat(throwable)
                .isInstanceOf(BlockException.class)
                .hasMessage("자기 자신은 차단할 수 없습니다.");
        assertThat(((BlockException) throwable).getErrorCode())
                .isEqualTo(BlockErrorCode.BLOCK_SELF_NOT_ALLOWED);
        assertThat(deviceBlockRepository.count()).isZero();
    }

    @Test
    void 자기_자신을_차단하는_행은_DB_제약이_거부한다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 내가_쓴_한숨 = 한숨을_저장한다(차단자.getId(), null);

        // when
        Throwable throwable = catchThrowable(() -> 차단_행을_직접_넣는다(
                차단자.getId(),
                차단자.getId(),
                내가_쓴_한숨
        ));

        // then
        assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(예외_사슬의_메시지(throwable)).contains("ck_device_blocks_not_self");
        assertThat(deviceBlockRepository.count()).isZero();
    }

    @Test
    void 작성자를_모르는_한숨으로_사용자를_차단하면_예외가_발생한다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 작성자를_모르는_한숨 = 한숨을_저장한다(null, null);

        // when
        Throwable throwable =
                catchThrowable(() -> deviceBlockService.save(작성자를_모르는_한숨, 차단자.getPublicId()));

        // then
        assertThat(throwable).isInstanceOf(BlockException.class);
        assertThat(((BlockException) throwable).getErrorCode())
                .isEqualTo(BlockErrorCode.BLOCK_AUTHOR_UNKNOWN);
        assertThat(deviceBlockRepository.count()).isZero();
    }

    @Test
    void 존재하지_않는_한숨으로_사용자를_차단하면_예외가_발생한다() {
        // given
        Device 차단자 = 기기를_저장한다();

        // when
        Throwable throwable =
                catchThrowable(() -> deviceBlockService.save(없는_한숨_식별자, 차단자.getPublicId()));

        // then
        assertThat(throwable).isInstanceOf(EmotionException.class);
        assertThat(((EmotionException) throwable).getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND);
        assertThat(deviceBlockRepository.count()).isZero();
    }

    @Test
    void 남의_차단_식별자로_해제해도_그_차단이_남는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 근거_한숨 = 한숨을_저장한다(차단_대상.getId(), null);
        Long 차단_식별자 = deviceBlockService.save(근거_한숨, 차단자.getPublicId()).block().blockId();

        // when
        deviceBlockService.delete(차단_식별자, 남.getPublicId());

        // then
        assertThat(deviceBlockRepository.findById(차단_식별자)).isPresent();
        assertThat(deviceBlockRepository.count()).isOne();
    }

    @Test
    void 차단하지_않은_식별자로_해제해도_오류가_나지_않는다() {
        // given
        Device 차단자 = 기기를_저장한다();

        // when
        Throwable throwable =
                catchThrowable(() -> deviceBlockService.delete(없는_차단_식별자, 차단자.getPublicId()));

        // then
        assertThat(throwable).isNull();
        assertThat(deviceBlockRepository.count()).isZero();
    }

    @Test
    void 차단_목록은_최근_차단부터_반환하고_내_차단만_담는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        Device 먼저_차단한_기기 = 기기를_저장한다();
        Device 나중에_차단한_기기 = 기기를_저장한다();
        Long 먼저_차단한_근거_한숨 = 한숨을_저장한다(먼저_차단한_기기.getId(), "먼저 차단");
        Long 나중에_차단한_근거_한숨 = 한숨을_저장한다(나중에_차단한_기기.getId(), "나중에 차단");
        deviceBlockService.save(먼저_차단한_근거_한숨, 차단자.getPublicId());
        deviceBlockService.save(나중에_차단한_근거_한숨, 차단자.getPublicId());
        deviceBlockService.save(먼저_차단한_근거_한숨, 남.getPublicId());

        // when
        BlockListResult result = deviceBlockService.findAll(차단자.getPublicId(), null);

        // then
        assertThat(result.items())
                .extracting(BlockResult::emotionId)
                .containsExactly(나중에_차단한_근거_한숨, 먼저_차단한_근거_한숨);
        assertThat(result.items().getFirst().memo()).isEqualTo("나중에 차단");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 사용할_수_없는_커서로_차단_목록을_조회하면_예외가_발생한다() {
        // given
        Device 차단자 = 기기를_저장한다();

        // when
        Throwable throwable =
                catchThrowable(() -> deviceBlockService.findAll(차단자.getPublicId(), "not-a-cursor"));

        // then
        assertThat(throwable).isInstanceOf(BlockException.class);
        assertThat(((BlockException) throwable).getErrorCode())
                .isEqualTo(BlockErrorCode.BLOCK_INVALID_CURSOR);
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().build());
    }

    private Long 한숨을_저장한다(Long deviceId, String memo) {
        등록_순번++;
        return jdbcClient.sql("""
                        INSERT INTO emotions (request_id, location, nickname, memo, device_id, created_at, updated_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            '외로운 회사원',
                            :memo,
                            :deviceId,
                            TIMESTAMPTZ '2026-09-01T10:00:00Z' + :sequence * INTERVAL '1 minute',
                            TIMESTAMPTZ '2026-09-01T10:00:00Z' + :sequence * INTERVAL '1 minute'
                        )
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID())
                .param("longitude", SEOUL_CITY_HALL_LONGITUDE)
                .param("latitude", SEOUL_CITY_HALL_LATITUDE)
                .param("memo", memo)
                .param("deviceId", deviceId)
                .param("sequence", 등록_순번)
                .query(Long.class)
                .single();
    }

    private void 차단_행을_직접_넣는다(Long blockerDeviceId, Long blockedDeviceId, Long originEmotionId) {
        jdbcClient.sql("""
                        INSERT INTO device_blocks
                            (blocker_device_id, blocked_device_id, origin_emotion_id, created_at, updated_at)
                        VALUES (:blockerDeviceId, :blockedDeviceId, :originEmotionId, NOW(), NOW())
                        """)
                .param("blockerDeviceId", blockerDeviceId)
                .param("blockedDeviceId", blockedDeviceId)
                .param("originEmotionId", originEmotionId)
                .update();
    }

    private List<BlockSaveResult> 동시에_차단한다(
            int requestCount,
            Long emotionId,
            UUID devicePublicId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        try (ExecutorService executorService = Executors.newFixedThreadPool(requestCount)) {
            List<Future<BlockSaveResult>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return deviceBlockService.save(emotionId, devicePublicId);
                }));
            }

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            List<BlockSaveResult> results = new ArrayList<>();
            for (Future<BlockSaveResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
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
}
