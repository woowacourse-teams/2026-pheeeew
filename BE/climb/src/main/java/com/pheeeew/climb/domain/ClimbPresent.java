package com.pheeeew.climb.domain;

/** 접속 하나(n)의 최신 자세. seq 는 새 자세를 받을 때마다 오른다. */
public record ClimbPresent(int n, long seq, ClimbPose pose) {
}
