package com.pheeeew.sigh.application.dto;

import com.pheeeew.sigh.domain.Sigh;
import java.time.Instant;

public record SighResult(
        Long id,
        double longitude,
        double latitude,
        Instant createdAt,
        String memo,
        String nickname
) {

    public static SighResult from(Sigh sigh) {
        return new SighResult(
                sigh.getId(),
                sigh.getLongitude(),
                sigh.getLatitude(),
                sigh.getCreatedAt(),
                sigh.getMemo(),
                sigh.getNickname()
        );
    }

    public static SighResult of(
            Long id,
            double longitude,
            double latitude,
            Instant createdAt,
            String memo,
            String nickname
    ) {
        return new SighResult(id, longitude, latitude, createdAt, memo, nickname);
    }
}
