package com.pheeeew.sigh.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.sigh.application.dto.SighListCursor;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.domain.repository.projection.SighListProjection;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SighMapMetrics.class, SighMapMetricsAspect.class, SighServiceIntegrationTest.MetricsConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SighServiceIntegrationTest {

    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;
    private static final SighSearchBounds SEOUL_BOUNDS =
            SighSearchBounds.of(126.9000, 37.5000, 127.1000, 37.6000);
    private static final SighSearchBounds DATE_LINE_BOUNDS =
            SighSearchBounds.of(170.0000, -10.0000, -170.0000, 10.0000);
    private static final SighSearchBounds WORLD_BOUNDS =
            SighSearchBounds.of(-180.0000, -90.0000, 180.0000, 90.0000);
    private static final UUID REJECTED_REQUEST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private SighService sighService;

    @Autowired
    private SighRepository sighRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM sigh_reports").update();
        sighRepository.deleteAll();
    }

    @Test
    void 새로운_requestId로_한숨을_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        SighSaveResult result = sighService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.sigh().id()).isPositive();
        assertThat(result.sigh().createdAt()).isNotNull();
        assertThat(result.sigh().createdAt().getNano() % 1_000).isZero();

        Sigh saved = sighRepository.findById(result.sigh().id()).orElseThrow();
        assertThat(saved.getCreatedAt()).isEqualTo(result.sigh().createdAt());
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getMemo()).isNull();
        assertThat(saved.getNickname())
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(50);
        assertThat(sighRepository.count()).isOne();
    }

    @Test
    void 메모가_있는_한숨을_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        SighSaveResult result = sighService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "  오늘은 힘들었다  "
        );

        // then
        Sigh saved = sighRepository.findById(result.sigh().id()).orElseThrow();
        assertThat(result.created()).isTrue();
        assertThat(result.sigh().memo()).isEqualTo("오늘은 힘들었다");
        assertThat(result.sigh().nickname()).isEqualTo(saved.getNickname());
        assertThat(saved.getMemo()).isEqualTo("오늘은 힘들었다");
    }

    @Test
    void 같은_requestId는_다른_중심으로_재시도해도_기존_한숨을_반환한다() {
        // given
        UUID requestId = UUID.randomUUID();
        SighSaveResult first = sighService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );

        // when
        SighSaveResult retried = sighService.save(requestId, 129.0756, 35.1796);

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.sigh().id()).isEqualTo(first.sigh().id());
        assertThat(retried.sigh().longitude()).isEqualTo(first.sigh().longitude());
        assertThat(retried.sigh().latitude()).isEqualTo(first.sigh().latitude());
        assertThat(sighRepository.count()).isOne();
    }

    @Test
    void 같은_requestId는_다른_메모로_재시도해도_최초_메모와_닉네임을_반환한다() {
        // given
        UUID requestId = UUID.randomUUID();
        SighSaveResult first = sighService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "최초 메모"
        );

        // when
        SighSaveResult retried = sighService.save(
                requestId,
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "재시도 메모"
        );

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.sigh().memo()).isEqualTo("최초 메모");
        assertThat(retried.sigh().nickname()).isEqualTo(first.sigh().nickname());
        assertThat(sighRepository.count()).isOne();
    }

    @Test
    void ID로_한숨_상세를_조회한다() {
        // given
        SighSaveResult saved = sighService.save(
                UUID.randomUUID(),
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE,
                "오늘은 조금 지쳤다"
        );

        // when
        SighResult result = sighService.findById(saved.sigh().id());

        // then
        assertThat(result.id()).isEqualTo(saved.sigh().id());
        assertThat(result.longitude()).isEqualTo(saved.sigh().longitude());
        assertThat(result.latitude()).isEqualTo(saved.sigh().latitude());
        assertThat(result.createdAt()).isEqualTo(saved.sigh().createdAt());
        assertThat(result.memo()).isEqualTo("오늘은 조금 지쳤다");
        assertThat(result.nickname()).isEqualTo(saved.sigh().nickname());
    }

    @Test
    void 존재하지_않는_ID로_한숨_상세를_조회하면_예외가_발생한다() {
        // given
        Long nonexistentId = Long.MAX_VALUE;

        // when
        Throwable throwable = catchThrowable(() -> sighService.findById(nonexistentId));

        // then
        assertThat(throwable)
                .isInstanceOf(SighException.class)
                .hasMessage("한숨을 찾을 수 없습니다.");
        assertThat(((SighException) throwable).getErrorCode())
                .isEqualTo(SighErrorCode.SIGH_NOT_FOUND);
    }

    @Test
    void 삭제된_한숨_상세를_조회하면_예외가_발생한다() {
        // given
        SighSaveResult saved = sighService.save(
                UUID.randomUUID(),
                SEOUL_CITY_HALL_LONGITUDE,
                SEOUL_CITY_HALL_LATITUDE
        );
        softDeleteSigh(saved.sigh().id());

        // when
        Throwable throwable = catchThrowable(() -> sighService.findById(saved.sigh().id()));

        // then
        assertThat(throwable)
                .isInstanceOf(SighException.class)
                .hasMessage("한숨을 찾을 수 없습니다.");
        assertThat(((SighException) throwable).getErrorCode())
                .isEqualTo(SighErrorCode.SIGH_NOT_FOUND);
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
        assertThat(sighRepository.count()).isOne();
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
        SighMapResult result = sighService.findAllWithinBounds(SEOUL_BOUNDS);

        // then
        assertThat(result.sighs())
                .extracting(SighMapItem::id)
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
        SighMapResult result = sighService.findAllWithinBounds(SEOUL_BOUNDS);

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.sighs())
                .extracting(SighMapItem::id)
                .containsExactly(boundaryId, insideId);
        assertThat(result.sighs().getFirst().longitude()).isEqualTo(127.1000);
        assertThat(result.sighs().getFirst().latitude()).isEqualTo(37.6000);
        assertThat(result.sighs().getFirst().createdAt())
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
        SighMapResult result = sighService.findAllWithinBounds(DATE_LINE_BOUNDS);

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.sighs())
                .extracting(SighMapItem::id)
                .containsExactly(음의_경도_한숨, 양의_경도_한숨);
    }

    @Test
    void 날짜변경선_양쪽_영역을_합쳐_500건_제한과_잘림_여부를_계산한다() {
        // given
        Long oldestId = insertSigh(175.0000, 0.0000, "2026-08-31T10:29:00Z");
        insertSighs(500, -175.0000, 0.0000);

        // when
        SighMapResult result = sighService.findAllWithinBounds(DATE_LINE_BOUNDS);

        // then
        assertThat(result.truncated()).isTrue();
        assertThat(result.sighs()).hasSize(500);
        assertThat(result.sighs())
                .extracting(SighMapItem::id)
                .doesNotContain(oldestId);
    }

    @Test
    void 전_세계_경계의_한숨을_조회한다() {
        // given
        Long 서쪽_경계_한숨 = insertSigh(-180.0000, 0.0000, "2026-08-31T10:30:00Z");
        Long 중앙_한숨 = insertSigh(0.0000, 0.0000, "2026-08-31T10:31:00Z");
        Long 동쪽_경계_한숨 = insertSigh(180.0000, 0.0000, "2026-08-31T10:32:00Z");

        // when
        SighMapResult result = sighService.findAllWithinBounds(WORLD_BOUNDS);

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.sighs())
                .extracting(SighMapItem::id)
                .containsExactly(동쪽_경계_한숨, 중앙_한숨, 서쪽_경계_한숨);
    }

    @Test
    void 지도_영역의_한숨이_500건이면_모두_반환하고_잘리지_않았음을_알린다() {
        // given
        insertSighs(500, 126.9780, 37.5664);

        // when
        SighMapResult result = sighService.findAllWithinBounds(SEOUL_BOUNDS);

        // then
        assertThat(result.truncated()).isFalse();
        assertThat(result.sighs()).hasSize(500);
    }

    @Test
    void 지도_영역의_한숨이_500건을_초과하면_최신_500건과_잘림_여부를_반환한다() {
        // given
        Long oldestId = insertSigh(126.9780, 37.5664, "2026-08-31T10:29:00Z");
        insertSighs(500, 126.9780, 37.5664);

        // when
        SighMapResult result = sighService.findAllWithinBounds(SEOUL_BOUNDS);

        // then
        assertThat(result.truncated()).isTrue();
        assertThat(result.sighs()).hasSize(500);
        assertThat(result.sighs())
                .extracting(SighMapItem::id)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .doesNotContain(oldestId);
    }

    @Test
    void 바텀시트_목록은_메모와_닉네임을_포함해_20건씩_최신순으로_조회한다() {
        // given
        String createdAt = Instant.now().minusSeconds(60).toString();
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
        SighListResult firstPage = sighService.findFirstListPage(SEOUL_BOUNDS);
        SighListResult secondPage = sighService.findNextListPage(firstPage.nextCursor());

        // then
        List<Long> expectedFirstPageIds = new ArrayList<>(ids.subList(1, ids.size()));
        expectedFirstPageIds.sort(Comparator.reverseOrder());

        assertThat(firstPage.items())
                .extracting(SighResult::id)
                .containsExactlyElementsOf(expectedFirstPageIds);
        assertThat(firstPage.items().getFirst().nickname()).isEqualTo("날아가는 고라니");
        assertThat(firstPage.items().getFirst().memo()).isEqualTo("오늘은 조금 지쳤다");
        assertThat(firstPage.items().getFirst().longitude()).isEqualTo(126.9780);
        assertThat(firstPage.items().getFirst().latitude()).isEqualTo(37.5664);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        assertThat(secondPage.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(ids.getFirst());
                    assertThat(item.memo()).isNull();
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
        SighListResult result = sighService.findFirstListPage(DATE_LINE_BOUNDS);

        // then
        assertThat(result.items())
                .extracting(SighResult::id)
                .containsExactly(음의_경도_경계_한숨, 양의_경도_경계_한숨);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 바텀시트_목록은_첫_조회_전에_스냅샷_시각에_등록된_한숨도_제외한다() {
        // given
        Instant snapshotAt = Instant.parse("2026-09-01T12:00:00Z");
        String beforeSnapshot = snapshotAt.minusSeconds(60).toString();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            ids.add(insertSigh(126.9780, 37.5664, beforeSnapshot));
        }
        SighListCursor initialCursor = SighListCursor.initial(SEOUL_BOUNDS, snapshotAt);
        Long 스냅샷_경계_한숨 = insertSigh(
                126.9780,
                37.5664,
                snapshotAt.toString()
        );

        // when
        List<SighListProjection> firstQuery = findListProjections(initialCursor);
        List<SighListProjection> firstPage = firstQuery.subList(0, 20);
        SighListProjection lastProjection = firstPage.getLast();
        SighListCursor nextCursor = initialCursor.next(
                lastProjection.getCreatedAt(),
                lastProjection.getId()
        );
        List<SighListProjection> secondPage = findListProjections(nextCursor);

        // then
        List<Long> expectedFirstPageIds = new ArrayList<>(ids.subList(1, ids.size()));
        expectedFirstPageIds.sort(Comparator.reverseOrder());

        assertThat(firstPage)
                .extracting(SighListProjection::getId)
                .containsExactlyElementsOf(expectedFirstPageIds)
                .doesNotContain(스냅샷_경계_한숨);
        assertThat(secondPage)
                .extracting(SighListProjection::getId)
                .containsExactly(ids.getFirst())
                .doesNotContain(스냅샷_경계_한숨);
    }

    @Test
    void 첫_페이지_이후에_등록된_한숨은_현재_바텀시트_목록에_포함하지_않는다() {
        // given
        String createdAt = Instant.now().minusSeconds(60).toString();
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            ids.add(insertSigh(126.9780, 37.5664, createdAt));
        }
        SighListResult firstPage = sighService.findFirstListPage(SEOUL_BOUNDS);
        SighListCursor cursor = SighListCursorCodec.decode(firstPage.nextCursor());
        Long 이후에_등록된_한숨 = insertSigh(
                126.9780,
                37.5664,
                cursor.snapshotAt().toString()
        );

        // when
        SighListResult secondPage = sighService.findNextListPage(firstPage.nextCursor());

        // then
        assertThat(secondPage.items())
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

        // when
        List<SighResult> items = findAllListPages(SEOUL_BOUNDS);

        // then
        assertThat(items)
                .hasSize(500)
                .extracting(SighResult::id)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .doesNotContain(oldestId);
    }

    @Test
    void 저장_무결성_오류는_한숨_도메인_예외로_변환한다() {
        // given
        addRejectedRequestIdConstraint();

        try {
            // when
            Throwable throwable = catchThrowable(() -> sighService.save(
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
                    return sighService.save(
                            requestId,
                            SEOUL_CITY_HALL_LONGITUDE,
                            SEOUL_CITY_HALL_LATITUDE
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

    private List<SighResult> findAllListPages(SighSearchBounds bounds) {
        List<SighResult> items = new ArrayList<>();
        SighListResult page = sighService.findFirstListPage(bounds);

        for (int pageIndex = 0; pageIndex < 25; pageIndex++) {
            assertThat(page.items()).hasSizeLessThanOrEqualTo(20);
            items.addAll(page.items());
            if (!page.hasNext()) {
                return items;
            }
            page = sighService.findNextListPage(page.nextCursor());
        }

        throw new AssertionError("500건 조회는 25페이지 안에 끝나야 합니다.");
    }

    private List<SighListProjection> findListProjections(SighListCursor cursor) {
        SighSearchBounds bounds = cursor.bounds();
        return sighRepository.findListWithinBounds(
                bounds.minLongitude(),
                bounds.minLatitude(),
                bounds.maxLongitude(),
                bounds.maxLatitude(),
                cursor.snapshotAt(),
                cursor.lastItemCreatedAt(),
                cursor.lastId(),
                500,
                21
        );
    }

    private void removeRejectedRequestIdConstraint() {
        jdbcClient.sql("""
                        ALTER TABLE sighs
                        DROP CONSTRAINT IF EXISTS ck_sighs_reject_test_request
                        """)
                .update();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MetricsConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
