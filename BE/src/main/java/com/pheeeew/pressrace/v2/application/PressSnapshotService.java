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
        return findTodaySnapshot().findScreen(devicePublicId, view);
    }

    /** DB 시각의 KST 날짜로 만든 오늘의 공통 순위. */
    public PressSnapshot findTodaySnapshot() {
        Instant now = pressSnapshotRepository.findNow();
        LocalDate today = LocalDate.ofInstant(now, KST);

        return PressSnapshot.of(today, now, pressSnapshotRepository.findParticipants(today));
    }

    /** 지정한 KST 날짜의 공통 순위. 활동 인원은 지금의 DB 시각으로 판정한다. */
    public PressSnapshot findSnapshotOn(LocalDate pressDate) {
        Instant now = pressSnapshotRepository.findNow();

        return PressSnapshot.of(pressDate, now, pressSnapshotRepository.findParticipants(pressDate));
    }
}
