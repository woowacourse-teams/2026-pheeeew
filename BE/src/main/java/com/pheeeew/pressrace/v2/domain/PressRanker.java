package com.pheeeew.pressrace.v2.domain;

import com.pheeeew.emotion.domain.EmotionState;

public record PressRanker(String publicId, String nickname, long score, Long rank, boolean participating,
                          EmotionState representativeState) {

    private static final String ANONYMOUS = "익명";

    public static PressRanker of(PressParticipant participant, long rank) {
        String nickname = participant.nickname() == null ? ANONYMOUS : participant.nickname();
        return new PressRanker(participant.publicId().toString(), nickname, participant.score(), rank, true,
                participant.representativeState());
    }

    public static PressRanker absent() {
        return new PressRanker(null, null, 0, null, false, null);
    }
}
