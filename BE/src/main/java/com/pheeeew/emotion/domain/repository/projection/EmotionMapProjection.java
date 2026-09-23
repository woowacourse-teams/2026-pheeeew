package com.pheeeew.emotion.domain.repository.projection;

import java.time.Instant;

public interface EmotionMapProjection {

    Long getId();

    double getLongitude();

    double getLatitude();

    Instant getCreatedAt();
}
