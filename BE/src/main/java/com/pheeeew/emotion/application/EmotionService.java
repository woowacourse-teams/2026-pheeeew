package com.pheeeew.emotion.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_EXPIRED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_INVALID_CURSOR;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REQUEST_ID_CONFLICT;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionDetailResult;
import com.pheeeew.emotion.application.dto.EmotionListCursor;
import com.pheeeew.emotion.application.dto.EmotionListResult;
import com.pheeeew.emotion.application.dto.EmotionMapItem;
import com.pheeeew.emotion.application.dto.EmotionMapResult;
import com.pheeeew.emotion.application.dto.EmotionResult;
import com.pheeeew.emotion.application.dto.EmotionSaveResult;
import com.pheeeew.emotion.application.like.dto.EmotionLikeResult;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.emotion.domain.repository.projection.EmotionListProjection;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapProjection;
import com.pheeeew.emotion.domain.repository.query.EmotionQueryPeriod;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionService {

    private static final int MAX_FIND_COUNT = 500;
    private static final int LIST_PAGE_SIZE = 20;
    private static final GeometryFactory WGS84_GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionLocationGenerator emotionLocationGenerator;
    private final EmotionNicknameGenerator emotionNicknameGenerator;
    private final Clock clock;

    public EmotionSaveResult save(UUID requestId, double longitude, double latitude) {
        return saveEmotion(requestId, () -> emotionLocationGenerator.generate(longitude, latitude), null, null, null, false);
    }

    public EmotionSaveResult save(UUID requestId, double longitude, double latitude, String memo, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        return saveEmotion(requestId, () -> emotionLocationGenerator.generate(longitude, latitude), memo, null, deviceId, false);
    }

    public EmotionSaveResult saveAtSelectedLocation(
            UUID requestId, EmotionState state, double longitude, double latitude, String memo, UUID devicePublicId
    ) {
        Objects.requireNonNull(state, "감정 상태는 필수입니다.");
        requireValidCoordinates(longitude, latitude);
        Long deviceId = findDeviceId(devicePublicId);

        return saveEmotion(
                requestId,
                () -> WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude)),
                memo,
                state,
                deviceId,
                true
        );
    }

    @Transactional(readOnly = true)
    public EmotionDetailResult findById(Long id, UUID devicePublicId) {
        Instant queriedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Long deviceId = findDeviceId(devicePublicId);
        EmotionDetailProjection projection = emotionRepository.findById(id, deviceId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_FOUND));

        EmotionQueryPeriod period = EmotionQueryPeriod.of(queriedAt, clock.getZone());
        if (projection.getEmotion().getCreatedAt().isBefore(period.startAt())) {
            throw new EmotionException(EMOTION_EXPIRED);
        }

        return EmotionDetailResult.from(projection);
    }

    public EmotionMapResult findAllWithinBounds(EmotionSearchBounds bounds, Optional<UUID> viewerDevicePublicId) {
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

        List<EmotionMapItem> emotions = projections.stream()
                .map(projection -> EmotionMapItem.of(
                        projection.getId(),
                        projection.getLongitude(),
                        projection.getLatitude(),
                        projection.getCreatedAt()
                ))
                .toList();

        return EmotionMapResult.of(emotions, truncated);
    }

    public EmotionListResult findFirstListPage(EmotionSearchBounds bounds, UUID devicePublicId) {
        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        EmotionListCursor cursor = EmotionListCursor.initial(bounds, snapshotAt);
        EmotionQueryPeriod period = EmotionQueryPeriod.of(snapshotAt, clock.getZone());

        return findList(cursor, period, devicePublicId);
    }

    public EmotionListResult findNextListPage(String encodedCursor, UUID devicePublicId) {
        EmotionListCursor cursor = EmotionListCursorCodec.decode(encodedCursor);
        Instant queriedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (cursor.snapshotAt().isAfter(queriedAt)) {
            throw new EmotionException(EMOTION_INVALID_CURSOR);
        }
        EmotionQueryPeriod period = EmotionQueryPeriod.of(queriedAt, clock.getZone());

        return findList(cursor, period, devicePublicId);
    }

    private EmotionSaveResult saveEmotion(
            UUID requestId, Supplier<Point> location, String memo, EmotionState state, Long deviceId,
            boolean requireSameDevice
    ) {
        Optional<EmotionDetailProjection> existingEmotion = emotionRepository.findByRequestId(requestId, deviceId);

        if (existingEmotion.isPresent()) {
            return createSaveResult(existingEmotion.get(), deviceId, requireSameDevice);
        }

        return saveNewEmotion(requestId, location, memo, state, deviceId, requireSameDevice);
    }

    private EmotionSaveResult createSaveResult(
            EmotionDetailProjection projection, Long deviceId, boolean requireSameDevice
    ) {
        if (requireSameDevice && !Objects.equals(projection.getEmotion().getDeviceId(), deviceId)) {
            throw new EmotionException(EMOTION_REQUEST_ID_CONFLICT);
        }
        EmotionDetailResult detail = EmotionDetailResult.from(projection);
        return EmotionSaveResult.of(detail.emotion(), false, detail.like());
    }

    private EmotionSaveResult saveNewEmotion(
            UUID requestId, Supplier<Point> location, String memo, EmotionState state, Long deviceId,
            boolean requireSameDevice
    ) {
        Emotion emotion = Emotion.builder()
                .requestId(requestId)
                .location(location.get())
                .memo(memo)
                .state(state)
                .nickname(emotionNicknameGenerator.generate())
                .deviceId(deviceId)
                .build();

        try {
            Emotion savedEmotion = emotionRepository.saveAndFlush(emotion);
            return EmotionSaveResult.of(EmotionResult.from(savedEmotion), true, EmotionLikeResult.of(false, 0));
        } catch (DataIntegrityViolationException cause) {
            return findExistingEmotion(requestId, deviceId, requireSameDevice, cause);
        }
    }

    private EmotionSaveResult findExistingEmotion(
            UUID requestId, Long deviceId, boolean requireSameDevice, DataIntegrityViolationException cause
    ) {
        return emotionRepository.findByRequestId(requestId, deviceId)
                .map(projection -> createSaveResult(projection, deviceId, requireSameDevice))
                .orElseThrow(() -> new EmotionException(EMOTION_SAVE_FAILED, cause));
    }

    private Long findDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private void requireValidCoordinates(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("선택 위치는 유효한 WGS84 좌표여야 합니다.");
        }
    }

    private Long findBlockerDeviceId(Optional<UUID> viewerDevicePublicId) {
        return viewerDevicePublicId
                .map(this::findDeviceId)
                .orElse(null);
    }

    private EmotionListResult findList(EmotionListCursor cursor, EmotionQueryPeriod currentPeriod, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        if (!cursor.snapshotAt().isAfter(currentPeriod.startAt())) {
            return EmotionListResult.of(List.of(), false, null);
        }

        EmotionSearchBounds bounds = cursor.bounds();
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

        List<EmotionDetailResult> items = projections.stream()
                .map(EmotionDetailResult::from)
                .toList();
        String nextCursor = createNextCursor(cursor, projections, hasNext);

        return EmotionListResult.of(items, hasNext, nextCursor);
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
