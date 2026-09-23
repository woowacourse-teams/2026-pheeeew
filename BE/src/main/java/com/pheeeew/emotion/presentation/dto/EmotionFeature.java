package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionMapItem;
import com.pheeeew.emotion.application.dto.EmotionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SighFeature")
public record EmotionFeature<P>(
        @Schema(example = "Feature")
        String type,

        @Schema(description = "한숨 ID", example = "42")
        Long id,

        PointGeometry geometry,

        P properties
) {

    private static final String FEATURE_TYPE = "Feature";

    public static <P> EmotionFeature<P> of(EmotionResult emotion, P properties) {
        return new EmotionFeature<>(
                FEATURE_TYPE,
                emotion.id(),
                PointGeometry.of(emotion.longitude(), emotion.latitude()),
                properties
        );
    }

    public static <P> EmotionFeature<P> of(EmotionMapItem emotion, P properties) {
        return new EmotionFeature<>(
                FEATURE_TYPE,
                emotion.id(),
                PointGeometry.of(emotion.longitude(), emotion.latitude()),
                properties
        );
    }
}
