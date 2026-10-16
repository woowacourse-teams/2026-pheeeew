package com.pheeeew.emotion.domain.repository;

import static com.pheeeew.emotion.domain.QEmotion.emotion;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.contentMatches;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.createdAtOrAfter;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.createdBefore;
import static com.pheeeew.emotion.domain.repository.EmotionFilterPredicates.scopeMatches;
import static com.pheeeew.report.domain.QDeviceBlock.deviceBlock;
import static com.pheeeew.report.domain.QEmotionBlock.emotionBlock;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

public class EmotionListQueryRepositoryImpl implements EmotionListQueryRepository {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public EmotionListQueryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Emotion> findVisiblePageWithoutBounds(
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            int limit
    ) {
        return visibleQuery(snapshotAt, lastCreatedAt, lastId, deviceId, filter, true)
                .limit(limit)
                .fetch();
    }

    @Override
    public List<Emotion> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            UUID groupId,
            boolean contentOnly,
            int limit
    ) {
        EmotionSearchFilter filter = EmotionSearchFilter.of(null, null, null, null, groupId);
        return visibleQuery(snapshotAt, lastCreatedAt, lastId, deviceId, filter, contentOnly)
                .where(withinBounds(bounds))
                .limit(limit)
                .fetch();
    }

    @Override
    public List<Emotion> findVisiblePageWithinBounds(
            EmotionSearchBounds bounds,
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            int limit
    ) {
        forceCustomPlanForMapTransaction();
        return visibleQuery(snapshotAt, lastCreatedAt, lastId, deviceId, filter, true)
                .where(withinBounds(bounds))
                .limit(limit)
                .fetch();
    }

    private void forceCustomPlanForMapTransaction() {
        // 외부 트랜잭션에 참여한 경우에도 물리 트랜잭션이 종료될 때 복원된다.
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL plan_cache_mode = 'force_custom_plan'");
            }
        });
    }

    private JPAQuery<Emotion> visibleQuery(
            Instant snapshotAt,
            Instant lastCreatedAt,
            long lastId,
            Long deviceId,
            EmotionSearchFilter filter,
            boolean contentOnly
    ) {
        return queryFactory.selectFrom(emotion)
                .where(
                        emotion.deletedAt.isNull(),
                        contentOnly ? contentMatches(filter.contentType()) : null,
                        createdAtOrAfter(filter.startInclusive()),
                        createdBefore(filter.endExclusive()),
                        scopeMatches(filter, deviceId),
                        emotion.createdAt.loe(snapshotAt),
                        beforeCursor(lastCreatedAt, lastId),
                        emotionNotBlockedBy(deviceId),
                        authorNotBlockedBy(deviceId)
                )
                .orderBy(emotion.createdAt.desc(), emotion.id.desc());
    }

    private BooleanExpression withinBounds(EmotionSearchBounds bounds) {
        if (bounds.minLongitude() < bounds.maxLongitude()) {
            return intersects(bounds.minLongitude(), bounds.maxLongitude(), bounds.minLatitude(), bounds.maxLatitude());
        }
        return intersects(bounds.minLongitude(), 180, bounds.minLatitude(), bounds.maxLatitude())
                .or(intersects(-180, bounds.maxLongitude(), bounds.minLatitude(), bounds.maxLatitude()));
    }

    private BooleanExpression intersects(double west, double east, double south, double north) {
        Geometry area = GEOMETRY_FACTORY.toGeometry(new Envelope(west, east, south, north));
        return Expressions.booleanTemplate("st_intersects({0}, {1}) = true", emotion.location, area);
    }

    private BooleanExpression beforeCursor(Instant lastCreatedAt, long lastId) {
        return emotion.createdAt.lt(lastCreatedAt)
                .or(emotion.createdAt.eq(lastCreatedAt).and(emotion.id.lt(lastId)));
    }

    private BooleanExpression emotionNotBlockedBy(Long deviceId) {
        return JPAExpressions.selectOne().from(emotionBlock)
                .where(emotionBlock.blockerDeviceId.eq(deviceId), emotionBlock.emotionId.eq(emotion.id))
                .notExists();
    }

    private BooleanExpression authorNotBlockedBy(Long deviceId) {
        return JPAExpressions.selectOne().from(deviceBlock)
                .where(deviceBlock.blockerDeviceId.eq(deviceId), deviceBlock.blockedDeviceId.eq(emotion.deviceId))
                .notExists();
    }
}
