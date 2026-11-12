package com.pheeeew.climb.domain;

import java.util.List;

/** 맵 버전 하나. zones 는 구간 경계 높이(오름차순)이고, 마지막 경계를 넘은 뒤 top 에 닿으면 완주다. */
public record ClimbMap(String version, int top, List<Integer> zones, int minFinishSeconds) {

    public static ClimbMap of(String version, int top, List<Integer> zones, int minFinishSeconds) {
        return new ClimbMap(version, top, List.copyOf(zones), minFinishSeconds);
    }

    int zonesBelow(int height) {
        return (int) zones.stream().filter(boundary -> boundary <= height).count();
    }
}
