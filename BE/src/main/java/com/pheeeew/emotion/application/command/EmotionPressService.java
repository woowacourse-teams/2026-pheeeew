package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.v2.application.PressRedisWriter;
import com.pheeeew.pressrace.v2.application.PressWriteBuffer;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class EmotionPressService {

    private static final String DEADLOCK_DETECTED_SQL_STATE = "40P01";

    private final DeviceRepository deviceRepository;
    private final EmotionPressMetrics emotionPressMetrics;
    private final PressRaceService pressRaceService;
    private final PressWriteBuffer pressWriteBuffer;
    private final Optional<PressRedisWriter> pressRedisWriter;

    public EmotionPressResult press(UUID devicePublicId, Map<EmotionState, Integer> counts) {
        return press(devicePublicId, counts, null);
    }

    public EmotionPressResult press(UUID devicePublicId, Map<EmotionState, Integer> counts, UUID requestId) {
        if (writesThroughDatabase() && deviceRepository.findByPublicId(devicePublicId).isEmpty()) {
            throw new DeviceException(DEVICE_NOT_FOUND);
        }
        PressCounts pressCounts = PressCounts.from(counts);
        Map<EmotionState, Long> applied = apply(devicePublicId, pressCounts, requestId);
        emotionPressMetrics.recordApplied(pressCounts.appliedTotal(), pressCounts.presses().size(),
                pressCounts.perStateDropped(), pressCounts.totalDropped());
        return EmotionPressResult.from(applied);
    }

    private boolean writesThroughDatabase() {
        return pressRedisWriter.isEmpty() && !pressWriteBuffer.isEnabled();
    }

    private Map<EmotionState, Long> apply(UUID devicePublicId, PressCounts pressCounts, UUID requestId) {
        if (pressRedisWriter.isPresent()) {
            return pressRedisWriter.get().press(devicePublicId, pressCounts, requestId);
        }
        if (pressWriteBuffer.isEnabled()) {
            return pressWriteBuffer.press(devicePublicId, pressCounts);
        }
        return pressThroughDatabase(devicePublicId, pressCounts);
    }

    private Map<EmotionState, Long> pressThroughDatabase(UUID devicePublicId, PressCounts pressCounts) {
        try {
            return pressRaceService.pressHttp(devicePublicId, pressCounts);
        } catch (DataAccessException exception) {
            if (isDeadlock(exception)) {
                emotionPressMetrics.recordDeadlock();
            }
            throw exception;
        }
    }

    private boolean isDeadlock(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();

        return cause instanceof SQLException sqlException
                && DEADLOCK_DETECTED_SQL_STATE.equals(sqlException.getSQLState());
    }
}
