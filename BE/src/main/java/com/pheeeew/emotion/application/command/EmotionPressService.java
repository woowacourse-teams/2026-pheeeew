package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.emotion.domain.repository.projection.DevicePressSum;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionPressService {

    private static final String DEADLOCK_DETECTED_SQL_STATE = "40P01";

    private final DeviceDailyPressRepository deviceDailyPressRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionPressMetrics emotionPressMetrics;
    private final Clock clock;

    @Transactional
    public EmotionPressResult press(UUID devicePublicId, Map<EmotionState, Integer> counts) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        PressCounts pressCounts = PressCounts.from(counts);
        increaseInLockOrder(today, deviceId, pressCounts);
        emotionPressMetrics.recordApplied(pressCounts);

        return EmotionPressResult.from(pressesOf(today, deviceId));
    }

    private void increaseInLockOrder(LocalDate pressDate, Long deviceId, PressCounts pressCounts) {
        try {
            for (Map.Entry<EmotionState, Integer> press : pressCounts.presses().entrySet()) {
                deviceDailyPressRepository.increase(
                        pressDate, press.getKey().name(), deviceId, press.getValue(), clock.instant());
            }
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

    private Map<EmotionState, Long> pressesOf(LocalDate pressDate, Long deviceId) {
        Map<EmotionState, Long> counts = emptyCounts();
        List<DevicePressSum> pressed = deviceDailyPressRepository.findByPressDateAndDeviceId(pressDate, deviceId);
        for (DevicePressSum press : pressed) {
            counts.put(press.state(), press.pressCount());
        }

        return counts;
    }

    private Map<EmotionState, Long> emptyCounts() {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            counts.put(state, 0L);
        }

        return counts;
    }
}
