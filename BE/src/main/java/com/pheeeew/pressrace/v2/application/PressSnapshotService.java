package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressSnapshot;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.domain.repository.PressSnapshotRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@Service
public class PressSnapshotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PressSnapshotRepository pressSnapshotRepository;

    public PressScreen findSnapshot(UUID devicePublicId, PressView view) {
        Instant now = pressSnapshotRepository.findNow();
        LocalDate today = LocalDate.ofInstant(now, KST);

        PressSnapshot snapshot = PressSnapshot.of(today, now, pressSnapshotRepository.findParticipants(today));

        return snapshot.findScreen(devicePublicId, view);
    }
}
