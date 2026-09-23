package com.pheeeew.sigh.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.dto.SighDetailResult;
import com.pheeeew.sigh.application.dto.EmotionListCursor;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.EmotionMapItem;
import com.pheeeew.sigh.application.dto.EmotionMapResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.like.EmotionLikeService;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import com.pheeeew.sigh.infra.metrics.EmotionMetrics;
import com.pheeeew.sigh.infra.metrics.EmotionMetricsAspect;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({EmotionMetrics.class, EmotionMetricsAspect.class, EmotionLikeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionServiceIntegrationTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-09-01T12:00:00.123456789Z");
    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;
    private static final EmotionSearchBounds SEOUL_BOUNDS =
            EmotionSearchBounds.of(126.9000, 37.5000, 127.1000, 37.6000);
    private static final EmotionSearchBounds DATE_LINE_BOUNDS =
            EmotionSearchBounds.of(170.0000, -10.0000, -170.0000, 10.0000);
    private static final EmotionSearchBounds WORLD_BOUNDS =
            EmotionSearchBounds.of(-180.0000, -90.0000, 180.0000, 90.0000);
    private static final UUID REJECTED_REQUEST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID 없는_기기_공개_식별자 =
            UUID.fromString("1f9b0c6a-7d4e-4a1b-9c2d-8e3f5a6b7c8d");

    @Autowired
    private EmotionService emotionService;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private EmotionLikeService emotionLikeService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean(enforceOverride = true)
    private Clock clock;

    private UUID devicePublicId;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(CURRENT_TIME);
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        devicePublicId = deviceRepository.save(기본_기기_빌더().build()).getPublicId();
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM sigh_blocks").update();
        jdbcClient.sql("DELETE FROM device_blocks").update();
        jdbcClient.sql("DELETE FROM sigh_reports").update();
        jdbcClient.sql("DELETE FROM sigh_likes").update();
        emotionRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void 새로운_requestId로_한숨을_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        SighSaveResult result = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.sigh().id()).isPositive();
        assertThat(result.sigh().createdAt()).isNotNull();
        assertThat(result.sigh().createdAt().getNano() % 1_000).isZero();

        Emotion saved = emotionRepository.findById(result.sigh().id()).orElseThrow();
        assertThat(saved.getCreatedAt()).isEqualTo(result.sigh().createdAt());
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getMemo()).isNull();
        assertThat(saved.getNickname())
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(50);
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 이전_버전으로_좋아요_수를_저장하면_먼저_저장된_값을_덮어쓰지_못한다() {
        // given
        SighSaveResult created = emotionService.save(
                UUID.randomUUID(),
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );
        Long sighId = created.sigh().id();
        Emotion first = emotionRepository.findById(sighId).orElseThrow();
        Emotion second = emotionRepository.findById(sighId).orElseThrow();
        first.increaseLikeCount();
        emotionRepository.saveAndFlush(first);

        // when
        second.increaseLikeCount();
        Throwable throwable = catchThrowable(() -> emotionRepository.saveAndFlush(second));

        // then
        assertThat(throwable).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(emotionRepository.findById(sighId).orElseThrow().getLikeCount()).isOne();
    }

    @Test
    void 메모가_있는_한숨을_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        SighSaveResult result = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "  오늘은 힘들었다  ",
                devicePublicId
        );

        // then
        Emotion saved = emotionRepository.findById(result.sigh().id()).orElseThrow();
        assertThat(result.created()).isTrue();
        assertThat(result.sigh().memo()).isEqualTo("오늘은 힘들었다");
        assertThat(result.sigh().nickname()).isEqualTo(saved.getNickname());
        assertThat(saved.getMemo()).isEqualTo("오늘은 힘들었다");
    }

    @Test
    void 인증한_기기를_작성자로_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().build());

        // when
        SighSaveResult result = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "오늘은 조금 지쳤다",
                device.getPublicId()
        );

        // then
        Emotion saved = emotionRepository.findById(result.sigh().id()).orElseThrow();
        assertThat(result.created()).isTrue();
        assertThat(saved.getDeviceId()).isEqualTo(device.getId());
    }

    @Test
    void 작성자를_넘기지_않으면_작성자를_비운_채_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        SighSaveResult result = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );

        // then
        Emotion saved = emotionRepository.findById(result.sigh().id()).orElseThrow();
        assertThat(saved.getDeviceId()).isNull();
    }

    @Test
    void 같은_requestId를_다른_기기가_재전송해도_작성자는_최초_기기로_남는다() {
        // given
        UUID requestId = UUID.randomUUID();
        Device 최초_기기 = deviceRepository.saveAndFlush(기본_기기_빌더().build());
        Device 나중_기기 = deviceRepository.saveAndFlush(기본_기기_빌더().build());
        SighSaveResult first = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "최초 메모",
                최초_기기.getPublicId()
        );

        // when
        SighSaveResult retried = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "재시도 메모",
                나중_기기.getPublicId()
        );

        // then
        Emotion saved = emotionRepository.findById(first.sigh().id()).orElseThrow();
        assertThat(retried.created()).isFalse();
        assertThat(retried.sigh().id()).isEqualTo(first.sigh().id());
        assertThat(saved.getDeviceId()).isEqualTo(최초_기기.getId());
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 등록되지_않은_기기를_작성자로_저장하면_예외가_발생하고_한숨이_생기지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        Throwable throwable = catchThrowable(() -> emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                null,
                없는_기기_공개_식별자
        ));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
        assertThat(emotionRepository.count()).isZero();
    }

    @Test
    void 같은_requestId는_다른_중심으로_재시도해도_기존_한숨을_반환한다() {
        // given
        UUID requestId = UUID.randomUUID();
        SighSaveResult first = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );

        // when
        SighSaveResult retried = emotionService.save(requestId, 129.0756, 35.1796);

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.sigh().id()).isEqualTo(first.sigh().id());
        assertThat(retried.sigh().longitude()).isEqualTo(first.sigh().longitude());
        assertThat(retried.sigh().latitude()).isEqualTo(first.sigh().latitude());
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 같은_requestId는_다른_메모로_재시도해도_최초_메모와_닉네임을_반환한다() {
        // given
        UUID requestId = UUID.randomUUID();
        SighSaveResult first = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "최초 메모",
                devicePublicId
        );

        // when
        SighSaveResult retried = emotionService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "재시도 메모",
                devicePublicId
        );

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.sigh().memo()).isEqualTo("최초 메모");
        assertThat(retried.sigh().nickname()).isEqualTo(first.sigh().nickname());
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 같은_requestId가_동시에_요청되어도_한_건만_저장한다() throws Exception {
        // given
        int requestCount = 6;
        UUID requestId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<SighSaveResult> results = executeConcurrently(requestCount, requestId, ready, start);

        // then
        assertThat(results)
                .extracting(result -> result.sigh().id())
                .containsOnly(results.getFirst().sigh().id());
        assertThat(results)
                .extracting(result -> result.sigh().longitude())
                .containsOnly(results.getFirst().sigh().longitude());
        assertThat(results)
                .extracting(result -> result.sigh().latitude())
                .containsOnly(results.getFirst().sigh().latitude());
        assertThat(results).filteredOn(SighSaveResult::created).hasSize(1);
        assertThat(results).extracting(SighSaveResult::like).containsOnly(SighLikeResult.of(false, 0));
        assertThat(emotionRepository.count()).isOne();
    }

    @Test
    void 등록되지_않은_기기로_조회하면_차단_필터를_끄지_않고_거부한다() {
        // given
        insertSigh(126.9780, 37.5664, "2026-09-01T10:30:00Z");

        // when
        Throwable 지도_조회 = catchThrowable(
                () -> emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.of(없는_기기_공개_식별자))
        );
        Throwable 목록_조회 = catchThrowable(
                () -> emotionService.findFirstListPage(SEOUL_BOUNDS, 없는_기기_공개_식별자)
        );

        // then
        assertThat(지도_조회).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) 지도_조회).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
        assertThat(목록_조회).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) 목록_조회).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-14T06:00:00.123456Z, 2026-08-31T15:00:00Z",
            "2026-09-14T15:00:00Z, 2026-09-01T15:00:00Z"
    })
    void 지도는_한국_날짜로_13일_전_자정부터_조회_시각까지_경계를_포함한다(String currentTime, String startTime) {
        // given
        Instant queriedAt = Instant.parse(currentTime);
        Instant startAt = Instant.parse(startTime);
        given(clock.instant()).willReturn(queriedAt);
        insertSigh(126.9780, 37.5664, startAt.minus(1, ChronoUnit.MICROS).toString());
        Long 시작_경계_한숨 = insertSigh(126.9780, 37.5664, startAt.toString());
        Long 조회_시각_한숨 = insertSigh(126.9780, 37.5664, queriedAt.toString());
        insertSigh(126.9780, 37.5664, queriedAt.plus(1, ChronoUnit.MICROS).toString());

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty());

        // then
        assertThat(result.emotions()).extracting(EmotionMapItem::id)
                .containsExactly(조회_시각_한숨, 시작_경계_한숨);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void 지도_영역에_기간_밖의_한숨만_있으면_빈_결과를_반환한다() {
        // given
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T06:00:00Z"));
        insertSigh(126.9780, 37.5664, "2026-08-31T14:59:59.999999Z");
        insertSigh(126.9780, 37.5664, "2026-09-14T06:00:00.000001Z");

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty());

        // then
        assertThat(result.emotions()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void 삭제된_한숨은_지도_영역_조회에_나오지_않는다() {
        // given
        Long 살아있는_한숨 = insertSigh(126.9780, 37.5664, "2026-09-01T10:30:00Z");
        Long 삭제된_한숨 = insertSigh(126.9790, 37.5665, "2026-09-01T10:31:00Z");
        softDeleteSigh(삭제된_한숨);
        Timer query = meterRegistry.get("pheeeew.sigh.map.query").timer();
        DistributionSummary results = meterRegistry.get("pheeeew.sigh.map.results")
                .tag("truncated", "false").summary();
        long previousQueries = query.count();
        long previousResults = results.count();
        double previousReturnedCount = results.totalAmount();

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty());

        // then
        assertThat(result.emotions())
                .extracting(EmotionMapItem::id)
                .containsExactly(살아있는_한숨);
        assertThat(query.count()).isEqualTo(previousQueries + 1);
        assertThat(results.count()).isEqualTo(previousResults + 1);
        assertThat(results.totalAmount()).isEqualTo(previousReturnedCount + 1);
    }

    @Test
    void 지도_영역_안과_경계의_한숨만_최신순으로_조회한다() {
        // given
        Long insideId = insertSigh(126.9780, 37.5664, "2026-08-31T10:30:00Z");
        insertSigh(127.2000, 37.5664, "2026-08-31T10:31:00Z");
        Long boundaryId = insertSigh(127.1000, 37.6000, "2026-08-31T10:32:00Z");

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty());

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.emotions())
                .extracting(EmotionMapItem::id)
                .containsExactly(boundaryId, insideId);
        assertThat(result.emotions().getFirst().longitude()).isEqualTo(127.1000);
        assertThat(result.emotions().getFirst().latitude()).isEqualTo(37.6000);
        assertThat(result.emotions().getFirst().createdAt())
                .isEqualTo(Instant.parse("2026-08-31T10:32:00Z"));
    }

    @Test
    void 날짜변경선_양쪽_영역의_한숨을_최신순으로_조회한다() {
        // given
        Long 양의_경도_한숨 = insertSigh(170.0000, 0.0000, "2026-08-31T10:30:00Z");
        Long 음의_경도_한숨 = insertSigh(-170.0000, 0.0000, "2026-08-31T10:31:00Z");
        insertSigh(169.9999, 0.0000, "2026-08-31T10:32:00Z");
        insertSigh(-169.9999, 0.0000, "2026-08-31T10:33:00Z");
        insertSigh(175.0000, 10.0001, "2026-08-31T10:34:00Z");

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(DATE_LINE_BOUNDS, Optional.empty());

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.emotions())
                .extracting(EmotionMapItem::id)
                .containsExactly(음의_경도_한숨, 양의_경도_한숨);
    }

    @Test
    void 날짜변경선_양쪽_영역을_합쳐_500건_제한과_잘림_여부를_계산한다() {
        // given
        Long oldestId = insertSigh(175.0000, 0.0000, "2026-08-31T10:29:00Z");
        insertSighs(500, -175.0000, 0.0000);

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(DATE_LINE_BOUNDS, Optional.empty());

        // then
        assertThat(result.truncated()).isTrue();
        assertThat(result.emotions()).hasSize(500);
        assertThat(result.emotions())
                .extracting(EmotionMapItem::id)
                .doesNotContain(oldestId);
    }

    @Test
    void 전_세계_경계의_한숨을_조회한다() {
        // given
        Long 서쪽_경계_한숨 = insertSigh(-180.0000, 0.0000, "2026-08-31T10:30:00Z");
        Long 중앙_한숨 = insertSigh(0.0000, 0.0000, "2026-08-31T10:31:00Z");
        Long 동쪽_경계_한숨 = insertSigh(180.0000, 0.0000, "2026-08-31T10:32:00Z");

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(WORLD_BOUNDS, Optional.empty());

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.emotions())
                .extracting(EmotionMapItem::id)
                .containsExactly(동쪽_경계_한숨, 중앙_한숨, 서쪽_경계_한숨);
    }

    @Test
    void 지도_영역의_기간_내_한숨이_500건이면_기간_밖의_한숨을_제외하고_잘리지_않았음을_알린다() {
        // given
        insertSighs(500, 126.9780, 37.5664);
        insertSigh(126.9780, 37.5664, "2026-08-18T14:59:59.999999Z");
        insertSigh(126.9780, 37.5664, CURRENT_TIME.plusSeconds(1).toString());

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty());

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.emotions()).hasSize(500);
    }

    @Test
    void 지도_영역의_한숨이_500건을_초과하면_최신_500건과_잘림_여부를_반환한다() {
        // given
        Long oldestId = insertSigh(126.9780, 37.5664, "2026-08-31T10:29:00Z");
        insertSighs(500, 126.9780, 37.5664);

        // when
        EmotionMapResult result = emotionService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty());

        // then
        assertThat(result.truncated()).isTrue();
        assertThat(result.emotions()).hasSize(500);
        assertThat(result.emotions())
                .extracting(EmotionMapItem::id)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .doesNotContain(oldestId);
    }

    @Test
    void 목록은_각_페이지의_한숨별_좋아요_수와_조회한_기기의_좋아요_여부를_반환한다() {
        // given
        UUID anotherDevicePublicId = deviceRepository.save(기본_기기_빌더().build()).getPublicId();
        List<Long> ids = new ArrayList<>();
        String createdAt = CURRENT_TIME.minusSeconds(60).toString();
        for (int index = 0; index < 21; index++) {
            ids.add(insertSigh(126.9780, 37.5664, createdAt));
        }
        Long firstPageSighId = ids.getLast();
        Long secondPageSighId = ids.getFirst();
        emotionLikeService.update(firstPageSighId, devicePublicId, true);
        emotionLikeService.update(firstPageSighId, anotherDevicePublicId, true);
        emotionLikeService.update(ids.get(19), anotherDevicePublicId, true);
        emotionLikeService.update(secondPageSighId, devicePublicId, true);

        // when
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);
        SighListResult anotherDevicePage = emotionService.findNextListPage(
                firstPage.nextCursor(), anotherDevicePublicId
        );

        // then
        assertThat(firstPage.items()).extracting(item -> item.sigh().id())
                .containsExactlyElementsOf(ids.subList(1, 21).reversed());
        assertThat(firstPage.items().get(0).like()).isEqualTo(SighLikeResult.of(true, 2));
        assertThat(firstPage.items().get(1).like()).isEqualTo(SighLikeResult.of(false, 1));
        assertThat(firstPage.items().get(2).like()).isEqualTo(SighLikeResult.of(false, 0));
        assertThat(secondPage.items()).singleElement().satisfies(item -> {
            assertThat(item.sigh().id()).isEqualTo(secondPageSighId);
            assertThat(item.like()).isEqualTo(SighLikeResult.of(true, 1));
        });
        assertThat(anotherDevicePage.items()).singleElement().satisfies(item -> {
            assertThat(item.sigh().id()).isEqualTo(secondPageSighId);
            assertThat(item.like()).isEqualTo(SighLikeResult.of(false, 1));
        });
    }

    @Test
    void 다음_페이지의_좋아요_정보는_첫_페이지_이후의_취소를_반영한다() {
        // given
        Long oldestId = insertSigh(126.9780, 37.5664, "2026-08-31T10:29:00Z");
        insertSighs(20, 126.9780, 37.5664);
        emotionLikeService.update(oldestId, devicePublicId, true);
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        emotionLikeService.update(oldestId, devicePublicId, false);

        // when
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);

        // then
        assertThat(secondPage.items()).singleElement().satisfies(item -> {
            assertThat(item.sigh().id()).isEqualTo(oldestId);
            assertThat(item.like()).isEqualTo(SighLikeResult.of(false, 0));
        });
    }

    @Test
    void 등록되지_않은_기기는_바텀시트_첫_페이지를_조회할_수_없다() {
        // given
        UUID unknownDevicePublicId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> emotionService.findFirstListPage(SEOUL_BOUNDS, unknownDevicePublicId))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @ParameterizedTest
    @CsvSource({"2026-09-01T12:00:01Z", "2026-09-15T12:00:00Z"})
    void 기기가_삭제되면_발급된_커서가_있어도_다음_페이지를_조회할_수_없다(String queryTime) {
        // given
        insertSighs(21, 126.9780, 37.5664);
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        deviceRepository.deleteAll();
        given(clock.instant()).willReturn(Instant.parse(queryTime));

        // when / then
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThatThrownBy(() -> emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @Test
    void 바텀시트_목록은_메모와_닉네임을_포함해_20건씩_최신순으로_조회한다() {
        // given
        String createdAt = CURRENT_TIME.minusSeconds(60).toString();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            ids.add(insertSigh(126.9780, 37.5664, createdAt));
        }
        ids.add(insertSighWithDetails(
                126.9780,
                37.5664,
                createdAt,
                "날아가는 고라니",
                "오늘은 조금 지쳤다"
        ));

        // when
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);

        // then
        List<Long> expectedFirstPageIds = new ArrayList<>(ids.subList(1, ids.size()));
        expectedFirstPageIds.sort(Comparator.reverseOrder());

        assertThat(firstPage.items())
                .extracting(SighDetailResult::sigh)
                .extracting(SighResult::id)
                .containsExactlyElementsOf(expectedFirstPageIds);
        assertThat(firstPage.items().getFirst().sigh().nickname()).isEqualTo("날아가는 고라니");
        assertThat(firstPage.items().getFirst().sigh().memo()).isEqualTo("오늘은 조금 지쳤다");
        assertThat(firstPage.items().getFirst().sigh().longitude()).isEqualTo(126.9780);
        assertThat(firstPage.items().getFirst().sigh().latitude()).isEqualTo(37.5664);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        assertThat(secondPage.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sigh().id()).isEqualTo(ids.getFirst());
                    assertThat(item.sigh().memo()).isNull();
                });
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 바텀시트_목록은_날짜변경선_경계를_포함하고_삭제된_한숨을_제외한다() {
        // given
        Long 양의_경도_경계_한숨 = insertSigh(170.0000, 10.0000, "2026-08-31T10:30:00Z");
        Long 음의_경도_경계_한숨 = insertSigh(-170.0000, -10.0000, "2026-08-31T10:31:00Z");
        insertSigh(169.9999, 0.0000, "2026-08-31T10:32:00Z");
        Long 삭제된_한숨 = insertSigh(175.0000, 0.0000, "2026-08-31T10:33:00Z");
        softDeleteSigh(삭제된_한숨);

        // when
        SighListResult result = emotionService.findFirstListPage(DATE_LINE_BOUNDS, devicePublicId);

        // then
        assertThat(result.items())
                .extracting(SighDetailResult::sigh)
                .extracting(SighResult::id)
                .containsExactly(음의_경도_경계_한숨, 양의_경도_경계_한숨);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 바텀시트_목록은_조회_시각을_마이크로초로_잘라_스냅샷을_고정하고_경계의_한숨을_제외한다() {
        // given
        Instant snapshotAt = Instant.parse("2026-09-01T12:00:00.123456Z");
        String beforeSnapshot = snapshotAt.minusSeconds(60).toString();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            ids.add(insertSigh(126.9780, 37.5664, beforeSnapshot));
        }
        Long 스냅샷_경계_한숨 = insertSigh(
                126.9780,
                37.5664,
                snapshotAt.toString()
        );

        // when
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        EmotionListCursor nextCursor = EmotionListCursorCodec.decode(firstPage.nextCursor());
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);

        // then
        List<Long> expectedFirstPageIds = new ArrayList<>(ids.subList(1, ids.size()));
        expectedFirstPageIds.sort(Comparator.reverseOrder());

        assertThat(nextCursor.snapshotAt()).isEqualTo(snapshotAt);
        assertThat(firstPage.items())
                .extracting(item -> item.sigh().id())
                .containsExactlyElementsOf(expectedFirstPageIds)
                .doesNotContain(스냅샷_경계_한숨);
        assertThat(secondPage.items())
                .extracting(item -> item.sigh().id())
                .containsExactly(ids.getFirst())
                .doesNotContain(스냅샷_경계_한숨);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-14T06:00:00.123456Z, 2026-08-31T15:00:00Z",
            "2026-09-14T15:00:00Z, 2026-09-01T15:00:00Z"
    })
    void 목록은_한국_날짜로_13일_전_자정부터_스냅샷_직전까지_페이지로_조회한다(String snapshotTime, String startTime) {
        // given
        Instant snapshotAt = Instant.parse(snapshotTime);
        Instant startAt = Instant.parse(startTime);
        given(clock.instant()).willReturn(snapshotAt);
        insertSigh(126.9780, 37.5664, startAt.minus(1, ChronoUnit.MICROS).toString());
        Long 시작_경계_한숨 = insertSigh(126.9780, 37.5664, startAt.toString());
        List<Long> recentIds = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            recentIds.add(insertSigh(126.9780, 37.5664, snapshotAt.minus(1, ChronoUnit.MICROS).toString()));
        }
        insertSigh(126.9780, 37.5664, snapshotAt.toString());
        insertSigh(126.9780, 37.5664, snapshotAt.plus(1, ChronoUnit.MICROS).toString());

        // when
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);

        // then
        recentIds.sort(Comparator.reverseOrder());
        assertThat(firstPage.items()).extracting(item -> item.sigh().id()).containsExactlyElementsOf(recentIds);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.items()).extracting(item -> item.sigh().id()).containsExactly(시작_경계_한숨);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void 목록_영역에_기간_밖의_한숨만_있으면_다음_페이지_없이_빈_결과를_반환한다() {
        // given
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T06:00:00Z"));
        insertSigh(126.9780, 37.5664, "2026-08-31T14:59:59.999999Z");
        insertSigh(126.9780, 37.5664, "2026-09-14T06:00:00.000001Z");

        // when
        SighListResult result = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-14T15:00:00Z, 2026-09-01T15:00:00Z",
            "2026-09-15T15:00:00Z, 2026-09-02T15:00:00Z"
    })
    void 한국_날짜가_바뀌면_현재_조회_기간과_기존_스냅샷으로_이어서_조회한다(String queryTime, String startTime) {
        // given
        Instant snapshotAt = Instant.parse("2026-09-14T14:59:59.999999Z");
        Instant startAt = Instant.parse(startTime);
        given(clock.instant()).willReturn(snapshotAt);
        insertSigh(126.9780, 37.5664, startAt.minus(1, ChronoUnit.MICROS).toString());
        List<Long> boundaryIds = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            boundaryIds.add(insertSigh(126.9780, 37.5664, startAt.toString()));
        }
        for (int index = 0; index < 20; index++) {
            insertSigh(126.9780, 37.5664, "2026-09-14T14:00:00Z");
        }
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        insertSigh(126.9780, 37.5664, snapshotAt.plus(1, ChronoUnit.MICROS).toString());
        given(clock.instant()).willReturn(Instant.parse(queryTime));

        // when
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);
        SighListResult thirdPage = emotionService.findNextListPage(secondPage.nextCursor(), devicePublicId);

        // then
        boundaryIds.sort(Comparator.reverseOrder());
        assertThat(secondPage.items()).extracting(item -> item.sigh().id())
                .containsExactlyElementsOf(boundaryIds.subList(0, 20));
        assertThat(secondPage.hasNext()).isTrue();
        assertThat(EmotionListCursorCodec.decode(secondPage.nextCursor()).snapshotAt()).isEqualTo(snapshotAt);
        assertThat(thirdPage.items()).extracting(item -> item.sigh().id()).containsExactly(boundaryIds.getLast());
        assertThat(thirdPage.hasNext()).isFalse();
        assertThat(thirdPage.nextCursor()).isNull();
    }

    @Test
    void 자정_이후_남은_한숨이_기간_밖이면_발급된_커서로_빈_결과를_반환한다() {
        // given
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T14:59:59.999999Z"));
        insertSigh(126.9780, 37.5664, "2026-08-31T15:00:00Z");
        for (int index = 0; index < 20; index++) {
            insertSigh(126.9780, 37.5664, "2026-09-14T14:00:00Z");
        }
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T15:00:00Z"));

        // when
        SighListResult result = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);

        // then
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-08-31T15:00:00Z",
            "2026-08-31T14:59:59.999999Z",
            "-1000000000-01-01T00:00:00Z"
    })
    void 스냅샷이_현재_조회_시작_시각_이하이면_빈_결과를_반환한다(String snapshotTime) {
        // given
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T06:00:00Z"));
        insertSigh(126.9780, 37.5664, "2026-08-31T14:00:00Z");
        String encodedCursor = EmotionListCursorCodec.encode(
                EmotionListCursor.initial(SEOUL_BOUNDS, Instant.parse(snapshotTime))
        );

        // when
        SighListResult result = emotionService.findNextListPage(encodedCursor, devicePublicId);

        // then
        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-14T06:00:00.000001Z",
            "+1000000000-12-31T23:59:59.999999999Z"
    })
    void 스냅샷이_현재보다_미래이면_커서를_거부한다(String snapshotTime) {
        // given
        given(clock.instant()).willReturn(Instant.parse("2026-09-14T06:00:00Z"));
        String encodedCursor = EmotionListCursorCodec.encode(
                EmotionListCursor.initial(SEOUL_BOUNDS, Instant.parse(snapshotTime))
        );

        // when / then
        assertThatThrownBy(() -> emotionService.findNextListPage(encodedCursor, devicePublicId))
                .isInstanceOf(SighException.class)
                .extracting(exception -> ((SighException) exception).getErrorCode())
                .isEqualTo(SighErrorCode.SIGH_INVALID_CURSOR);
    }

    @Test
    void 한국_날짜가_같으면_시간이_지나도_첫_페이지의_스냅샷으로_이어서_조회한다() {
        // given
        Instant snapshotAt = Instant.parse("2026-09-14T15:00:00Z");
        given(clock.instant()).willReturn(snapshotAt);
        String createdAt = snapshotAt.minusSeconds(60).toString();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            ids.add(insertSigh(126.9780, 37.5664, createdAt));
        }
        SighListResult firstPage = emotionService.findFirstListPage(SEOUL_BOUNDS, devicePublicId);
        EmotionListCursor cursor = EmotionListCursorCodec.decode(firstPage.nextCursor());
        Long 이후에_등록된_한숨 = insertSigh(
                126.9780,
                37.5664,
                cursor.snapshotAt().plusSeconds(30).toString()
        );
        given(clock.instant()).willReturn(Instant.parse("2026-09-15T06:00:00Z"));

        // when
        SighListResult secondPage = emotionService.findNextListPage(firstPage.nextCursor(), devicePublicId);

        // then
        assertThat(secondPage.items())
                .extracting(SighDetailResult::sigh)
                .extracting(SighResult::id)
                .containsExactly(ids.getFirst())
                .doesNotContain(이후에_등록된_한숨);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void 바텀시트_목록은_최신_500건까지만_페이지로_조회한다() {
        // given
        Long oldestId = insertSigh(126.9780, 37.5664, "2026-08-31T10:29:00Z");
        insertSighs(500, 126.9780, 37.5664);
        Long 기간_이전_한숨 = insertSigh(126.9780, 37.5664, "2026-08-18T14:59:59.999999Z");
        Long 미래_한숨 = insertSigh(126.9780, 37.5664, CURRENT_TIME.plusSeconds(1).toString());

        // when
        List<SighResult> items = findAllListPages(SEOUL_BOUNDS);

        // then
        assertThat(items)
                .hasSize(500)
                .extracting(SighResult::id)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .doesNotContain(oldestId, 기간_이전_한숨, 미래_한숨);
    }

    @Test
    void 저장_무결성_오류는_한숨_도메인_예외로_변환한다() {
        // given
        addRejectedRequestIdConstraint();

        try {
            // when
            Throwable throwable = catchThrowable(() -> emotionService.save(
                    REJECTED_REQUEST_ID,
                    SEOUL_CITY_HALL_LONGITUDE,
                    SEOUL_CITY_HALL_LATITUDE
            ));

            // then
            assertThat(throwable)
                    .isInstanceOf(SighException.class)
                    .hasMessage("한숨을 저장하지 못했습니다.")
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);
            assertThat(((SighException) throwable).getErrorCode())
                    .isEqualTo(SighErrorCode.SIGH_SAVE_FAILED);
        } finally {
            removeRejectedRequestIdConstraint();
        }
    }

    private List<SighSaveResult> executeConcurrently(
            int requestCount,
            UUID requestId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        try (ExecutorService executorService = Executors.newFixedThreadPool(requestCount)) {
            List<Future<SighSaveResult>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return emotionService.save(
                            requestId,
                            SEOUL_CITY_HALL_LONGITUDE,
                            SEOUL_CITY_HALL_LATITUDE,
                            null,
                            devicePublicId
                    );
                }));
            }

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            List<SighSaveResult> results = new ArrayList<>();
            for (Future<SighSaveResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private void addRejectedRequestIdConstraint() {
        jdbcClient.sql("""
                        ALTER TABLE sighs
                        ADD CONSTRAINT ck_sighs_reject_test_request
                        CHECK (request_id <> '00000000-0000-0000-0000-000000000001'::uuid)
                        """)
                .update();
    }

    private void softDeleteSigh(Long sighId) {
        jdbcClient.sql("UPDATE sighs SET deleted_at = NOW() WHERE id = :id")
                .param("id", sighId)
                .update();
    }

    private Long insertSigh(double longitude, double latitude, String createdAt) {
        return jdbcClient.sql("""
                        INSERT INTO sighs (request_id, location, nickname, created_at, updated_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            '외로운 회사원',
                            CAST(:createdAt AS TIMESTAMPTZ),
                            CAST(:createdAt AS TIMESTAMPTZ)
                        )
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID())
                .param("longitude", longitude)
                .param("latitude", latitude)
                .param("createdAt", createdAt)
                .query(Long.class)
                .single();
    }

    private Long insertSighWithDetails(
            double longitude,
            double latitude,
            String createdAt,
            String nickname,
            String memo
    ) {
        return jdbcClient.sql("""
                        INSERT INTO sighs (request_id, location, nickname, memo, created_at, updated_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            :nickname,
                            :memo,
                            CAST(:createdAt AS TIMESTAMPTZ),
                            CAST(:createdAt AS TIMESTAMPTZ)
                        )
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID())
                .param("longitude", longitude)
                .param("latitude", latitude)
                .param("nickname", nickname)
                .param("memo", memo)
                .param("createdAt", createdAt)
                .query(Long.class)
                .single();
    }

    private void insertSighs(int count, double longitude, double latitude) {
        jdbcClient.sql("""
                        INSERT INTO sighs (request_id, location, nickname, created_at, updated_at)
                        SELECT
                            (
                                '00000000-0000-0000-0000-'
                                || LPAD(sequence::text, 12, '0')
                            )::uuid,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            '외로운 회사원',
                            TIMESTAMPTZ '2026-08-31T10:30:00Z'
                                + sequence * INTERVAL '1 microsecond',
                            TIMESTAMPTZ '2026-08-31T10:30:00Z'
                                + sequence * INTERVAL '1 microsecond'
                        FROM generate_series(1, :count) AS sequence
                        """)
                .param("longitude", longitude)
                .param("latitude", latitude)
                .param("count", count)
                .update();
    }

    private List<SighResult> findAllListPages(EmotionSearchBounds bounds) {
        List<SighResult> items = new ArrayList<>();
        SighListResult page = emotionService.findFirstListPage(bounds, devicePublicId);

        for (int pageIndex = 0; pageIndex < 25; pageIndex++) {
            assertThat(page.items()).hasSizeLessThanOrEqualTo(20);
            items.addAll(page.items().stream().map(SighDetailResult::sigh).toList());
            if (!page.hasNext()) {
                return items;
            }
            page = emotionService.findNextListPage(page.nextCursor(), devicePublicId);
        }

        throw new AssertionError("500건 조회는 25페이지 안에 끝나야 합니다.");
    }

    private void removeRejectedRequestIdConstraint() {
        jdbcClient.sql("""
                        ALTER TABLE sighs
                        DROP CONSTRAINT IF EXISTS ck_sighs_reject_test_request
                        """)
                .update();
    }
}
