package com.pheeeew.pressevent.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.domain.EmotionState.ANGRY;
import static com.pheeeew.emotion.domain.EmotionState.DISCOURAGED;
import static com.pheeeew.emotion.domain.EmotionState.EXHAUSTED;
import static com.pheeeew.emotion.domain.EmotionState.FRUSTRATED;
import static com.pheeeew.emotion.domain.EmotionState.IRRITATED;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.EVENT_NOT_FOUND;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.EVENT_NOT_OPEN;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.EVENT_RATE_LIMITED;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.INVALID_CURSOR;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.REQUEST_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.common.exception.ErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.application.query.EmotionPressQueryService;
import com.pheeeew.emotion.application.query.EmotionPressRankingService;
import com.pheeeew.pressevent.application.PressEventRankingResult.Standing;
import com.pheeeew.pressevent.domain.PressEventCounts;
import com.pheeeew.pressevent.infra.PressEventRepository;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.v2.domain.PressParticipant;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({PressEventService.class, PressEventRepository.class, PressSnapshotRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressEventServiceIntegrationTest {

    @Autowired
    private PressEventService eventService;

    @Autowired
    private PressRaceService pressRaceService;

    @Autowired
    private EmotionPressService emotionPressService;

    @Autowired
    private EmotionPressQueryService pressQueryService;

    @Autowired
    private EmotionPressRankingService pressRankingService;

    @Autowired
    private PressSnapshotRepository snapshots;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private JdbcClient jdbc;

    private UUID eventId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation")
                .param("generation", pressRaceService.serverGeneration()).update();
        eventId = 이벤트를_등록한다();
        deviceId = 기기를_등록한다("히유");
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM press_event_attempts").update();
        jdbc.sql("DELETE FROM press_event_bests").update();
        jdbc.sql("DELETE FROM press_events").update();
        jdbc.sql("DELETE FROM device_daily_presses").update();
        devices.deleteAllInBatch();
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL").update();
    }

    @Test
    void 도전마다_프레스를_가산하고_이벤트_최고_점수는_갱신하거나_유지한다() {
        // given
        emotionPressService.press(deviceId, Map.of(ANGRY, 2));

        // when
        PressEventAttemptResult 최초 = 제출한다(deviceId, 120);
        PressEventAttemptResult 재도전 = eventService.submit(eventId, deviceId, UUID.randomUUID(),
                PressEventCounts.from(Map.of(ANGRY, 20, EXHAUSTED, 12, DISCOURAGED, 0, FRUSTRATED, 0, IRRITATED, 0)));
        PressEventAttemptResult 갱신 = 제출한다(deviceId, 130);

        // then
        assertThat(최초.bestScore()).isEqualTo(120);
        assertThat(재도전.total()).isEqualTo(32);
        assertThat(재도전.bestScore()).isEqualTo(120);
        assertThat(갱신.bestScore()).isEqualTo(130);
        assertThat(순위를_조회한다(deviceId, null, 20).me().bestScore()).isEqualTo(130);

        var 개인 = pressQueryService.findMyDailyPresses(deviceId, 0);
        assertThat(개인.counts()).containsEntry(ANGRY, 272L).containsEntry(EXHAUSTED, 12L);
        assertThat(pressQueryService.findDailyTotal(0).total()).isEqualTo(284);
        assertThat(pressRankingService.findDailyPressRanking(deviceId, 개인.pressDate()).me().pressCount())
                .isEqualTo(284);
        assertThat(snapshots.findParticipants(개인.pressDate())).extracting(PressParticipant::score).containsExactly(284L);
    }

    @Test
    void 같은_도전이_동시에_제출되어도_한_번만_집계하고_응답을_재전달한다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        Callable<PressEventAttemptResult> submit = () ->
                eventService.submit(eventId, deviceId, requestId, 도전_횟수(132));

        // when
        List<PressEventAttemptResult> 결과 = 동시에_제출한다(submit, submit);

        // then
        assertThat(결과).extracting(PressEventAttemptResult::replayed).containsExactlyInAnyOrder(false, true);
        assertThat(결과.getFirst()).usingRecursiveComparison().ignoringFields("replayed").isEqualTo(결과.getLast());
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(132);
        assertThatThrownBy(() -> eventService.submit(eventId, deviceId, requestId,
                PressEventCounts.from(Map.of(ANGRY, 131, EXHAUSTED, 1, DISCOURAGED, 0, FRUSTRATED, 0, IRRITATED, 0))))
                .isInstanceOfSatisfying(PheeeewException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(REQUEST_CONFLICT));
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(132);
    }

    @Test
    void 같은_기기의_서로_다른_동시_도전은_모두_가산하고_최고_점수만_남긴다() throws Exception {
        // when
        var 결과 = 동시에_제출한다(() -> 제출한다(deviceId, 125), () -> 제출한다(deviceId, 140));

        // then
        assertThat(결과).extracting(PressEventAttemptResult::total).containsExactlyInAnyOrder(125, 140);
        assertThat(결과).extracting(PressEventAttemptResult::replayed).containsOnly(false);
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(265);
        assertThat(순위를_조회한다(deviceId, null, 20).items()).extracting(Standing::bestScore).containsExactly(140);
    }

    @Test
    void 도전_저장에_실패하면_최고_기록과_프레스가_롤백되고_같은_키로_재시도할_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        jdbc.sql("ALTER TABLE press_event_attempts ADD CONSTRAINT test_reject_attempt CHECK (total <> 123)").update();
        try {
            // when & then
            assertThatThrownBy(() -> eventService.submit(eventId, deviceId, requestId, 도전_횟수(123)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(순위를_조회한다(deviceId, null, 20).me()).isNull();
            var 개인 = pressQueryService.findMyDailyPresses(deviceId, 0);
            assertThat(개인.total()).isZero();
            assertThat(snapshots.findParticipants(개인.pressDate())).isEmpty();
        } finally {
            jdbc.sql("ALTER TABLE press_event_attempts DROP CONSTRAINT test_reject_attempt").update();
        }
        assertThat(eventService.submit(eventId, deviceId, requestId, 도전_횟수(123)).replayed()).isFalse();
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(123);
    }

    @Test
    void 활성_프레스_서버가_아니면_새_도전을_반영하지_않는다() {
        // given
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL").update();

        // when & then
        assertThatThrownBy(() -> 제출한다(deviceId, 100))
                .isInstanceOfSatisfying(PressRaceException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SERVER_REPLACED"));
        assertThat(순위를_조회한다(deviceId, null, 20).items()).isEmpty();
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isZero();
    }

    @Test
    void 이벤트_기간을_검증하고_종료_후에는_순위와_성공한_도전_응답을_제공한다() {
        // given
        UUID requestId = UUID.randomUUID();
        assertThatThrownBy(() -> eventService.submit(UUID.randomUUID(), deviceId, requestId, 도전_횟수(9)))
                .isInstanceOfSatisfying(PheeeewException.class, exception -> 오류를_확인한다(exception, EVENT_NOT_FOUND));
        UUID 예정_이벤트 = 이벤트를_등록한다("1 day", "2 days");
        assertThatThrownBy(() -> eventService.submit(예정_이벤트, deviceId, requestId, 도전_횟수(9)))
                .isInstanceOfSatisfying(PheeeewException.class, exception -> 오류를_확인한다(exception, EVENT_NOT_OPEN));
        var 최초 = eventService.submit(eventId, deviceId, requestId, 도전_횟수(9));
        제출한다(deviceId, 12);

        // when
        jdbc.sql("UPDATE press_events SET ends_at = clock_timestamp() WHERE id = :id").param("id", eventId).update();

        // then
        assertThatThrownBy(() -> 제출한다(deviceId, 10))
                .isInstanceOfSatisfying(PheeeewException.class, exception -> 오류를_확인한다(exception, EVENT_NOT_OPEN));
        var 재시도 = eventService.submit(eventId, deviceId, requestId, 도전_횟수(9));
        assertThat(재시도.replayed()).isTrue();
        assertThat(재시도).usingRecursiveComparison().ignoringFields("replayed").isEqualTo(최초);
        assertThat(순위를_조회한다(deviceId, null, 20).me().bestScore()).isEqualTo(12);
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(21);
    }

    @Test
    void 동점은_공동_순위이며_최고점_달성순으로_페이지를_넘겨도_내_순위를_받는다() {
        // given
        UUID 다른_기기 = 기기를_등록한다("그린");
        UUID 낮은_점수_기기 = 기기를_등록한다("블루");
        var 빈_순위 = 순위를_조회한다(낮은_점수_기기, null, 1);
        assertThat(빈_순위.leader()).isNull();
        assertThat(빈_순위.items()).isEmpty();
        assertThat(빈_순위.me()).isNull();
        제출한다(deviceId, 160);
        제출한다(다른_기기, 160);
        제출한다(deviceId, 160);
        var 미참여 = 순위를_조회한다(낮은_점수_기기, null, 1);
        assertThat(미참여.leader()).isNotNull();
        assertThat(미참여.me()).isNull();
        var 제출 = 제출한다(낮은_점수_기기, 80);

        // when
        var 첫_페이지 = 순위를_조회한다(낮은_점수_기기, null, 1);
        var 두번째_페이지 = 순위를_조회한다(낮은_점수_기기, 첫_페이지.nextCursor(), 1);
        var 마지막_페이지 = 순위를_조회한다(낮은_점수_기기, 두번째_페이지.nextCursor(), 1);

        // then
        assertThat(첫_페이지.leader().nickname()).isEqualTo("히유");
        assertThat(첫_페이지.leader().publicId()).isNotEqualTo(deviceId);
        assertThat(첫_페이지.items()).extracting(Standing::nickname, Standing::rank, Standing::mine)
                .containsExactly(tuple("히유", 1L, false));
        assertThat(두번째_페이지.items()).extracting(Standing::nickname, Standing::rank, Standing::mine)
                .containsExactly(tuple("그린", 1L, false));
        assertThat(마지막_페이지.items()).extracting(Standing::nickname, Standing::rank, Standing::mine)
                .containsExactly(tuple("블루", 3L, true));
        assertThat(첫_페이지.me().rank()).isEqualTo(제출.rank()).isEqualTo(3);
        assertThat(첫_페이지.me().bestScore()).isEqualTo(80);
        assertThat(마지막_페이지.nextCursor()).isNull();
        UUID 다른_이벤트 = 이벤트를_등록한다();
        assertThatThrownBy(() -> eventService.findRanking(다른_이벤트, deviceId, 첫_페이지.nextCursor(), 1))
                .isInstanceOfSatisfying(PheeeewException.class, exception -> 오류를_확인한다(exception, INVALID_CURSOR));
    }

    @Test
    void 점수와_달성_시각이_같으면_공개_UUID_순으로_리더와_페이지를_정렬한다() {
        // given
        UUID 다른_기기 = 기기를_등록한다("그린");
        제출한다(deviceId, 100);
        제출한다(다른_기기, 100);
        List<UUID> 공개_ID = 순위를_조회한다(deviceId, null, 2).items().stream()
                .map(Standing::publicId).sorted(Comparator.comparing(UUID::toString)).toList();
        jdbc.sql("UPDATE press_event_bests SET achieved_at = statement_timestamp() WHERE event_id = :id")
                .param("id", eventId).update();

        // when
        var 첫_페이지 = 순위를_조회한다(deviceId, null, 1);
        var 다음_페이지 = 순위를_조회한다(deviceId, 첫_페이지.nextCursor(), 1);

        // then
        assertThat(첫_페이지.leader().publicId()).isEqualTo(공개_ID.getFirst());
        assertThat(첫_페이지.items()).extracting(Standing::publicId).containsExactly(공개_ID.getFirst());
        assertThat(다음_페이지.items()).extracting(Standing::publicId).containsExactly(공개_ID.getLast());
        assertThat(다음_페이지.nextCursor()).isNull();
    }

    @Test
    void 기기별_최근_20초_한도를_다른_이벤트에도_적용하고_재시도는_차감하지_않는다() {
        // given
        UUID 다른_이벤트 = 이벤트를_등록한다();
        UUID requestId = UUID.randomUUID();
        eventService.submit(eventId, deviceId, requestId, 도전_횟수(400));

        // when & then
        assertThat(eventService.submit(eventId, deviceId, requestId, 도전_횟수(400)).replayed()).isTrue();
        assertThatThrownBy(() -> eventService.submit(다른_이벤트, deviceId, requestId, 도전_횟수(1)))
                .isInstanceOfSatisfying(PheeeewException.class, exception -> {
                    오류를_확인한다(exception, EVENT_RATE_LIMITED);
                    assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(20));
                });
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(400);

        jdbc.sql("UPDATE press_event_attempts SET submitted_at = clock_timestamp() - INTERVAL '20 seconds'").update();
        var 영점_도전 = eventService.submit(다른_이벤트, deviceId, requestId, 도전_횟수(0));
        assertThat(영점_도전.bestScore()).isZero();
        assertThat(영점_도전.rank()).isEqualTo(1);
        assertThat(pressQueryService.findMyDailyPresses(deviceId, 0).total()).isEqualTo(400);
    }

    private UUID 이벤트를_등록한다() {
        return 이벤트를_등록한다("-1 day", "1 day");
    }

    private UUID 이벤트를_등록한다(String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO press_events (id, starts_at, ends_at)
                VALUES (:id, statement_timestamp() + CAST(:start AS interval),
                             statement_timestamp() + CAST(:end AS interval))
                """).param("id", id).param("start", start).param("end", end).update();
        return id;
    }

    private UUID 기기를_등록한다(String nickname) {
        Device device = devices.saveAndFlush(기본_기기_빌더().nickname(nickname).build());
        return device.getPublicId();
    }

    private PressEventAttemptResult 제출한다(UUID participant, int score) {
        return eventService.submit(eventId, participant, UUID.randomUUID(), 도전_횟수(score));
    }

    private PressEventRankingResult 순위를_조회한다(UUID participant, String cursor, int limit) {
        return eventService.findRanking(eventId, participant, cursor, limit);
    }

    private PressEventCounts 도전_횟수(int angry) {
        return PressEventCounts.from(Map.of(ANGRY, angry, EXHAUSTED, 0, DISCOURAGED, 0, FRUSTRATED, 0, IRRITATED, 0));
    }

    private List<PressEventAttemptResult> 동시에_제출한다(Callable<PressEventAttemptResult> first,
            Callable<PressEventAttemptResult> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var tasks = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 제출 시작 시간이 지났습니다.");
                }
                return task.call();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(tasks.getFirst().get(10, TimeUnit.SECONDS), tasks.getLast().get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void 오류를_확인한다(PheeeewException exception, ErrorCode expected) {
        assertThat(exception.getErrorCode()).isEqualTo(expected);
    }
}
