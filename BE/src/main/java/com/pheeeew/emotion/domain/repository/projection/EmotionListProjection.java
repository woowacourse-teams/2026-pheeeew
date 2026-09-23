package com.pheeeew.emotion.domain.repository.projection;

import java.time.Instant;

public interface EmotionListProjection {

    Long getId();

    Double getLongitude();

    Double getLatitude();

    Instant getCreatedAt();

    String getNickname();

    String getMemo();

    boolean getLiked();

    long getLikeCount();
}
