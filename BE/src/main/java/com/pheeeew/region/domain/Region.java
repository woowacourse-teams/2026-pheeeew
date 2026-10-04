package com.pheeeew.region.domain;

public record Region(String code, RegionLevel level, String name, String parentCode, double longitude, double latitude) {

    public static Region of(String code, RegionLevel level, String name, String parentCode, double longitude, double latitude) {
        return new Region(code, level, name, parentCode, longitude, latitude);
    }
}
