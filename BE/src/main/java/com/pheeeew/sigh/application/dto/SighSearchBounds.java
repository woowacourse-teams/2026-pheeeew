package com.pheeeew.sigh.application.dto;

public record SighSearchBounds(
        double minLongitude,
        double minLatitude,
        double maxLongitude,
        double maxLatitude
) {

    public SighSearchBounds {
        validateLongitude(minLongitude);
        validateLongitude(maxLongitude);
        validateLatitude(minLatitude);
        validateLatitude(maxLatitude);
        if (minLongitude == maxLongitude) {
            throw new IllegalArgumentException("검색 영역의 두 경도는 달라야 합니다.");
        }
        if (minLatitude >= maxLatitude) {
            throw new IllegalArgumentException("검색 영역의 최소 위도는 최대 위도보다 작아야 합니다.");
        }
    }

    public static SighSearchBounds of(
            double minLongitude,
            double minLatitude,
            double maxLongitude,
            double maxLatitude
    ) {
        return new SighSearchBounds(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }

    private static void validateLongitude(double longitude) {
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("경도는 -180 이상 180 이하여야 합니다.");
        }
    }

    private static void validateLatitude(double latitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("위도는 -90 이상 90 이하여야 합니다.");
        }
    }
}
