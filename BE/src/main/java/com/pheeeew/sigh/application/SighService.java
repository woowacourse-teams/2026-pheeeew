package com.pheeeew.sigh.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_SAVE_FAILED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.dto.SighDetailResult;
import com.pheeeew.sigh.application.dto.SighListCursor;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.domain.repository.projection.SighDetailProjection;
import com.pheeeew.sigh.domain.repository.projection.SighListProjection;
import com.pheeeew.sigh.domain.repository.projection.SighMapProjection;
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
public class SighService {

    private static final int MAX_FIND_COUNT = 500;
    private static final int LIST_PAGE_SIZE = 20;

    private final SighRepository sighRepository;
    private final DeviceRepository deviceRepository;
    private final SighLocationGenerator sighLocationGenerator;
    private final SighNicknameGenerator sighNicknameGenerator;
    private final Clock clock;

    public SighSaveResult save(UUID requestId, double longitude, double latitude) {
        return saveSigh(requestId, longitude, latitude, null, null);
    }

    public SighSaveResult save(UUID requestId, double longitude, double latitude, String memo, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        return saveSigh(requestId, longitude, latitude, memo, deviceId);
    }

    @Transactional(readOnly = true)
    public SighDetailResult findById(Long id, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        return sighRepository.findById(id, deviceId)
                .map(SighDetailResult::from)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
    }

    public SighMapResult findAllWithinBounds(SighSearchBounds bounds, Optional<UUID> viewerDevicePublicId) {
        Long blockerDeviceId = findBlockerDeviceId(viewerDevicePublicId);
        List<SighMapProjection> projections = sighRepository.findAllWithinBounds(
                bounds.minLongitude(),
                bounds.minLatitude(),
                bounds.maxLongitude(),
                bounds.maxLatitude(),
                blockerDeviceId,
                MAX_FIND_COUNT + 1
        );

        boolean truncated = projections.size() > MAX_FIND_COUNT;
        if (truncated) {
            projections = projections.subList(0, MAX_FIND_COUNT);
        }

        List<SighMapItem> sighs = projections.stream()
                .map(projection -> SighMapItem.of(
                        projection.getId(),
                        projection.getLongitude(),
                        projection.getLatitude(),
                        projection.getCreatedAt()
                ))
                .toList();

        return SighMapResult.of(sighs, truncated);
    }

    public SighListResult findFirstListPage(SighSearchBounds bounds, UUID devicePublicId) {
        Instant snapshotAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        SighListCursor cursor = SighListCursor.initial(bounds, snapshotAt);

        return findList(cursor, devicePublicId);
    }

    public SighListResult findNextListPage(String encodedCursor, UUID devicePublicId) {
        return findList(SighListCursorCodec.decode(encodedCursor), devicePublicId);
    }

    private SighSaveResult saveSigh(UUID requestId, double longitude, double latitude, String memo, Long deviceId) {
        Optional<SighDetailProjection> existingSigh = sighRepository.findByRequestId(requestId, deviceId);

        if (existingSigh.isPresent()) {
            return createSaveResult(existingSigh.get());
        }

        return saveNewSigh(requestId, longitude, latitude, memo, deviceId);
    }

    private SighSaveResult createSaveResult(SighDetailProjection projection) {
        SighDetailResult detail = SighDetailResult.from(projection);
        return SighSaveResult.of(detail.sigh(), false, detail.like());
    }

    private SighSaveResult saveNewSigh(UUID requestId, double longitude, double latitude, String memo, Long deviceId) {
        Point location = sighLocationGenerator.generate(longitude, latitude);
        Sigh sigh = Sigh.builder()
                .requestId(requestId)
                .location(location)
                .memo(memo)
                .nickname(sighNicknameGenerator.generate())
                .deviceId(deviceId)
                .build();

        try {
            Sigh savedSigh = sighRepository.saveAndFlush(sigh);
            return SighSaveResult.of(SighResult.from(savedSigh), true, SighLikeResult.of(false, 0));
        } catch (DataIntegrityViolationException cause) {
            return findExistingSigh(requestId, deviceId, cause);
        }
    }

    private SighSaveResult findExistingSigh(UUID requestId, Long deviceId, DataIntegrityViolationException cause) {
        return sighRepository.findByRequestId(requestId, deviceId)
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

    private SighListResult findList(SighListCursor cursor, UUID devicePublicId) {
        Long deviceId = findDeviceId(devicePublicId);

        SighSearchBounds bounds = cursor.bounds();
        List<SighListProjection> projections = sighRepository.findListWithinBounds(
                bounds.minLongitude(),
                bounds.minLatitude(),
                bounds.maxLongitude(),
                bounds.maxLatitude(),
                cursor.snapshotAt(),
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
            SighListCursor cursor,
            List<SighListProjection> projections,
            boolean hasNext
    ) {
        if (!hasNext) {
            return null;
        }

        SighListProjection lastProjection = projections.getLast();
        SighListCursor nextCursor = cursor.next(
                lastProjection.getCreatedAt(),
                lastProjection.getId()
        );
        return SighListCursorCodec.encode(nextCursor);
    }
}
