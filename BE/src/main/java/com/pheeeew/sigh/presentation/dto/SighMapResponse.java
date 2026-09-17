package com.pheeeew.sigh.presentation.dto;

import com.pheeeew.sigh.application.dto.SighMapResult;
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

    public static SighMapResponse from(SighMapResult result) {
        List<SighFeature<SighV1Properties>> features = result.sighs().stream()
                .map(sigh -> SighFeature.of(
                        sigh,
                        SighV1Properties.from(sigh.createdAt())
                ))
                .toList();

        return new SighMapResponse(FEATURE_COLLECTION_TYPE, result.truncated(), features);
    }
}
