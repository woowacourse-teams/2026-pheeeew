package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_LOCATION_OUT_OF_SERVICE_AREA;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.EmotionPressMetrics;
import com.pheeeew.emotion.application.dto.EmotionPressResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.PressCounts;
import com.pheeeew.emotion.domain.repository.DeviceRegionDailyPressRepository;
import com.pheeeew.emotion.domain.repository.projection.DeviceRegionPressSum;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.region.application.RegionClassifier;
import io.micrometer.core.instrument.Timer;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionPressService {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String DEADLOCK_DETECTED_SQL_STATE = "40P01";

    private final DeviceRegionDailyPressRepository deviceRegionDailyPressRepository;
    private final DeviceRepository deviceRepository;
    private final RegionClassifier regionClassifier;
    private final EmotionPressMetrics emotionPressMetrics;
    private final Clock clock;

    @Transactional
    public EmotionPressResult press(
            UUID devicePublicId,
            double longitude,
            double latitude,
            Map<EmotionState, Integer> counts
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Point location = WGS84.createPoint(new Coordinate(longitude, latitude));
        String regionCode = classifyRegion(location);
        LocalDate today = LocalDate.now(clock);
        PressCounts pressCounts = PressCounts.from(counts);
        increaseInLockOrder(today, regionCode, deviceId, pressCounts);
        emotionPressMetrics.recordApplied(pressCounts);

        return EmotionPressResult.of(regionCode, pressesOf(today, regionCode, deviceId));
    }

    private String classifyRegion(Point location) {
        Timer.Sample sample = emotionPressMetrics.startClassify();
        String regionCode;
        try {
            regionCode = regionClassifier.classify(location).regionCode();
        } catch (RuntimeException exception) {
            emotionPressMetrics.recordClassifyFailure(sample);
            throw exception;
        }
        emotionPressMetrics.recordClassify(sample, regionCode != null);
        if (regionCode == null) {
            emotionPressMetrics.recordRegionUnassigned();
            throw new EmotionException(EMOTION_LOCATION_OUT_OF_SERVICE_AREA);
        }

        return regionCode;
    }

    private void increaseInLockOrder(LocalDate pressDate, String regionCode, Long deviceId, PressCounts pressCounts) {
        try {
            for (Map.Entry<EmotionState, Integer> press : pressCounts.presses().entrySet()) {
                deviceRegionDailyPressRepository.increase(
                        pressDate, regionCode, press.getKey().name(), deviceId, press.getValue(), clock.instant());
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

    private Map<EmotionState, Long> pressesOf(LocalDate pressDate, String regionCode, Long deviceId) {
        Map<EmotionState, Long> counts = emptyCounts();
        List<DeviceRegionPressSum> pressed = deviceRegionDailyPressRepository
                .findByPressDateAndRegionCodeAndDeviceId(pressDate, regionCode, deviceId);
        for (DeviceRegionPressSum press : pressed) {
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
