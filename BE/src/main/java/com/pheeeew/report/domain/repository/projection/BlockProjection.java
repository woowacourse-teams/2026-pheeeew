package com.pheeeew.report.domain.repository.projection;

import java.time.Instant;

public interface BlockProjection {

    Long getBlockId();

    Long getEmotionId();

    String getNickname();

    String getMemo();

    Instant getCreatedAt();
}
