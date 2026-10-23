package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_ACTIVITY_COUNTS_MISSING;
import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_CARD_NOT_FOUND;
import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_DATE_CHANGED;
import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_INVALID_LIMIT;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.collection.application.dto.CollectionCardDetailResult;
import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.application.dto.CollectionHistoryCursor;
import com.pheeeew.collection.application.dto.CollectionHistoryResult;
import com.pheeeew.collection.application.dto.CollectionTodayResult.Item;
import com.pheeeew.collection.application.dto.CollectionTodayResult;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.domain.repository.projection.CollectionCardDetail;
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

    public CollectionHistoryResult findHistory(UUID devicePublicId, EmotionState emotion, Integer limit, String encodedCursor) {
        Device device = deviceRepository.findByPublicId(devicePublicId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Long deviceId = device.getId();

        int pageSize = limit == null ? 20 : limit;
        if (pageSize < 1 || pageSize > 50) {
            throw new CollectionException(COLLECTION_INVALID_LIMIT);
        }

        LocalDate today = LocalDate.now(clock);
        var cursor = encodedCursor == null ? CollectionHistoryCursor.initial(today, emotion)
                : CollectionHistoryCursorCodec.decode(encodedCursor);
        if (encodedCursor != null) {
            cursor.validateRequest(today, emotion);
        }

        var history = collectionProgressRepository.findHistory(deviceId, cursor.beforeDate(), cursor.lastDate(),
                cursor.lastId(), cursor.emotion(), pageSize + 1);

        var rows = history.cards();
        boolean hasNext = rows.size() > pageSize;
        var page = rows.subList(0, Math.min(rows.size(), pageSize));
        var items = page.stream().map(row -> toDetailResult(row.detail())).toList();
        String nextCursor = null;
        if (hasNext) {
            var last = page.getLast();
            nextCursor = CollectionHistoryCursorCodec.encode(cursor.next(last.detail().date(), last.id()));
        }

        return CollectionHistoryResult.of(items, hasNext, nextCursor, history.counts(), device.getNickname());
    }

    public CollectionCardDetailResult findDetail(UUID devicePublicId, UUID cardId) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        var card = collectionProgressRepository.findDetail(cardId, deviceId)
                .orElseThrow(() -> new CollectionException(COLLECTION_CARD_NOT_FOUND));

        return toDetailResult(card);
    }

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

    private CollectionCardDetailResult toDetailResult(CollectionCardDetail card) {
        validateCardCounts(card.stampCount(), card.pressCount());
        return CollectionCardDetailResult.of(card.publicId(), card.date(), card.state(),
                card.stampCount(), card.pressCount(), assetService.resolveImages(card.version(), card.paths()));
    }

    private CollectionTodayResult findByDate(LocalDate date, Long deviceId) {
        Map<EmotionState, Item> progress = new EnumMap<>(EmotionState.class);
        collectionProgressRepository.findTodayByDateAndDeviceId(date, deviceId)
                .forEach(row -> {
                    if (row.card() != null) {
                        validateCardCounts(row.stampCount(), row.pressCount());
                    }
                    var card = row.card() == null ? null : CollectionCardResult.of(row.card(), row.state(),
                            assetService.resolveImages(row.card().version(), row.card().paths()));
                    progress.put(row.state(), Item.of(row.state(), countOrZero(row.stampCount()),
                            countOrZero(row.pressCount()), card));
                });

        return CollectionTodayResult.of(date, Arrays.stream(EmotionState.values())
                .map(state -> progress.getOrDefault(state, Item.of(state, 0, 0)))
                .toList());
    }

    private void validateCardCounts(Long stampCount, Long pressCount) {
        if (stampCount == null || pressCount == null) {
            throw new CollectionException(COLLECTION_ACTIVITY_COUNTS_MISSING);
        }
    }

    private long countOrZero(Long count) {
        return count == null ? 0 : count;
    }
}
