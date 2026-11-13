package com.pheeeew.climb.domain;

import java.time.Instant;

/** 명예의 전당 한 줄. 완주한 순간의 닉네임·꼬리표를 그대로 둔다. best 는 가장 빠른 완주다. */
public record ClimbHallEntry(String tag, String nickname, String character, int finishSeconds, int falls,
                             Instant finishedAt, int bestSeconds, int bestFalls, Instant bestAt) {
}
