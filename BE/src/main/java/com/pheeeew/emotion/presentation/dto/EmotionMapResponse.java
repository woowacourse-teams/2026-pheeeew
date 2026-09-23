package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.dto.EmotionMapResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "SighMapResponse")
public record EmotionMapResponse(
        @Schema(example = "FeatureCollection")
        String type,

        @Schema(description = "현재 영역의 조회 결과가 500건을 초과해 일부만 반환됐는지 여부", example = "false")
        boolean truncated,

        List<EmotionFeature<EmotionV1Properties>> features
) {

    private static final String FEATURE_COLLECTION_TYPE = "FeatureCollection";

    public static EmotionMapResponse from(EmotionMapResult result) {
        List<EmotionFeature<EmotionV1Properties>> features = result.emotions().stream()
                .map(emotion -> EmotionFeature.of(
                        emotion,
                        EmotionV1Properties.from(emotion.createdAt())
                ))
                .toList();

        return new EmotionMapResponse(FEATURE_COLLECTION_TYPE, result.truncated(), features);
    }
}
