package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionPressDailyResult;
import com.pheeeew.emotion.application.dto.EmotionPressTotalResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.emotion.domain.repository.projection.DevicePressSum;
import com.pheeeew.pressrace.v2.application.PressRedisDayReader;
import com.pheeeew.pressrace.v2.domain.PressDayRead;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionPressQueryService {

    private final DeviceDailyPressRepository deviceDailyPressRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private final Optional<PressRedisDayReader> pressRedisDayReader;

    public EmotionPressDailyResult findMyDailyPresses(UUID devicePublicId, int daysAgo) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        if (daysAgo == 0 && pressRedisDayReader.isPresent()) {
            return findMyTodayPresses(pressRedisDayReader.get(), devicePublicId);
        }
        LocalDate pressDate = pressDateOf(daysAgo);

        Map<EmotionState, Long> counts = emptyCounts();
        List<DevicePressSum> pressed =
                deviceDailyPressRepository.findByPressDateAndDeviceId(pressDate, deviceId);
        for (DevicePressSum press : pressed) {
            counts.put(press.state(), press.pressCount());
        }

        return EmotionPressDailyResult.of(pressDate, counts);
    }

    public EmotionPressTotalResult findDailyTotal(int daysAgo) {
        if (daysAgo == 0 && pressRedisDayReader.isPresent()) {
            PressDayRead read = pressRedisDayReader.get().findTotal();
            return EmotionPressTotalResult.of(read.date(), read.total());
        }
        LocalDate pressDate = pressDateOf(daysAgo);

        return EmotionPressTotalResult.of(
                pressDate,
                deviceDailyPressRepository.sumByPressDate(pressDate)
        );
    }

    private EmotionPressDailyResult findMyTodayPresses(PressRedisDayReader reader, UUID devicePublicId) {
        PressDayRead read = reader.findToday(devicePublicId);
        Map<EmotionState, Long> counts = emptyCounts();
        counts.putAll(read.counts());

        return EmotionPressDailyResult.of(read.date(), counts);
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
