package com.pheeeew.block.application.dto;

import com.pheeeew.block.domain.DeviceBlock;
import com.pheeeew.block.domain.SighBlock;
import com.pheeeew.block.domain.repository.projection.BlockProjection;
import com.pheeeew.sigh.domain.Sigh;
import java.time.Instant;

public record BlockResult(Long blockId, Long sighId, String nickname, String memo, Instant createdAt) {

    public static BlockResult from(BlockProjection projection) {
        return new BlockResult(
                projection.getBlockId(),
                projection.getSighId(),
                projection.getNickname(),
                projection.getMemo(),
                projection.getCreatedAt()
        );
    }

    public static BlockResult of(SighBlock block, Sigh sigh) {
        return new BlockResult(
                block.getId(),
                block.getSighId(),
                sigh.getNickname(),
                sigh.getMemo(),
                block.getCreatedAt()
        );
    }

    public static BlockResult of(DeviceBlock block, Sigh originSigh) {
        return new BlockResult(
                block.getId(),
                block.getOriginSighId(),
                originSigh.getNickname(),
                originSigh.getMemo(),
                block.getCreatedAt()
        );
    }
}
