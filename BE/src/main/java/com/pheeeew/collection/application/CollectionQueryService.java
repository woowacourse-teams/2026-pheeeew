package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_DATE_CHANGED;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.application.dto.CollectionTodayResult.Item;
import com.pheeeew.collection.application.dto.CollectionTodayResult;
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
    private final CollectionAssetService assetService;

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
        collectionProgressRepository.findTodayByDateAndDeviceId(date, deviceId)
                .forEach(row -> {
                    var card = row.card() == null ? null : CollectionCardResult.of(row.card(), row.state(),
                            assetService.resolveImages(row.card().version(), row.card().paths()));
                    progress.put(row.state(), Item.of(row.state(), row.stampCount(), row.pressCount(), card));
                });

        return CollectionTodayResult.of(date, Arrays.stream(EmotionState.values())
                .map(state -> progress.getOrDefault(state, Item.of(state, 0, 0)))
                .toList());
    }
}
