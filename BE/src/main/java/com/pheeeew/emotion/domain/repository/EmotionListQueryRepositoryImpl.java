package com.pheeeew.emotion.domain.repository;

import static com.pheeeew.emotion.domain.QEmotion.emotion;
import static com.pheeeew.groups.domain.QGroupStamp.groupStamp;
import static com.pheeeew.report.domain.QDeviceBlock.deviceBlock;
import static com.pheeeew.report.domain.QEmotionBlock.emotionBlock;

import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EmotionListQueryRepositoryImpl implements EmotionListQueryRepository {

    private final JPAQueryFactory queryFactory;

    public EmotionListQueryRepositoryImpl(EntityManager entityManager) {
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
        return queryFactory.selectFrom(emotion)
                .where(
                        emotion.deletedAt.isNull(),
                        contentMatches(filter.contentType()),
                        createdAtOrAfter(filter.startInclusive()),
                        createdBefore(filter.endExclusive()),
                        scopeMatches(filter, deviceId),
                        emotion.createdAt.loe(snapshotAt),
                        beforeCursor(lastCreatedAt, lastId),
                        emotionNotBlockedBy(deviceId),
                        authorNotBlockedBy(deviceId)
                )
                .orderBy(emotion.createdAt.desc(), emotion.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression contentMatches(EmotionContentFilter contentType) {
        return switch (contentType) {
            case ALL -> emotion.content.memo.isNotNull().or(emotion.content.audio.objectKey.isNotNull());
            case MEMO -> emotion.content.memo.isNotNull();
            case AUDIO -> emotion.content.audio.objectKey.isNotNull();
        };
    }

    private BooleanExpression createdAtOrAfter(Instant startInclusive) {
        return startInclusive == null ? null : emotion.createdAt.goe(startInclusive);
    }

    private BooleanExpression createdBefore(Instant endExclusive) {
        return endExclusive == null ? null : emotion.createdAt.lt(endExclusive);
    }

    private BooleanExpression scopeMatches(EmotionSearchFilter filter, Long deviceId) {
        return switch (filter.scope()) {
            case ALL -> null;
            case GROUP -> hasGroupStamp(filter.groupId());
            case MY -> emotion.deviceId.eq(deviceId);
        };
    }

    private BooleanExpression hasGroupStamp(UUID groupId) {
        return JPAExpressions.selectOne().from(groupStamp)
                .where(groupStamp.id.eq(emotion.groupStamp.id), groupStamp.group.publicId.eq(groupId))
                .exists();
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
