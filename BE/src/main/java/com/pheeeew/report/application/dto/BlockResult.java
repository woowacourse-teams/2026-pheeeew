package com.pheeeew.report.application.dto;

import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import com.pheeeew.sigh.domain.Sigh;
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

    public static BlockResult of(EmotionBlock block, Sigh emotion) {
        return new BlockResult(
                block.getId(),
                block.getEmotionId(),
                emotion.getNickname(),
                emotion.getMemo(),
                block.getCreatedAt()
        );
    }

    public static BlockResult of(DeviceBlock block, Sigh originEmotion) {
        return new BlockResult(
                block.getId(),
                block.getOriginEmotionId(),
                originEmotion.getNickname(),
                originEmotion.getMemo(),
                block.getCreatedAt()
        );
    }
}
