package com.pheeeew.pressevent.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.EVENT_NOT_FOUND;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.EVENT_NOT_OPEN;
import static com.pheeeew.pressevent.exception.PressEventErrorCode.REQUEST_CONFLICT;

import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.pressevent.application.PressEventRankingResult.MyRank;
import com.pheeeew.pressevent.application.PressEventRankingResult.Standing;
import com.pheeeew.pressevent.domain.PressEvent;
import com.pheeeew.pressevent.domain.PressEventCounts;
import com.pheeeew.pressevent.infra.PressEventRepository;
import com.pheeeew.pressevent.infra.PressEventRepository.SavedAttempt;
import com.pheeeew.pressrace.application.PressRaceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PressEventService {

    private final DeviceRepository devices;
    private final PressEventRepository events;
    private final PressRaceService pressRaceService;

    @Transactional
    public PressEventAttemptResult submit(UUID eventId, UUID devicePublicId, UUID requestId, PressEventCounts counts) {
        long deviceId = findDeviceId(devicePublicId);
        pressRaceService.lockEventPress(deviceId);
        PressEvent event = events.findEventForSubmission(eventId).orElseThrow(PressEventService::eventNotFound);
        String normalized = counts.normalized();
        SavedAttempt existing = events.findAttempt(eventId, deviceId, requestId).orElse(null);
        if (existing != null) {
            if (!existing.normalizedCounts().equals(normalized)) {
                throw new PheeeewException(REQUEST_CONFLICT, null);
            }
            return existing.result();
        }

        Instant now = events.now();
        if (!event.accepts(now)) {
            throw new PheeeewException(EVENT_NOT_OPEN, null);
        }
        if (counts.total() > 0) {
            pressRaceService.applyEventPress(deviceId, counts.counts(), now);
        }
        events.updateBest(eventId, deviceId, counts.total(), now);
        MyRank myRank = events.findMyRank(eventId, deviceId).orElseThrow();
        PressEventAttemptResult result = PressEventAttemptResult.of(
                requestId, counts.total(), myRank.bestScore(), myRank.rank(), myRank.rankedAt(), false);
        events.saveAttempt(eventId, deviceId, normalized, now, result);
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PressEventRankingResult findRanking(UUID eventId, UUID devicePublicId, String cursor, int limit) {
        PressEvent event = events.findEvent(eventId).orElseThrow(PressEventService::eventNotFound);
        long deviceId = findDeviceId(devicePublicId);
        PressEventCursor boundary = PressEventCursor.decode(eventId, cursor);
        List<Standing> rows = events.findPage(eventId, deviceId, boundary, limit + 1);
        List<Standing> items = rows.subList(0, Math.min(rows.size(), limit));
        String nextCursor = rows.size() > limit ? PressEventCursor.encode(eventId, items.getLast()) : null;
        Standing leader = events.findLeader(eventId, deviceId).orElse(null);
        MyRank me = events.findMyRank(eventId, deviceId).orElse(null);
        return PressEventRankingResult.of(leader, List.copyOf(items), nextCursor, me, event.observedAt());
    }

    private long findDeviceId(UUID devicePublicId) {
        return devices.findByPublicId(devicePublicId).map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private static PheeeewException eventNotFound() {
        return new PheeeewException(EVENT_NOT_FOUND, null);
    }
}
