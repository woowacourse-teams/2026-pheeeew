package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_DATE_CHANGED;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.collection.application.dto.CollectionTodayResult.Item;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CollectionQueryService {

    private static final int MAX_READ_ATTEMPTS = 2;

    private final CollectionProgressRepository collectionProgressRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public CollectionTodayResult findToday(UUID devicePublicId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        for (int attempt = 0; attempt < MAX_READ_ATTEMPTS; attempt++) {
            LocalDate date = LocalDate.now(clock);
            CollectionTodayResult result = findByDate(date, deviceId);
            if (date.equals(LocalDate.now(clock))) {
                return result;
            }
        }

        throw new CollectionException(COLLECTION_DATE_CHANGED);
    }

    private CollectionTodayResult findByDate(LocalDate date, Long deviceId) {
        Map<EmotionState, Item> progress = new EnumMap<>(EmotionState.class);
        collectionProgressRepository.findByDateAndDeviceId(date, deviceId)
                .forEach(counts -> progress.put(counts.state(), Item.of(counts.state(), counts.stampCount(), counts.pressCount())));

        return CollectionTodayResult.of(date, Arrays.stream(EmotionState.values())
                .map(state -> progress.getOrDefault(state, Item.of(state, 0, 0)))
                .toList());
    }
}
