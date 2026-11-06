package com.pheeeew.collection.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.collection.application.dto.CollectionCardDetailResult;
import com.pheeeew.collection.application.dto.CollectionDailyResult;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CollectionDailyQueryService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final DeviceRepository devices;
    private final CollectionProgressRepository progress;
    private final CollectionAssetService assets;
    private final CollectionEmotionQueryService emotions;
    private final Clock clock;

    public CollectionDailyResult findDaily(UUID devicePublicId, LocalDate date, String encodedCursor) {
        var device = devices.findByPublicId(devicePublicId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        if (date == null || date.getYear() < 1 || date.getYear() > 9999
                || date.isAfter(LocalDate.ofInstant(clock.instant(), KOREA))) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, null);
        }

        var activity = progress.findTodayByDateAndDeviceId(date, device.getId());

        long stampCount = activity.stream().mapToLong(row -> countOrZero(row.stampCount())).sum();
        long pressCount = activity.stream().mapToLong(row -> countOrZero(row.pressCount())).sum();
        var card = activity.stream().filter(row -> row.card() != null).findFirst().map(row ->
                CollectionCardDetailResult.of(row.card().publicId(), row.card().date(), row.state(), stampCount, pressCount,
                        assets.resolveImages(row.card().version(), row.card().paths()))).orElse(null);
        var page = emotions.findByDate(device, date, encodedCursor);

        return CollectionDailyResult.of(date, card, pressCount, page);
    }

    private long countOrZero(Long count) {
        return count == null ? 0 : count;
    }
}
