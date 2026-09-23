package com.pheeeew.emotion.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PointGeometry(
        @Schema(example = "Point")
        String type,

        @Schema(description = "GeoJSON 좌표. 경도, 위도 순서입니다.", example = "[126.9774, 37.5669]")
        List<Double> coordinates
) {

    private static final String POINT_TYPE = "Point";

    public static PointGeometry of(double longitude, double latitude) {
        return new PointGeometry(POINT_TYPE, List.of(longitude, latitude));
    }
}
