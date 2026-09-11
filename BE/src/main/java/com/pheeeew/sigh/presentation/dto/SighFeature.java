package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.application.dto.SighResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SighFeature<P>(
        @Schema(example = "Feature")
        String type,

        @Schema(description = "한숨 ID", example = "42")
        Long id,

        PointGeometry geometry,

        P properties
) {

    private static final String FEATURE_TYPE = "Feature";

    public static <P> SighFeature<P> of(SighResult sigh, P properties) {
        return new SighFeature<>(
                FEATURE_TYPE,
                sigh.id(),
                PointGeometry.of(sigh.longitude(), sigh.latitude()),
                properties
        );
    }

    public static <P> SighFeature<P> of(SighMapItem sigh, P properties) {
        return new SighFeature<>(
                FEATURE_TYPE,
                sigh.id(),
                PointGeometry.of(sigh.longitude(), sigh.latitude()),
                properties
        );
    }
}
