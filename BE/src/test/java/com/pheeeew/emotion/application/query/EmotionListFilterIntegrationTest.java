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
import com.pheeeew.emotion.application.EmotionCursorCodec;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.dto.EmotionPageView;
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
import com.pheeeew.groups.exception.GroupErrorCode;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
        var page = queryService.findListWithoutBounds(viewer.getPublicId(), groupId == null ? null : List.of(groupId), DATE, DATE, contentType, scope, null);
        var map = mapQueryService.findContentMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                viewer.getPublicId(), groupId == null ? null : List.of(groupId), DATE, DATE, contentType, scope, null);

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 지도는_여러_그룹을_OR로_조회하고_다음_페이지에서도_선택한_그룹_전체를_검증한다(boolean deleteAfterFirst) {
        // given: 각 그룹은 200개 미만이지만 A와 B를 합치면 201개이며, 조회 기기는 그룹에 가입하지 않았다.
        GroupStamp a = saveStamp("그룹 A");
        GroupStamp b = saveStamp("그룹 B");
        GroupStamp c = saveStamp("그룹 C");
        List<Long> expected = saveFilteredEmotions(201, a, b, c);

        // when
        List<UUID> groupIds = List.of(b.getGroup().getPublicId(), a.getGroup().getPublicId(), b.getGroup().getPublicId());
        var first = mapQueryService.findContentMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                viewer.getPublicId(), groupIds, DATE, DATE, EmotionContentFilter.MEMO, null, null);

        // then
        assertThat(first.items()).extracting(EmotionMapItemView::id)
                .containsExactlyElementsOf(expected.reversed().subList(0, 200));
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(EmotionCursorCodec.decodeFilteredWithinBounds(first.nextCursor()).groupIds())
                .containsExactlyInAnyOrder(a.getGroup().getPublicId(), b.getGroup().getPublicId());
        if (deleteAfterFirst) {
            // 남은 한숨은 A 소속이어도 커서에 담긴 B가 삭제되면 조회를 거부한다.
            em.find(Group.class, b.getGroup().getId()).delete(Instant.now());
            em.flush();
            em.clear();
            assertThatThrownBy(() -> findMap(first.nextCursor()))
                    .isInstanceOfSatisfying(GroupException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
            return;
        }

        var next = findMap(first.nextCursor());
        assertThat(next.items()).hasSize(1);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(Stream.concat(first.items().stream(), next.items().stream()).map(EmotionMapItemView::id).toList())
                .containsExactlyElementsOf(expected.reversed());
        for (List<UUID> same : List.of(groupIds, groupIds.reversed(), List.of(a.getGroup().getPublicId(), b.getGroup().getPublicId()))) {
            assertThat(mapQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), same,
                    null, null, null, EmotionSearchScope.GROUP, first.nextCursor())).isEqualTo(next);
        }
        for (List<UUID> changed : List.of(List.of(a.getGroup().getPublicId()),
                List.of(a.getGroup().getPublicId(), b.getGroup().getPublicId(), c.getGroup().getPublicId()))) {
            assertThatThrownBy(() -> mapQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), changed,
                    null, null, null, null, first.nextCursor())).isInstanceOfSatisfying(EmotionException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 목록은_여러_그룹을_OR로_조회하고_다음_페이지에서도_선택한_그룹_전체를_검증한다(boolean deleteAfterFirst) {
        // given: 각 그룹은 50개 미만이지만 A와 B를 합치면 51개이며, 조회 기기는 그룹에 가입하지 않았다.
        GroupStamp a = saveStamp("그룹 A");
        GroupStamp b = saveStamp("그룹 B");
        GroupStamp c = saveStamp("그룹 C");
        List<Long> expected = saveFilteredEmotions(51, a, b, c);

        // when
        List<UUID> groupIds = List.of(b.getGroup().getPublicId(), a.getGroup().getPublicId(), b.getGroup().getPublicId());
        var first = queryService.findListWithoutBounds(viewer.getPublicId(), groupIds,
                DATE, DATE, EmotionContentFilter.MEMO, null, null);

        // then
        assertThat(first.items()).extracting(EmotionDetailView::id)
                .containsExactlyElementsOf(expected.reversed().subList(0, 50));
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(EmotionCursorCodec.decodeFilteredWithoutBounds(first.nextCursor()).groupIds())
                .containsExactlyInAnyOrder(a.getGroup().getPublicId(), b.getGroup().getPublicId());
        if (deleteAfterFirst) {
            // 남은 한숨은 A 소속이어도 커서에 담긴 B가 삭제되면 조회를 거부한다.
            em.find(Group.class, b.getGroup().getId()).delete(Instant.now());
            em.flush();
            em.clear();
            assertThatThrownBy(() -> findList(first.nextCursor()))
                    .isInstanceOfSatisfying(GroupException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
            return;
        }

        var next = findList(first.nextCursor());
        assertThat(next.items()).hasSize(1);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
        assertThat(Stream.concat(first.items().stream(), next.items().stream()).map(EmotionDetailView::id).toList())
                .containsExactlyElementsOf(expected.reversed());
        for (List<UUID> same : List.of(groupIds, groupIds.reversed(), List.of(a.getGroup().getPublicId(), b.getGroup().getPublicId()))) {
            assertThat(queryService.findListWithoutBounds(viewer.getPublicId(), same,
                    null, null, null, EmotionSearchScope.GROUP, first.nextCursor())).isEqualTo(next);
        }
        for (List<UUID> changed : List.of(List.of(a.getGroup().getPublicId()),
                List.of(a.getGroup().getPublicId(), b.getGroup().getPublicId(), c.getGroup().getPublicId()))) {
            assertThatThrownBy(() -> queryService.findListWithoutBounds(viewer.getPublicId(), changed,
                    null, null, null, null, first.nextCursor())).isInstanceOfSatisfying(EmotionException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
        }
        assertThatThrownBy(() -> queryService.findListWithoutBounds(viewer.getPublicId(), null, null, null,
                EmotionContentFilter.ALL, null, first.nextCursor()))
                .isInstanceOfSatisfying(EmotionException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(EmotionErrorCode.EMOTION_INVALID_CURSOR));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "true, true", "false, false", "false, true"})
    void 지도와_목록은_선택한_그룹_중_하나라도_없거나_삭제되었으면_거부한다(boolean map, boolean deleted) {
        // given
        GroupStamp valid = saveStamp("유효 그룹");
        Group invalid = 기본_그룹_빌더().name("무효 그룹").build();
        if (deleted) {
            em.persist(invalid);
            invalid.delete(Instant.now());
        }
        save(author.getId(), valid, false, "2025-01-02T01:00:00Z");
        em.flush();
        em.clear();
        List<UUID> groupIds = List.of(valid.getGroup().getPublicId(), invalid.getPublicId());

        // when / then
        assertThatThrownBy(() -> {
            if (map) {
                mapQueryService.findContentMapWithinBounds(EmotionSearchBounds.of(126, 37, 128, 38),
                        viewer.getPublicId(), groupIds, DATE, DATE, EmotionContentFilter.MEMO, null, null);
            } else {
                queryService.findListWithoutBounds(viewer.getPublicId(), groupIds,
                        DATE, DATE, EmotionContentFilter.MEMO, null, null);
            }
        })
                .isInstanceOfSatisfying(GroupException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND));
    }

    @ParameterizedTest
    @CsvSource({"true,0", "true,200", "true,201", "false,0", "false,50", "false,51"})
    void 많은_그룹을_선택해도_결과_수와_무관하게_조회하고_긴_커서로_다음_페이지를_잇는다(boolean map, int count) {
        // given
        List<GroupStamp> stamps = IntStream.range(0, 100).mapToObj(index -> saveStamp("확장" + index)).toList();
        List<UUID> groups = stamps.stream().map(stamp -> stamp.getGroup().getPublicId()).toList();
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            expected.add(save(author.getId(), stamps.get(index % stamps.size()), false, "2025-01-02T01:00:00Z").getId());
        }
        save(author.getId(), saveStamp("선택 외"), false, "2025-01-02T02:00:00Z");
        em.flush();
        em.clear();
        int pageSize = map ? 200 : 50;

        // when
        var bounds = EmotionSearchBounds.of(126, 37, 128, 38);
        var mapPage = map ? mapQueryService.findContentMapWithinBounds(bounds, viewer.getPublicId(), groups,
                DATE, DATE, EmotionContentFilter.MEMO, null, null) : null;
        var listPage = map ? null : queryService.findListWithoutBounds(viewer.getPublicId(), groups,
                DATE, DATE, EmotionContentFilter.MEMO, null, null);
        List<Long> firstIds = map ? mapPage.items().stream().map(EmotionMapItemView::id).toList()
                : listPage.items().stream().map(EmotionDetailView::id).toList();
        String nextCursor = map ? mapPage.nextCursor() : listPage.nextCursor();

        // then
        assertThat(firstIds).containsExactlyElementsOf(expected.reversed().stream().limit(pageSize).toList());
        assertThat(map ? mapPage.hasNext() : listPage.hasNext()).isEqualTo(count > pageSize);
        if (count <= pageSize) {
            assertThat(nextCursor).isNull();
            return;
        }
        assertThat(nextCursor.length()).isGreaterThan(2_048);
        var decoded = map ? EmotionCursorCodec.decodeFilteredWithinBounds(nextCursor)
                : EmotionCursorCodec.decodeFilteredWithoutBounds(nextCursor);
        assertThat(decoded.groupIds()).containsExactlyElementsOf(groups.stream().sorted().toList());
        assertThat(decoded.filter().startDate()).isEqualTo(DATE);
        assertThat(decoded.filter().endDate()).isEqualTo(DATE);
        assertThat(decoded.filter().contentType()).isEqualTo(EmotionContentFilter.MEMO);
        if (map) {
            var next = findMap(nextCursor);
            assertThat(next.items()).extracting(EmotionMapItemView::id).containsExactly(expected.getFirst());
            assertThat(next.hasNext()).isFalse();
            assertThat(next.nextCursor()).isNull();
        } else {
            var next = findList(nextCursor);
            assertThat(next.items()).extracting(EmotionDetailView::id).containsExactly(expected.getFirst());
            assertThat(next.hasNext()).isFalse();
            assertThat(next.nextCursor()).isNull();
        }
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

    private GroupStamp saveStamp(String name) {
        Group group = 기본_그룹_빌더().name(name).build();
        em.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        em.persist(stamp);
        return stamp;
    }

    private List<Long> saveFilteredEmotions(int count, GroupStamp a, GroupStamp b, GroupStamp c) {
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            expected.add(save(author.getId(), index % 2 == 0 ? a : b, false, "2025-01-02T01:00:00Z").getId());
            save(author.getId(), c, false, "2025-01-02T01:01:00Z");
        }
        save(author.getId(), a, true, "2025-01-02T01:02:00Z");
        save(author.getId(), b, false, "2025-01-01T01:00:00Z");
        save(author.getId(), b, false, Instant.now().plusSeconds(3600).toString());
        save(author.getId(), a, false, "2025-01-02T01:03:00Z").delete();
        Emotion blocked = save(author.getId(), b, false, "2025-01-02T01:04:00Z");
        em.persist(EmotionBlock.builder().blockerDeviceId(viewer.getId()).emotionId(blocked.getId()).build());
        Device blockedAuthor = devices.save(기본_기기_빌더().build());
        Emotion byBlockedAuthor = save(blockedAuthor.getId(), a, false, "2025-01-02T01:05:00Z");
        em.persist(DeviceBlock.builder().blockerDeviceId(viewer.getId()).blockedDeviceId(blockedAuthor.getId())
                .originEmotionId(byBlockedAuthor.getId()).build());
        em.flush();
        em.clear();
        return expected;
    }

    private EmotionMapPageView findMap(String cursor) {
        return mapQueryService.findContentMapWithinBounds(null, viewer.getPublicId(), null, null, null, null, null, cursor);
    }

    private EmotionPageView findList(String cursor) {
        return queryService.findListWithoutBounds(viewer.getPublicId(), null, null, null, null, null, cursor);
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
