package com.pheeeew.pressrace.v2.domain;

public record PressRanker(String publicId, String nickname, long score, Long rank, boolean participating) {

    private static final String ANONYMOUS = "익명";

    public static PressRanker of(PressParticipant participant, long rank) {
        String nickname = participant.nickname() == null ? ANONYMOUS : participant.nickname();
        return new PressRanker(participant.publicId().toString(), nickname, participant.score(), rank, true);
    }

    public static PressRanker absent() {
        return new PressRanker(null, null, 0, null, false);
    }
}
