package com.pheeeew.sigh.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_EXPIRED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_INVALID_CURSOR;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_SAVE_FAILED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.dto.SighDetailResult;
import com.pheeeew.sigh.application.dto.EmotionListCursor;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Emotion;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.sigh.domain.repository.projection.EmotionListProjection;
import com.pheeeew.sigh.domain.repository.projection.EmotionMapProjection;
import com.pheeeew.sigh.domain.repository.query.EmotionQueryPeriod;
import com.pheeeew.sigh.domain.repository.query.SighSearchBounds;
import com.pheeeew.sigh.exception.SighException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionService {

    private static final int MAX_FIND_COUNT = 500;
    private static final int LIST_PAGE_SIZE = 20;

    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionLocationGenerator emotionLocationGenerator;
    private final EmotionNicknameGenerator emotionNicknameGenerator;
    private final Clock clock;

    public SighSaveResult save(UUID requestId, double longitude, double latitude) {
        return saveEmotion(requestId, longitude, latitude, null, null);
    }

    public SighSaveResult save(UUID requestId, double longitude, double latitude, String memo, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        return saveEmotion(requestId, longitude, latitude, memo, deviceId);
    }

    @Transactional(readOnly = true)
    public SighDetailResult findById(Long id, UUID devicePublicId) {
        Instant queriedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Long deviceId = findDeviceId(devicePublicId);
        EmotionDetailProjection projection = emotionRepository.findById(id, deviceId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));

        EmotionQueryPeriod period = EmotionQueryPeriod.of(queriedAt, clock.getZone());
        if (projection.getEmotion().getCreatedAt().isBefore(period.startAt())) {
            throw new SighException(SIGH_EXPIRED);
        }

        return SighDetailResult.from(projection);
    }

    public SighMapResult findAllWithinBounds(SighSearchBounds bounds, Optional<UUID> viewerDevicePublicId) {
        Instant queriedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        EmotionQueryPeriod period = EmotionQueryPeriod.of(queriedAt, clock.getZone());
        Long blockerDeviceId = findBlockerDeviceId(viewerDevicePublicId);
        List<EmotionMapProjection> projections = emotionRepository.findAllWithinBounds(
                bounds, period, blockerDeviceId, MAX_FIND_COUNT + 1
        );

        boolean truncated = projections.size() > MAX_FIND_COUNT;
        if (truncated) {
            projections = projections.subList(0, MAX_FIND_COUNT);
        }

        List<SighMapItem> emotions = projections.stream()
                .map(projection -> SighMapItem.of(
                        projection.getId(),
                        projection.getLongitude(),
                        projection.getLatitude(),
                        projection.getCreatedAt()
                ))
                .toList();

        return SighMapResult.of(emotions, truncated);
    }

    public SighListResult findFirstListPage(SighSearchBounds bounds, UUID devicePublicId) {
        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        EmotionListCursor cursor = EmotionListCursor.initial(bounds, snapshotAt);
        EmotionQueryPeriod period = EmotionQueryPeriod.of(snapshotAt, clock.getZone());

        return findList(cursor, period, devicePublicId);
    }

    public SighListResult findNextListPage(String encodedCursor, UUID devicePublicId) {
        EmotionListCursor cursor = EmotionListCursorCodec.decode(encodedCursor);
        Instant queriedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (cursor.snapshotAt().isAfter(queriedAt)) {
            throw new SighException(SIGH_INVALID_CURSOR);
        }
        EmotionQueryPeriod period = EmotionQueryPeriod.of(queriedAt, clock.getZone());

        return findList(cursor, period, devicePublicId);
    }

    private SighSaveResult saveEmotion(UUID requestId, double longitude, double latitude, String memo, Long deviceId) {
        Optional<EmotionDetailProjection> existingEmotion = emotionRepository.findByRequestId(requestId, deviceId);

        if (existingEmotion.isPresent()) {
            return createSaveResult(existingEmotion.get());
        }

        return saveNewEmotion(requestId, longitude, latitude, memo, deviceId);
    }

    private SighSaveResult createSaveResult(EmotionDetailProjection projection) {
        SighDetailResult detail = SighDetailResult.from(projection);
        return SighSaveResult.of(detail.sigh(), false, detail.like());
    }

    private SighSaveResult saveNewEmotion(UUID requestId, double longitude, double latitude, String memo, Long deviceId) {
        Point location = emotionLocationGenerator.generate(longitude, latitude);
        Emotion emotion = Emotion.builder()
                .requestId(requestId)
                .location(location)
                .memo(memo)
                .nickname(emotionNicknameGenerator.generate())
                .deviceId(deviceId)
                .build();

        try {
            Emotion savedEmotion = emotionRepository.saveAndFlush(emotion);
            return SighSaveResult.of(SighResult.from(savedEmotion), true, SighLikeResult.of(false, 0));
        } catch (DataIntegrityViolationException cause) {
            return findExistingEmotion(requestId, deviceId, cause);
        }
    }

    private SighSaveResult findExistingEmotion(UUID requestId, Long deviceId, DataIntegrityViolationException cause) {
        return emotionRepository.findByRequestId(requestId, deviceId)
                .map(this::createSaveResult)
                .orElseThrow(() -> new SighException(SIGH_SAVE_FAILED, cause));
    }

    private Long findDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private Long findBlockerDeviceId(Optional<UUID> viewerDevicePublicId) {
        return viewerDevicePublicId
                .map(this::findDeviceId)
                .orElse(null);
    }

    private SighListResult findList(EmotionListCursor cursor, EmotionQueryPeriod currentPeriod, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        if (!cursor.snapshotAt().isAfter(currentPeriod.startAt())) {
            return SighListResult.of(List.of(), false, null);
        }

        SighSearchBounds bounds = cursor.bounds();
        EmotionQueryPeriod period = EmotionQueryPeriod.of(currentPeriod.startAt(), cursor.snapshotAt());
        List<EmotionListProjection> projections = emotionRepository.findListWithinBounds(
                bounds,
                period,
                cursor.lastItemCreatedAt(),
                cursor.lastId(),
                deviceId,
                MAX_FIND_COUNT,
                LIST_PAGE_SIZE + 1,
                deviceId
        );

        boolean hasNext = projections.size() > LIST_PAGE_SIZE;
        if (hasNext) {
            projections = projections.subList(0, LIST_PAGE_SIZE);
        }

        List<SighDetailResult> items = projections.stream()
                .map(SighDetailResult::from)
                .toList();
        String nextCursor = createNextCursor(cursor, projections, hasNext);

        return SighListResult.of(items, hasNext, nextCursor);
    }

    private String createNextCursor(
            EmotionListCursor cursor,
            List<EmotionListProjection> projections,
            boolean hasNext
    ) {
        if (!hasNext) {
            return null;
        }

        EmotionListProjection lastProjection = projections.getLast();
        EmotionListCursor nextCursor = cursor.next(
                lastProjection.getCreatedAt(),
                lastProjection.getId()
        );
        return EmotionListCursorCodec.encode(nextCursor);
    }
}
