package com.pheeeew.emotion.domain.repository;

import static com.pheeeew.emotion.domain.QEmotion.emotion;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.contentMatches;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.createdAtOrAfter;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.createdBefore;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.scopeMatches;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

public class EmotionRegionQueryRepositoryImpl implements EmotionRegionQueryRepository {

    private static final List<EmotionState> REPRESENTATIVE_PRIORITY = List.of(
            EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
            EmotionState.FRUSTRATED, EmotionState.IRRITATED);

    private final JPAQueryFactory queryFactory;
    private final JdbcClient jdbc;

    public EmotionRegionQueryRepositoryImpl(EntityManager entityManager, JdbcClient jdbc) {
        this.queryFactory = new JPAQueryFactory(entityManager);
        this.jdbc = jdbc;
    }

    @Override
    public List<RegionEmotionSummaryProjection> findSummariesByRegionCodes(
            List<String> regionCodes,
            EmotionSearchFilter filter,
            Long deviceId,
            Instant snapshotAt
    ) {
        if (regionCodes.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> rootsByMember = findRootsByMember(regionCodes);
        if (rootsByMember.isEmpty()) {
            return List.of();
        }

        List<Tuple> counts = queryFactory.select(emotion.regionCode, emotion.state, emotion.id.count())
                .from(emotion)
                .where(emotion.regionCode.in(rootsByMember.keySet()), emotion.deletedAt.isNull(),
                        emotion.createdAt.loe(snapshotAt), contentMatches(filter.contentType()),
                        createdAtOrAfter(filter.startInclusive()), createdBefore(filter.endExclusive()),
                        scopeMatches(filter, deviceId))
                .groupBy(emotion.regionCode, emotion.state)
                .fetch();

        Map<String, Map<EmotionState, Long>> countsByRoot = new HashMap<>();
        for (Tuple count : counts) {
            String memberCode = count.get(emotion.regionCode);
            EmotionState state = count.get(emotion.state);
            long stateCount = count.get(emotion.id.count());
            for (String root : rootsByMember.get(memberCode)) {
                countsByRoot.computeIfAbsent(root, ignored -> new HashMap<>())
                        .merge(state, stateCount, Long::sum);
            }
        }

        return countsByRoot.entrySet().stream()
                .map(entry -> RegionEmotionSummaryProjection.of(entry.getKey(),
                        entry.getValue().values().stream().mapToLong(Long::longValue).sum(),
                        findRepresentative(entry.getValue())))
                .toList();
    }

    private Map<String, List<String>> findRootsByMember(List<String> regionCodes) {
        // 코드 접두사가 아니라 저장된 부모 관계로 하위 지역을 찾는다.
        return jdbc.sql("""
                WITH RECURSIVE region_members(root_code, member_code) AS (
                    SELECT code, code FROM regions WHERE code IN (:regionCodes)
                    UNION ALL
                    SELECT parent.root_code, child.code
                    FROM region_members parent JOIN regions child ON child.parent_code = parent.member_code
                )
                SELECT root_code, member_code FROM region_members
                """).param("regionCodes", regionCodes)
                .query((row, index) -> Map.entry(row.getString("member_code"), row.getString("root_code")))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private EmotionState findRepresentative(Map<EmotionState, Long> stateCounts) {
        EmotionState representative = null;
        long highestCount = 0;
        for (EmotionState state : REPRESENTATIVE_PRIORITY) {
            long count = stateCounts.getOrDefault(state, 0L);
            if (count > highestCount) {
                representative = state;
                highestCount = count;
            }
        }
        return representative;
    }
}
