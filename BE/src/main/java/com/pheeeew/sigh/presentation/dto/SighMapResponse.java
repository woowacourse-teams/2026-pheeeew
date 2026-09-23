package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.dto.EmotionMapResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SighMapResponse(
        @Schema(example = "FeatureCollection")
        String type,

        @Schema(description = "현재 영역의 조회 결과가 500건을 초과해 일부만 반환됐는지 여부", example = "false")
        boolean truncated,

        List<SighFeature<SighV1Properties>> features
) {

    private static final String FEATURE_COLLECTION_TYPE = "FeatureCollection";

    public static SighMapResponse from(EmotionMapResult result) {
        List<SighFeature<SighV1Properties>> features = result.emotions().stream()
                .map(emotion -> SighFeature.of(
                        emotion,
                        SighV1Properties.from(emotion.createdAt())
                ))
                .toList();

        return new SighMapResponse(FEATURE_COLLECTION_TYPE, result.truncated(), features);
    }
}
