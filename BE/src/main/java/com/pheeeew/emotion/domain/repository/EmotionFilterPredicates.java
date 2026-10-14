package com.pheeeew.emotion.domain.repository;

import static com.pheeeew.emotion.domain.QEmotion.emotion;
import static com.pheeeew.groups.domain.QGroupStamp.groupStamp;

import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import java.time.Instant;
import java.util.UUID;

final class EmotionFilterPredicates {

    private EmotionFilterPredicates() {
    }

    static BooleanExpression contentMatches(EmotionContentFilter contentType) {
        return switch (contentType) {
            case ALL -> emotion.content.memo.isNotNull().or(emotion.content.audio.objectKey.isNotNull());
            case MEMO -> emotion.content.memo.isNotNull();
            case AUDIO -> emotion.content.audio.objectKey.isNotNull();
        };
    }

    static BooleanExpression createdAtOrAfter(Instant startInclusive) {
        return startInclusive == null ? null : emotion.createdAt.goe(startInclusive);
    }

    static BooleanExpression createdBefore(Instant endExclusive) {
        return endExclusive == null ? null : emotion.createdAt.lt(endExclusive);
    }

    static BooleanExpression scopeMatches(EmotionSearchFilter filter, Long deviceId) {
        return switch (filter.scope()) {
            case ALL -> null;
            case GROUP -> hasGroupStamp(filter.groupId());
            case MY -> emotion.deviceId.eq(deviceId);
        };
    }

    static BooleanExpression hasGroupStamp(UUID groupId) {
        return JPAExpressions.selectOne().from(groupStamp)
                .where(groupStamp.id.eq(emotion.groupStamp.id), groupStamp.group.publicId.eq(groupId))
                .exists();
    }

}
