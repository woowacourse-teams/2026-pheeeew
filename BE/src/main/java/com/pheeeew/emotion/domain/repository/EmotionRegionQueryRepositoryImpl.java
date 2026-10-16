package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.RegionEmotionSummaryProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.region.domain.RegionLevel;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;

public class EmotionRegionQueryRepositoryImpl implements EmotionRegionQueryRepository {

    private static final List<EmotionState> REPRESENTATIVE_PRIORITY = List.of(
            EmotionState.ANGRY, EmotionState.DISCOURAGED, EmotionState.EXHAUSTED,
            EmotionState.FRUSTRATED, EmotionState.IRRITATED);

    private final EntityManager entityManager;

    public EmotionRegionQueryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<RegionEmotionSummaryProjection> findSummariesByRegionCodes(
            List<String> regionCodes,
            RegionLevel level,
            EmotionSearchFilter filter,
            Long deviceId,
            Instant snapshotAt
    ) {
        if (regionCodes.isEmpty()) {
            return List.of();
        }

        NativeQuery<Object[]> query = createCountsQuery(level, filter);
        query.setParameterList("regionCodes", regionCodes, String.class);
        query.setParameter("snapshotAt", snapshotAt, StandardBasicTypes.INSTANT);
        if (filter.startInclusive() != null) {
            query.setParameter("startInclusive", filter.startInclusive(), StandardBasicTypes.INSTANT);
            query.setParameter("endExclusive", filter.endExclusive(), StandardBasicTypes.INSTANT);
        }
        if (filter.scope() == EmotionSearchScope.MY) {
            query.setParameter("deviceId", deviceId);
        }
        if (filter.scope() == EmotionSearchScope.GROUP) {
            query.setParameter("groupId", filter.groupId());
        }

        Map<String, Map<EmotionState, Long>> countsByRoot = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            String rootCode = (String) row[0];
            EmotionState state = row[1] == null ? null : EmotionState.valueOf((String) row[1]);
            countsByRoot.computeIfAbsent(rootCode, ignored -> new HashMap<>())
                    .merge(state, (Long) row[2], Long::sum);
        }

        return countsByRoot.entrySet().stream()
                .map(entry -> RegionEmotionSummaryProjection.of(entry.getKey(),
                        entry.getValue().values().stream().mapToLong(Long::longValue).sum(),
                        findRepresentative(entry.getValue())))
                .toList();
    }

    private NativeQuery<Object[]> createCountsQuery(RegionLevel level, EmotionSearchFilter filter) {
        String root = switch (level) {
            case EMD -> "emd.code";
            case SIGUNGU -> "sigungu.code";
            case SIDO -> "sido.code";
        };
        String joins = switch (level) {
            case EMD -> "";
            case SIGUNGU -> "JOIN regions sigungu ON sigungu.code = emd.parent_code";
            case SIDO -> """
                    JOIN regions sigungu ON sigungu.code = emd.parent_code
                    JOIN regions sido ON sido.code = sigungu.parent_code
                    """;
        };
        String content = switch (filter.contentType()) {
            case ALL -> "(e.memo IS NOT NULL OR e.audio_object_key IS NOT NULL)";
            case MEMO -> "e.memo IS NOT NULL";
            case AUDIO -> "e.audio_object_key IS NOT NULL";
        };
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS region_code, e.state AS state, COUNT(e.id) AS state_count
                FROM emotions e
                JOIN regions emd ON emd.code = e.region_code
                %s
                WHERE %s IN (:regionCodes)
                  AND e.deleted_at IS NULL
                  AND e.created_at <= :snapshotAt
                  AND %s
                """.formatted(root, joins, root, content));
        if (filter.startInclusive() != null) {
            sql.append(" AND e.created_at >= :startInclusive AND e.created_at < :endExclusive");
        }
        switch (filter.scope()) {
            case ALL -> { }
            case MY -> sql.append(" AND e.device_id = :deviceId");
            case GROUP -> sql.append("""
                     AND EXISTS (
                        SELECT 1 FROM group_stamps gs JOIN groups g ON g.id = gs.group_id
                        WHERE gs.id = e.group_stamp_id AND g.public_id = :groupId
                    )
                    """);
        }
        sql.append(" GROUP BY ").append(root).append(", e.state");

        NativeQuery<Object[]> query = entityManager.unwrap(Session.class).createNativeQuery(sql.toString(), Object[].class)
                .addScalar("region_code", String.class)
                .addScalar("state", String.class)
                .addScalar("state_count", Long.class)
                .addSynchronizedEntityClass(Emotion.class);
        // 외부 쓰기 트랜잭션의 AUTO flush와 읽기 전용 트랜잭션의 flush 모드를 그대로 따른다.
        if (filter.scope() == EmotionSearchScope.GROUP) {
            query.addSynchronizedEntityClass(GroupStamp.class).addSynchronizedEntityClass(Group.class);
        }
        return query;
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
