package com.pheeeew.report.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pheeeew.report.application.command.EmotionBlockCommandService;
import com.pheeeew.report.application.query.EmotionBlockQueryService;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.application.DeviceService;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.report.exception.BlockErrorCode;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.Emotion;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionBlockServiceIntegrationTest {

    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;
    private static final Long 없는_한숨_식별자 = Long.MAX_VALUE;
    private static final UUID 없는_기기_공개_식별자 =
            UUID.fromString("1f9b0c6a-7d4e-4a1b-9c2d-8e3f5a6b7c8d");

    private int 등록_순번;

    @Autowired
    private EmotionBlockCommandService emotionBlockCommandService;

    @Autowired
    private EmotionBlockQueryService emotionBlockQueryService;

    @Autowired
    private EmotionBlockRepository emotionBlockRepository;

    @Autowired
    private EmotionRepository emotionRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbcClient);
    }

    @AfterEach
    void tearDown() {
        emotionBlockRepository.deleteAll();
        emotionRepository.deleteAll();
        deviceRepository.deleteAll();
        jdbcClient.sql("DELETE FROM regions").update();
        등록_순번 = 0;
    }

    @Test
    void 처음_차단하면_차단을_저장하고_한숨의_닉네임과_메모를_반환한다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 작성자 = 기기를_저장한다();
        Long 차단할_한숨 = 한숨을_저장한다(작성자.getId(), "오늘은 조금 지쳤다");

        // when
        BlockSaveResult result = emotionBlockCommandService.save(차단할_한숨, 차단자.getPublicId());

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.block().blockId()).isPositive();
        assertThat(result.block().emotionId()).isEqualTo(차단할_한숨);
        assertThat(result.block().nickname()).isEqualTo("익명");
        assertThat(result.block().memo()).isEqualTo("오늘은 조금 지쳤다");
        assertThat(result.block().createdAt()).isNotNull();
        assertThat(emotionBlockRepository.count()).isOne();
    }

    @Test
    void 같은_한숨을_두_번_차단해도_오류가_나지_않고_행이_하나만_남는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 차단할_한숨 = 한숨을_저장한다(null, null);
        BlockSaveResult 최초 = emotionBlockCommandService.save(차단할_한숨, 차단자.getPublicId());

        // when
        BlockSaveResult 다시 = emotionBlockCommandService.save(차단할_한숨, 차단자.getPublicId());

        // then
        assertThat(최초.created()).isTrue();
        assertThat(다시.created()).isFalse();
        assertThat(다시.block().blockId()).isEqualTo(최초.block().blockId());
        assertThat(다시.block().createdAt()).isEqualTo(최초.block().createdAt());
        assertThat(다시.block().nickname()).isEqualTo("익명");
        assertThat(emotionBlockRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 익명과_기명_한숨을_동시에_차단해도_한_건만_저장한다(boolean anonymous) throws Exception {
        // given
        int requestCount = 6;
        Device 차단자 = 기기를_저장한다();
        Device 작성자 = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("작성자").build());
        Long 차단할_한숨 = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .deviceId(작성자.getId()).anonymous(anonymous).build()).getId();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        // when
        List<BlockSaveResult> results =
                동시에_차단한다(requestCount, 차단할_한숨, 차단자.getPublicId(), ready, start);

        // then
        assertThat(results).filteredOn(BlockSaveResult::created).hasSize(1);
        assertThat(results)
                .extracting(result -> result.block().blockId())
                .containsOnly(results.getFirst().block().blockId());
        String nickname = "익명";
        if (!anonymous) {
            nickname = "작성자";
        }
        assertThat(results).extracting(result -> result.block().nickname()).containsOnly(nickname);
        assertThat(emotionBlockRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 감정_차단_재시도는_현재_닉네임을_반영하되_익명은_유지한다(boolean anonymous) {
        // given
        Device blocker = 기기를_저장한다();
        Device writer = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("이전 이름").build());
        Emotion target = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .deviceId(writer.getId()).anonymous(anonymous).memo("최초 근거").build());
        BlockSaveResult first = emotionBlockCommandService.save(target.getId(), blocker.getPublicId());
        String firstNickname = "익명";
        if (!anonymous) {
            firstNickname = "이전 이름";
        }
        assertThat(first.block().nickname()).isEqualTo(firstNickname);

        // when
        deviceService.updateNickname(writer.getPublicId(), "새 이름");
        BlockSaveResult retried = emotionBlockCommandService.save(target.getId(), blocker.getPublicId());

        // then
        String currentNickname = "익명";
        if (!anonymous) {
            currentNickname = "새 이름";
        }
        assertThat(retried.created()).isFalse();
        assertThat(retried.block().nickname()).isEqualTo(currentNickname);
        assertThat(retried.block().blockId()).isEqualTo(first.block().blockId());
        assertThat(retried.block().emotionId()).isEqualTo(target.getId());
        assertThat(retried.block().memo()).isEqualTo("최초 근거");
        assertThat(retried.block().createdAt()).isEqualTo(first.block().createdAt());
        assertThat(emotionBlockRepository.count()).isOne();
    }

    @Test
    void 기명_감정의_작성자_닉네임이_없으면_과거_랜덤_이름을_반환하지_않는다() {
        // given
        Device blocker = 기기를_저장한다();
        Device writer = 기기를_저장한다();
        Emotion target = emotionRepository.saveAndFlush(기본_한숨_빌더().deviceId(writer.getId()).anonymous(false).build());

        // when
        BlockSaveResult result = emotionBlockCommandService.save(target.getId(), blocker.getPublicId());

        // then
        assertThat(result.block().nickname()).isEqualTo("익명");
        assertThat(result.block().emotionId()).isEqualTo(target.getId());
    }

    @Test
    void 자기_한숨도_한숨_차단할_수_있다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 내가_쓴_한숨 = 한숨을_저장한다(차단자.getId(), null);

        // when
        BlockSaveResult result = emotionBlockCommandService.save(내가_쓴_한숨, 차단자.getPublicId());

        // then
        assertThat(result.created()).isTrue();
        assertThat(emotionBlockRepository.count()).isOne();
    }

    @Test
    void 존재하지_않는_감정을_차단하면_예외가_발생하고_행이_생기지_않는다() {
        // given
        Device 차단자 = 기기를_저장한다();

        // when
        Throwable throwable =
                catchThrowable(() -> emotionBlockCommandService.save(없는_한숨_식별자, 차단자.getPublicId()));

        // then
        assertThat(throwable)
                .isInstanceOf(EmotionException.class)
                .hasMessage("감정을 찾을 수 없습니다.");
        assertThat(((EmotionException) throwable).getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_NOT_FOUND);
        assertThat(emotionBlockRepository.count()).isZero();
    }

    @Test
    void 등록되지_않은_기기가_차단하면_예외가_발생하고_행이_생기지_않는다() {
        // given
        Long 차단할_한숨 = 한숨을_저장한다(null, null);

        // when
        Throwable throwable =
                catchThrowable(() -> emotionBlockCommandService.save(차단할_한숨, 없는_기기_공개_식별자));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
        assertThat(emotionBlockRepository.count()).isZero();
    }

    @Test
    void 차단하지_않은_한숨을_해제해도_오류가_나지_않는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 차단하지_않은_한숨 = 한숨을_저장한다(null, null);

        // when
        Throwable throwable =
                catchThrowable(() -> emotionBlockCommandService.delete(차단하지_않은_한숨, 차단자.getPublicId()));

        // then
        assertThat(throwable).isNull();
        assertThat(emotionBlockRepository.count()).isZero();
    }

    @Test
    void 남의_차단은_해제되지_않는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        Long 차단할_한숨 = 한숨을_저장한다(null, null);
        emotionBlockCommandService.save(차단할_한숨, 차단자.getPublicId());

        // when
        emotionBlockCommandService.delete(차단할_한숨, 남.getPublicId());

        // then
        assertThat(emotionBlockRepository.findByBlockerDeviceIdAndEmotionId(차단자.getId(), 차단할_한숨))
                .isPresent();
        assertThat(emotionBlockRepository.count()).isOne();
    }

    @Test
    void 차단_목록은_최근_차단부터_반환하고_내_차단만_담는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 남 = 기기를_저장한다();
        Long 먼저_차단한_한숨 = 한숨을_저장한다(null, "먼저 차단");
        Long 나중에_차단한_한숨 = 한숨을_저장한다(null, "나중에 차단");
        Long 남이_차단한_한숨 = 한숨을_저장한다(null, null);
        emotionBlockCommandService.save(먼저_차단한_한숨, 차단자.getPublicId());
        emotionBlockCommandService.save(나중에_차단한_한숨, 차단자.getPublicId());
        emotionBlockCommandService.save(남이_차단한_한숨, 남.getPublicId());

        // when
        BlockListResult result = emotionBlockQueryService.findAll(차단자.getPublicId(), null);

        // then
        assertThat(result.items())
                .extracting(BlockResult::emotionId)
                .containsExactly(나중에_차단한_한숨, 먼저_차단한_한숨);
        assertThat(result.items().getFirst().memo()).isEqualTo("나중에 차단");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 차단_목록은_현재_닉네임을_반영하고_익명과_작성자_없는_감정을_유지한다() {
        // given
        Device blocker = 기기를_저장한다();
        Device writer = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("이전 이름").build());
        Device unnamed = 기기를_저장한다();
        Emotion namedEmotion = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .deviceId(writer.getId()).anonymous(false).memo("기명 근거").build());
        Emotion anonymousEmotion = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .deviceId(writer.getId()).anonymous(true).build());
        Emotion ownerlessEmotion = emotionRepository.saveAndFlush(기본_한숨_빌더().build());
        Emotion unnamedEmotion = emotionRepository.saveAndFlush(기본_한숨_빌더()
                .deviceId(unnamed.getId()).anonymous(false).build());
        for (Emotion emotion : List.of(namedEmotion, anonymousEmotion, ownerlessEmotion, unnamedEmotion)) {
            emotionBlockCommandService.save(emotion.getId(), blocker.getPublicId());
        }
        BlockListResult before = emotionBlockQueryService.findAll(blocker.getPublicId(), null);
        assertThat(before.items()).extracting(BlockResult::nickname)
                .containsExactly("익명", "익명", "익명", "이전 이름");

        // when
        deviceService.updateNickname(writer.getPublicId(), "새 이름");
        jdbcClient.sql("UPDATE emotions SET deleted_at = NOW() WHERE id = :id")
                .param("id", namedEmotion.getId()).update();
        BlockListResult after = emotionBlockQueryService.findAll(blocker.getPublicId(), null);

        // then
        assertThat(after.items()).extracting(BlockResult::nickname)
                .containsExactly("익명", "익명", "익명", "새 이름");
        assertThat(after.items()).extracting(BlockResult::emotionId)
                .containsExactly(unnamedEmotion.getId(), ownerlessEmotion.getId(), anonymousEmotion.getId(), namedEmotion.getId());
        assertThat(after.items()).extracting(BlockResult::blockId)
                .containsExactlyElementsOf(before.items().stream().map(BlockResult::blockId).toList());
        assertThat(after.items()).extracting(BlockResult::createdAt)
                .containsExactlyElementsOf(before.items().stream().map(BlockResult::createdAt).toList());
        assertThat(after.items().getLast().memo()).isEqualTo("기명 근거");
    }

    @Test
    void 삭제된_한숨도_차단_목록에_남는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long 차단할_한숨 = 한숨을_저장한다(null, null);
        emotionBlockCommandService.save(차단할_한숨, 차단자.getPublicId());
        jdbcClient.sql("UPDATE emotions SET deleted_at = NOW() WHERE id = :id")
                .param("id", 차단할_한숨)
                .update();

        // when
        BlockListResult result = emotionBlockQueryService.findAll(차단자.getPublicId(), null);

        // then
        assertThat(result.items())
                .extracting(BlockResult::emotionId)
                .containsExactly(차단할_한숨);
    }

    @Test
    void 삭제된_감정을_새로_차단하고_재요청하면_최초_차단을_반환한다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Long emotionId = 한숨을_저장한다(null, null);
        jdbcClient.sql("UPDATE emotions SET deleted_at = NOW() WHERE id = :id")
                .param("id", emotionId)
                .update();

        // when
        BlockSaveResult 최초 = emotionBlockCommandService.save(emotionId, 차단자.getPublicId());
        BlockSaveResult 재요청 = emotionBlockCommandService.save(emotionId, 차단자.getPublicId());

        // then
        assertThat(최초.created()).isTrue();
        assertThat(재요청.created()).isFalse();
        assertThat(재요청.block().blockId()).isEqualTo(최초.block().blockId());
        assertThat(emotionBlockRepository.count()).isOne();
        assertThat(emotionBlockQueryService.findAll(차단자.getPublicId(), null).items())
                .extracting(BlockResult::emotionId)
                .containsExactly(emotionId);
    }

    @Test
    void 차단이_한_페이지를_넘으면_커서로_다음_페이지를_이어_준다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 작성자 = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("이전 이름").build());
        List<Long> 차단한_한숨들 = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            Long emotionId;
            if (index == 0) {
                emotionId = emotionRepository.saveAndFlush(기본_한숨_빌더()
                        .deviceId(작성자.getId()).anonymous(false).build()).getId();
            } else {
                emotionId = 한숨을_저장한다(null, null);
            }
            차단한_한숨들.add(emotionId);
            emotionBlockCommandService.save(emotionId, 차단자.getPublicId());
        }

        // when
        BlockListResult 첫_페이지 = emotionBlockQueryService.findAll(차단자.getPublicId(), null);
        deviceService.updateNickname(작성자.getPublicId(), "새 이름");
        BlockListResult 다음_페이지 =
                emotionBlockQueryService.findAll(차단자.getPublicId(), 첫_페이지.nextCursor());

        // then
        assertThat(첫_페이지.items()).hasSize(50);
        assertThat(첫_페이지.items()).extracting(BlockResult::nickname).containsOnly("익명");
        assertThat(첫_페이지.items()).extracting(BlockResult::emotionId)
                .containsExactlyElementsOf(차단한_한숨들.reversed().subList(0, 50));
        assertThat(첫_페이지.hasNext()).isTrue();
        assertThat(첫_페이지.nextCursor()).isNotBlank();
        assertThat(다음_페이지.items())
                .extracting(BlockResult::emotionId)
                .containsExactly(차단한_한숨들.getFirst());
        assertThat(다음_페이지.hasNext()).isFalse();
        assertThat(다음_페이지.items().getFirst().nickname()).isEqualTo("새 이름");
        assertThat(다음_페이지.nextCursor()).isNull();
    }

    @Test
    void 사용할_수_없는_커서로_차단_목록을_조회하면_예외가_발생한다() {
        // given
        Device 차단자 = 기기를_저장한다();

        // when
        Throwable throwable =
                catchThrowable(() -> emotionBlockQueryService.findAll(차단자.getPublicId(), "not-a-cursor"));

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
                        INSERT INTO emotions (request_id, location, nickname, memo, device_id, created_at, updated_at,
                                              region_code, region_classified_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            '외로운 회사원',
                            :memo,
                            :deviceId,
                            TIMESTAMPTZ '2026-09-01T10:00:00Z' + :sequence * INTERVAL '1 minute',
                            TIMESTAMPTZ '2026-09-01T10:00:00Z' + :sequence * INTERVAL '1 minute',
                            '11010530',
                            TIMESTAMPTZ '2026-09-01T10:00:00Z'
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
                    return emotionBlockCommandService.save(emotionId, devicePublicId);
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
}
