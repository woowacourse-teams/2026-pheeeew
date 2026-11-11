package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.application.PressRedisScreenReader;
import com.pheeeew.pressrace.v2.application.PressRedisTodayBoard;
import com.pheeeew.pressrace.v2.domain.PressScreenRange;
import com.pheeeew.pressrace.v2.domain.PressScreenRead;
import com.pheeeew.pressrace.v2.domain.PressTodayObservation;
import com.pheeeew.pressrace.v2.domain.PressTodayParticipant;
import com.pheeeew.pressrace.v2.domain.PressParticipant;
import com.pheeeew.pressrace.v2.domain.PressRankRange;
import com.pheeeew.pressrace.v2.domain.PressRanker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PressSnapshotService {

    private final PressDatabaseSnapshotService pressDatabaseSnapshotService;
    private final Optional<PressRedisTodayBoard> pressRedisTodayBoard;
    private final Optional<PressRedisScreenReader> pressRedisScreenReader;

    public PressScreen findSnapshot(UUID devicePublicId, PressView view) {
        if (pressRedisScreenReader.isPresent()) {
            PressScreenRead read = pressRedisScreenReader.get().read(devicePublicId, toRange(view));
            return toScreen(read, devicePublicId, view);
        }
        return findTodaySnapshot().findScreen(devicePublicId, view);
    }

    public PressSnapshot findTodaySnapshot() {
        if (pressRedisTodayBoard.isPresent()) {
            return toSnapshot(pressRedisTodayBoard.get().current());
        }
        return pressDatabaseSnapshotService.findTodaySnapshot();
    }

    public PressSnapshot findSnapshotOn(LocalDate pressDate) {
        if (pressRedisTodayBoard.isPresent()) {
            PressTodayObservation observation = pressRedisTodayBoard.get().current();
            if (observation.date().equals(pressDate)) {
                return toSnapshot(observation);
            }
        }
        return pressDatabaseSnapshotService.findSnapshotOn(pressDate);
    }

    private PressScreenRange toRange(PressView view) {
        int topSize = view == PressView.ALL ? PressSnapshot.TOP_SIZE : 0;
        return PressScreenRange.of(topSize, PressSnapshot.NEARBY_SIZE, PressSnapshot.LEADER_SIZE,
                PressSnapshot.ACTIVE_WINDOW);
    }

    private PressScreen toScreen(PressScreenRead read, UUID devicePublicId, PressView view) {
        PressRankRange nearby = PressRankRange.of(read.nearbyStart(), read.nearbyFirstRank(),
                toParticipants(read.nearby()));
        List<PressRanker> nearbyRankers = nearby.rankers();
        List<PressRanker> top100 = view == PressView.ALL
                ? PressRankRange.of(0, 1, toParticipants(read.top())).rankers()
                : List.of();
        PressRanker me = findMe(nearby, nearbyRankers, devicePublicId);

        return PressScreen.of(read.date(), read.observedAt(), read.activeCount(), view, me, nearbyRankers, top100);
    }

    private List<PressParticipant> toParticipants(List<PressTodayParticipant> participants) {
        return participants.stream()
                .map(this::toParticipant)
                .toList();
    }

    private PressRanker findMe(PressRankRange nearby, List<PressRanker> nearbyRankers, UUID devicePublicId) {
        List<PressParticipant> participants = nearby.participants();
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).devicePublicId().equals(devicePublicId)) {
                return nearbyRankers.get(index);
            }
        }
        return PressRanker.absent();
    }

    private PressSnapshot toSnapshot(PressTodayObservation observation) {
        List<PressParticipant> participants = observation.participants().stream()
                .map(this::toParticipant)
                .toList();

        return PressSnapshot.of(observation.date(), observation.observedAt(), participants);
    }

    private PressParticipant toParticipant(PressTodayParticipant participant) {
        return PressParticipant.of(participant.devicePublicId(), participant.publicId(), participant.nickname(),
                participant.total(), participant.lastAppliedAt(), participant.representativeState());
    }
}
