package com.pheeeew.emotion.infra.metrics;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.EmotionListCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionListCursor;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class EmotionMetricsAspectTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126.9, 37.5, 127.1, 37.6);
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EmotionRepository repository = mock(EmotionRepository.class);
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private EmotionQueryService service;
    private Device device;

    @BeforeEach
    void setUp() {
        device = 기본_기기_빌더().build();
        ReflectionTestUtils.setField(device, "id", 1L);
        DeviceRepository devices = mock(DeviceRepository.class);
        when(devices.findByPublicId(any())).thenReturn(Optional.of(device));
        context.registerBean(EmotionRepository.class, () -> repository);
        context.registerBean(DeviceRepository.class, () -> devices);
        context.registerBean(EmotionEmojiRepository.class, () -> mock(EmotionEmojiRepository.class));
        context.registerBean(GroupStampRepository.class, () -> mock(GroupStampRepository.class));
        context.registerBean(SimpleMeterRegistry.class, () -> registry);
        context.registerBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));
        context.register(AopAutoConfiguration.class, EmotionMetrics.class, EmotionMetricsAspect.class, EmotionQueryService.class);
        context.refresh();
        service = context.getBean(EmotionQueryService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
        registry.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20, 21})
    void 첫_페이지의_실제_반환_개수와_다음_페이지_여부를_기록한다(int count) {
        when(repository.findVisiblePageWithinBounds(any(), any(), any(), anyLong(), any(), any(), anyInt())).thenReturn(items(count));
        service.findFirstListPage(BOUNDS, device.getPublicId());
        var summary = registry.get("pheeeew.sigh.list.results").tags("page", "first", "has_next", Boolean.toString(count > 20)).summary();
        assertThat(summary.count()).isOne();
        assertThat(summary.totalAmount()).isEqualTo(Math.min(count, 20));
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isOne();
    }

    @Test
    void 다음_페이지를_별도로_기록한다() {
        String cursor = EmotionListCursorCodec.encode(EmotionListCursor.initial(BOUNDS, NOW));
        service.findNextListPage(cursor, device.getPublicId());
        assertThat(registry.get("pheeeew.sigh.list.results").tags("page", "next", "has_next", "false").summary().count()).isOne();
    }

    @Test
    void 실패한_쿼리도_시간을_기록하지만_결과는_기록하지_않는다() {
        when(repository.findVisiblePageWithinBounds(any(), any(), any(), anyLong(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.findFirstListPage(BOUNDS, device.getPublicId())).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get("pheeeew.sigh.list.query").timer().count()).isOne();
        assertThat(registry.get("pheeeew.sigh.list.results").tags("page", "first", "has_next", "false").summary().count()).isZero();
    }

    private List<Emotion> items(int count) {
        return IntStream.range(0, count).mapToObj(index -> {
            Emotion emotion = 기본_한숨_빌더().build();
            ReflectionTestUtils.setField(emotion, "id", (long) count - index);
            ReflectionTestUtils.setField(emotion, "createdAt", NOW);
            return emotion;
        }).toList();
    }
}
