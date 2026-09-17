package com.pheeeew.report.domain.repository.projection;

import java.time.Instant;

public interface BlockProjection {

    Long getBlockId();

    Long getSighId();

    String getNickname();

    String getMemo();

    Instant getCreatedAt();
}
