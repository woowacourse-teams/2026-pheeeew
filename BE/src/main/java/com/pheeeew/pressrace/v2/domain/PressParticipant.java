package com.pheeeew.pressrace.v2.domain;

import java.time.Instant;
import java.util.UUID;

/** 오늘 점수가 양수인 기기. devicePublicId 는 인증 식별자라 응답에 내보내지 않는다. */
public record PressParticipant(UUID devicePublicId, UUID publicId, String nickname, long score, Instant lastAppliedAt) {

    public static PressParticipant of(UUID devicePublicId, UUID publicId, String nickname, long score,
            Instant lastAppliedAt) {
        return new PressParticipant(devicePublicId, publicId, nickname, score, lastAppliedAt);
    }
}
