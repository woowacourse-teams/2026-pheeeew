package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.command.EmotionContentResolver;
import com.pheeeew.emotion.application.mapcache.EmotionMapCacheCoordinator;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionCommandService.class, EmotionContentResolver.class,
        MapCacheTestTime.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EmotionPinSnapshotIntegrationTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

    @Autowired
    private EmotionMapQueryService queries;
    @Autowired
    private EmotionCommandService commands;
    @Autowired
    private PinCandidateCache pins;
    @Autowired
    private EmotionMapCacheCoordinator coordinator;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private MapCacheTestTime.MutableClock clock;
    @Autowired
    private PlatformTransactionManager transactions;

    private Device requester;
    private Long originalId;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP, backfill_verified_at = CURRENT_TIMESTAMP").update();
        requester = devices.save(기본_기기_빌더().build());
        originalId = emotions.save(기본_한숨_빌더().deviceId(requester.getId()).memo("기존 핀")
                .state(EmotionState.ANGRY).build()).getId();
        page();
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DELETE FROM emotion_blocks").update();
        jdbc.sql("DELETE FROM device_blocks").update();
        emotions.deleteAllInBatch();
        jdbc.sql("DELETE FROM device_daily_stamps").update();
        devices.deleteAllInBatch();
        jdbc.sql("DELETE FROM regions").update();
        jdbc.sql("UPDATE region_datasets SET boundaries_verified_at = NULL, backfill_verified_at = NULL").update();
    }

    @Test
    void 외부_트랜잭션의_미커밋_변경은_공유_스냅샷을_바꾸지_않고_롤백한다() {
        // given
        Object previous = snapshot();
        List<EmotionMapPin> previousPins = snapshotPins(previous);

        // when
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            commands.update(originalId, requester.getPublicId(), EmotionState.EXHAUSTED,
                    "외부 TX 롤백", null, false, null);
            assertThat(page().items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo(EmotionState.EXHAUSTED));
            var other = CompletableFuture.supplyAsync(this::page).orTimeout(5, TimeUnit.SECONDS).join();
            assertThat(other.items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo(EmotionState.ANGRY));
            assertThat(previousPins.getFirst().state()).isEqualTo(EmotionState.ANGRY);
            status.setRollbackOnly();
        });

        // then
        assertThat(page().items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo(EmotionState.ANGRY));
    }

    @Test
    void 외부_트랜잭션_커밋_후에는_최신_DB값을_읽고_이전_스냅샷은_보존한다() {
        // given
        List<EmotionMapPin> previousPins = snapshotPins(snapshot());

        // when
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            commands.update(originalId, requester.getPublicId(), EmotionState.EXHAUSTED,
                    "외부 TX 커밋", null, false, null);
            assertThat(page().items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo(EmotionState.EXHAUSTED));
        });

        // then
        assertThat(page().items()).singleElement().satisfies(item -> assertThat(item.state()).isEqualTo(EmotionState.EXHAUSTED));
        assertThat(previousPins.getFirst().state()).isEqualTo(EmotionState.ANGRY);
    }

    @Test
    void 미게시_등록과_즉시_수정삭제가_이전_스냅샷의_핀_선택을_바꾸지_않는다() {
        // given
        Object old = snapshot();
        List<EmotionMapPin> ordered = snapshotPins(old);

        // when
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "대기 등록", null, null, requester.getPublicId());
        assertThat(meters.get("pheeeew.map.cache.candidates").tag("cache", "pins").gauge().value()).isEqualTo(2);
        commands.update(originalId, requester.getPublicId(), EmotionState.EXHAUSTED, "수정", null, false, null);
        commands.delete(originalId, requester.getPublicId());

        // then: 이미 공개한 객체의 내용은 유지되고 새 조회만 최신 상태를 읽는다.
        assertThat(snapshotPins(old)).isEqualTo(ordered);
        assertThat(ordered).singleElement().satisfies(pin -> assertThat(pin.state()).isEqualTo(EmotionState.ANGRY));
        assertThat(page().items()).hasSize(1).noneSatisfy(pin -> assertThat(pin.id()).isEqualTo(originalId));
    }

    @Test
    void 렌더링_도중_무효화하면_이전_Map의_결과를_다시_게시하지_않는다() throws Exception {
        // given
        Object publication = ReflectionTestUtils.getField(pins, "publication");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Function<Object, Object> original = blockRender(publication, entered, release);
        CompletableFuture<Void> updating = CompletableFuture.runAsync(() -> commands.update(originalId,
                requester.getPublicId(), EmotionState.EXHAUSTED, "경합 수정", null, false, null));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // when: writer를 기다리지 않고 포인터를 폐기한다.
            coordinator.invalidate();
            assertThat(snapshot()).isNull();
        } finally {
            release.countDown();
            updating.get(5, TimeUnit.SECONDS);
            ReflectionTestUtils.setField(publication, "render", original);
        }

        // then
        assertThat(snapshot()).isNull();
        assertThat(page().items()).singleElement().satisfies(pin -> assertThat(pin.state()).isEqualTo(EmotionState.EXHAUSTED));
    }

    @Test
    void 같은_epoch에서_3초_초과로_폐기된_Current도_늦은_렌더링이_되살리지_않는다() throws Exception {
        // given
        commands.save(UUID.randomUUID(), EmotionState.ANGRY, 127, 38, 0, "대기 등록", null, null, requester.getPublicId());
        Long epoch = coordinator.read(value -> value);
        Object publication = ReflectionTestUtils.getField(pins, "publication");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Function<Object, Object> original = blockRender(publication, entered, release);
        CompletableFuture<Void> updating = CompletableFuture.runAsync(() -> commands.update(originalId,
                requester.getPublicId(), EmotionState.EXHAUSTED, "기한 경합", null, false, null));
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // when: 이 조회는 오래된 snapshot을 버리고 SQL로 응답한다.
            clock.advance(Duration.ofSeconds(3));
            assertThat(page().items()).hasSize(2);
            long currentEpoch = coordinator.read(value -> value);
            assertThat(currentEpoch).isEqualTo(epoch);
            assertThat(snapshot()).isNull();
        } finally {
            release.countDown();
            updating.get(5, TimeUnit.SECONDS);
            ReflectionTestUtils.setField(publication, "render", original);
        }

        // then: epoch가 같아도 Current identity가 달라졌으므로 게시를 거부한다.
        assertThat(snapshot()).isNull();
        assertThat(page().items()).filteredOn(pin -> pin.id().equals(originalId)).singleElement()
                .satisfies(pin -> assertThat(pin.state()).isEqualTo(EmotionState.EXHAUSTED));
    }

    private com.pheeeew.emotion.application.dto.EmotionMapPageView page() {
        return queries.findContentMapWithinBounds(BOUNDS, requester.getPublicId(), null, null, null, null, null, null);
    }

    private Object snapshot() {
        Object publication = ReflectionTestUtils.getField(pins, "publication");
        Object read = ReflectionTestUtils.invokeMethod(publication, "read");
        return ReflectionTestUtils.invokeMethod(read, "snapshot");
    }

    private List<EmotionMapPin> snapshotPins(Object snapshot) {
        Object ordered = ReflectionTestUtils.invokeMethod(snapshot, "ordered");
        var cursor = EmotionCursor.initialWithinBounds(BOUNDS, clock.instant(), EmotionSearchFilter.from(null));
        Object selection = ReflectionTestUtils.invokeMethod(ordered, "select", cursor,
                EmotionMapBlocks.of(Set.of(), Set.of()), 201);

        return ReflectionTestUtils.invokeMethod(selection, "pins");
    }

    @SuppressWarnings("unchecked")
    private Function<Object, Object> blockRender(Object publication, CountDownLatch entered, CountDownLatch release) {
        Function<Object, Object> original = (Function<Object, Object>) ReflectionTestUtils.getField(publication, "render");
        ReflectionTestUtils.setField(publication, "render", (Function<Object, Object>) canonical -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("렌더링 해제 시간 초과");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
            return original.apply(canonical);
        });
        return original;
    }
}
