package com.pheeeew.pressrace.v2.domain;

import java.util.ArrayList;
import java.util.List;

public record PressRankRange(int startIndex, long firstRank, List<PressParticipant> participants) {
    public static PressRankRange of(int startIndex, long firstRank, List<PressParticipant> participants) {
        return new PressRankRange(startIndex, firstRank, participants);
    }

    public List<PressRanker> rankers() {
        List<PressRanker> rankers = new ArrayList<>();
        long rank = firstRank;
        for (int index = 0; index < participants.size(); index++) {
            PressParticipant participant = participants.get(index);
            if (index > 0 && participant.score() != participants.get(index - 1).score()) {
                rank = startIndex + index + 1L;
            }
            rankers.add(PressRanker.of(participant, rank));
        }
        return List.copyOf(rankers);
    }
}
