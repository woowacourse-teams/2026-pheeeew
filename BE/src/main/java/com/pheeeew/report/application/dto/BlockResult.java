package com.pheeeew.report.application.dto;

import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import com.pheeeew.emotion.domain.Emotion;
import java.time.Instant;

public record BlockResult(Long blockId, Long emotionId, String nickname, String memo, Instant createdAt) {

    public static BlockResult from(BlockProjection projection) {
        return new BlockResult(
                projection.getBlockId(),
                projection.getEmotionId(),
                projection.getNickname(),
                projection.getMemo(),
                projection.getCreatedAt()
        );
    }

    public static BlockResult of(EmotionBlock block, Emotion emotion, String authorNickname) {
        String nickname = "익명";
        if (!emotion.isAnonymous() && authorNickname != null) {
            nickname = authorNickname;
        }

        return new BlockResult(
                block.getId(),
                block.getEmotionId(),
                nickname,
                emotion.getMemo(),
                block.getCreatedAt()
        );
    }

    public static BlockResult of(DeviceBlock block, Emotion originEmotion, String authorNickname) {
        String nickname = "익명";
        if (!originEmotion.isAnonymous() && authorNickname != null) {
            nickname = authorNickname;
        }

        return new BlockResult(
                block.getId(),
                block.getOriginEmotionId(),
                nickname,
                originEmotion.getMemo(),
                block.getCreatedAt()
        );
    }
}
