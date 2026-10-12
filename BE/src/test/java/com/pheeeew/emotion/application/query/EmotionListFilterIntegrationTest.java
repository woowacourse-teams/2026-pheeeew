package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionErrorCode;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.emotion.infra.metrics.EmotionMetrics;
import com.pheeeew.emotion.infra.metrics.EmotionMetricsAspect;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.support.PostgisDataJpaTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({EmotionQueryService.class, EmotionMapQueryService.class, AopAutoConfiguration.class, EmotionMetrics.class,
        EmotionMetricsAspect.class})
class EmotionListFilterIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2025, 1, 2);

    @Autowired private EmotionQueryService queryService;
    @Autowired private EmotionMapQueryService mapQueryService;
    @Autowired private EmotionRepository emotions;
    @Autowired private DeviceRepository devices;
    @Autowired private EntityManager em;
    @Autowired private JdbcClient jdbc;
    @MockitoBean private AudioUrlIssuer issuer;
    @Autowired private MeterRegistry registry;

    private Device viewer;
    private Device author;

    @BeforeEach
    void setUp() {
        검증용_지역_계층을_저장한다(jdbc);
        viewer = devices.save(기본_기기_빌더().build());
        author = devices.save(기본_기기_빌더().build());
        when(issuer.issuePlayback(anyString())).thenReturn(
                PlaybackUrl.of("https://audio.example.test/recording", Instant.now().plusSeconds(3600)));
    }

    @ParameterizedTest
    @CsvSource({
            "ALL, ALL, audioMine audioOther memoGroup memoLegacy memoMine",
            "MEMO, ALL, memoGroup memoLegacy memoMine", "AUDIO, ALL, audioMine audioOther",
            "ALL, GROUP, audioMine memoGroup memoMine", "MEMO, GROUP, memoGroup memoMine", "AUDIO, GROUP, audioMine",
            "ALL, MY, audioMine memoMine", "MEMO, MY, memoMine", "AUDIO, MY, audioMine"
    })
    void 날짜_경계와_내용_및_조회_범위를_함께_적용한다(
            EmotionContentFilter contentType, EmotionSearchScope scope, String expected) {
        // given: 조회 기기는 그룹에 가입하지 않았다.
        Group group = 기본_그룹_빌더().build();
        em.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        em.persist(stamp);
        Map<String, Emotion> records = Map.of(
                "memoMine", save(viewer.getId(), stamp, false, "2025-01-01T15:00:00Z"),
                "memoLegacy", save(null, null, false, "2025-01-02T01:00:00Z"),
                "memoGroup", save(author.getId(), stamp, false, "2025-01-02T02:00:00Z"),
                "audioOther", save(author.getId(), null, true, "2025-01-02T03:00:00Z"),
                "audioMine", save(viewer.getId(), stamp, true, "2025-01-02T14:59:59.999999Z"));
        save(viewer.getId(), stamp, false, "2025-01-01T14:59:59.999999Z");
        save(viewer.getId(), stamp, true, "2025-01-02T15:00:00Z");
        em.clear();
        UUID groupId = scope == EmotionSearchScope.GROUP ? group.getPublicId() : null;

        // when
        var page = queryService.findListWithoutBounds(viewer.getPublicId(), groupId, DATE, DATE, contentType, scope, null);
        var map = mapQueryService.findContentMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                viewer.getPublicId(), groupId, DATE, DATE, contentType, scope, null);

        // then
        assertThat(page.items()).extracting(EmotionDetailView::id)
                .containsExactlyElementsOf(Arrays.stream(expected.split(" ")).map(key -> records.get(key).getId()).toList());
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(map.items()).extracting(EmotionMapItemView::id)
                .containsExactlyElementsOf(page.items().stream().map(EmotionDetailView::id).toList());
        assertThat(map.hasNext()).isFalse();
        assertThat(map.nextCursor()).isNull();
    }

    @Test
    void 필터를_페이지_제한_전에_적용하고_커서만으로_같은_조건을_이어간다() {
        // given: 더 최신인 제외 대상이 50개를 넘어도 본인의 메모 51개를 모두 조회한다.
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            expected.add(save(viewer.getId(), null, false, "2025-01-02T01:00:00Z").getId());
            save(author.getId(), null, false, "2025-01-02T02:00:00Z");
            save(viewer.getId(), null, true, "2025-01-02T03:00:00Z");
        }
        em.clear();

        // when
        var first = queryService.findListWithoutBounds(viewer.getPublicId(), null, DATE, DATE,
                EmotionContentFilter.MEMO, EmotionSearchScope.MY, null);
        assertThat(first.items()).hasSize(50);
        assertThat(first.hasNext()).isTrue();
        var next = queryService.findListWithoutBounds(viewer.getPublicId(), null, null, null, null, null, first.nextCursor());

        // then
        assertThat(Stream.concat(first.items().stream(), next.items().stream()).map(EmotionDetailView::id).toList())
                .containsExactlyElementsOf(expected.reversed());
        assertThat(next.items()).hasSize(1);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(queryService.findListWithoutBounds(author.getPublicId(), null, null, null, null, null, first.nextCursor()).items()).isEmpty();
        assertThatThrownBy(() -> queryService.findListWithoutBounds(viewer.getPublicId(), null, null, null,
                EmotionContentFilter.ALL, null, first.nextCursor()))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
        var empty = queryService.findListWithoutBounds(viewer.getPublicId(), null, DATE.minusDays(2), DATE.minusDays(2),
                EmotionContentFilter.MEMO, EmotionSearchScope.MY, null);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.hasNext()).isFalse();
        assertThat(empty.nextCursor()).isNull();
    }

    @Test
    void 지도_필터를_페이지_제한_전에_적용하고_커서로_이어간다() {
        // given
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            expected.add(save(viewer.getId(), null, false, "2025-01-02T01:00:00Z").getId());
            save(author.getId(), null, false, "2025-01-02T02:00:00Z");
            save(viewer.getId(), null, true, "2025-01-02T03:00:00Z");
        }
        em.clear();

        // when
        var first = mapQueryService.findContentMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                viewer.getPublicId(), null, DATE, DATE, EmotionContentFilter.MEMO, EmotionSearchScope.MY, null);
        assertThat(first.items()).hasSize(200);
        assertThat(first.hasNext()).isTrue();
        var next = mapQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), null, null, null, null, null, first.nextCursor());

        // then
        assertThat(Stream.concat(first.items().stream(), next.items().stream()).map(EmotionMapItemView::id).toList())
                .containsExactlyElementsOf(expected.reversed());
        assertThat(next.items()).hasSize(1);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(mapQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), null,
                DATE, DATE, EmotionContentFilter.MEMO, EmotionSearchScope.MY, first.nextCursor())).isEqualTo(next);
        assertThat(mapQueryService.findContentMapWithinBounds(null, author.getPublicId(), null, null, null, null, null, first.nextCursor()).items()).isEmpty();
        assertThatThrownBy(() -> mapQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), null,
                null, null, EmotionContentFilter.ALL, null, first.nextCursor()))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
    }

    @Test
    void 실제_목록_쿼리와_응답의_지표를_각각_한_번_기록한다() {
        // given
        save(viewer.getId(), null, false, "2025-01-02T01:00:00Z");
        em.clear();
        var timer = registry.get("pheeeew.sigh.list.query").timer();
        var results = registry.get("pheeeew.sigh.list.results").tags("page", "first", "has_next", "false").summary();
        long queryCount = timer.count();
        long resultCount = results.count();
        double resultAmount = results.totalAmount();

        // when
        var page = queryService.findListWithoutBounds(viewer.getPublicId(), null, DATE, DATE,
                EmotionContentFilter.MEMO, EmotionSearchScope.MY, null);

        // then
        assertThat(page.items()).hasSize(1);
        assertThat(timer.count()).isEqualTo(queryCount + 1);
        assertThat(results.count()).isEqualTo(resultCount + 1);
        assertThat(results.totalAmount()).isEqualTo(resultAmount + 1);
    }

    private Emotion save(Long deviceId, GroupStamp stamp, boolean audio, String createdAt) {
        Emotion emotion = emotions.saveAndFlush(기본_한숨_빌더().deviceId(deviceId).groupStamp(stamp)
                .memo(audio ? null : "메모")
                .audio(audio ? Audio.builder().objectKey("recordings/test.m4a").build() : null).build());
        jdbc.sql("UPDATE emotions SET created_at = CAST(:at AS TIMESTAMPTZ) WHERE id = :id")
                .param("at", createdAt).param("id", emotion.getId()).update();
        return emotion;
    }
}
