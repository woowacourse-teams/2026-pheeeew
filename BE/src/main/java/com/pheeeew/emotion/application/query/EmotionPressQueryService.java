package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.DeviceRegionDailyPressRepository;
import com.pheeeew.emotion.domain.repository.projection.DeviceRegionPressSum;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionPressQueryService {

    private final DeviceRegionDailyPressRepository deviceRegionDailyPressRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public EmotionPressDailyResult findMyDailyPresses(UUID devicePublicId, int daysAgo) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        LocalDate pressDate = pressDateOf(daysAgo);

        Map<EmotionState, Long> counts = emptyCounts();
        List<DeviceRegionPressSum> pressed =
                deviceRegionDailyPressRepository.findByPressDateAndDeviceId(pressDate, deviceId);
        for (DeviceRegionPressSum press : pressed) {
            counts.put(press.state(), press.pressCount());
        }

        return EmotionPressDailyResult.of(pressDate, counts);
    }

    public EmotionPressTotalResult findDailyTotal(int daysAgo) {
        LocalDate pressDate = pressDateOf(daysAgo);

        return EmotionPressTotalResult.of(
                pressDate,
                deviceRegionDailyPressRepository.sumByPressDate(pressDate)
        );
    }

    private LocalDate pressDateOf(int daysAgo) {
        return LocalDate.now(clock).minusDays(daysAgo);
    }

    private Map<EmotionState, Long> emptyCounts() {
        Map<EmotionState, Long> counts = new EnumMap<>(EmotionState.class);
        for (EmotionState state : EmotionState.values()) {
            counts.put(state, 0L);
        }

        return counts;
    }
}
