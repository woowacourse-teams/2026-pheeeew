package com.pheeeew.sigh.application;

import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_SAVE_FAILED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.sigh.application.dto.SighListCursor;
import com.pheeeew.sigh.application.dto.SighListResult;
import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.application.dto.SighMapResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.dto.SighSearchBounds;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.domain.repository.projection.SighListProjection;
import com.pheeeew.sigh.domain.repository.projection.SighMapProjection;
import com.pheeeew.sigh.exception.SighException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SighService {

    private static final int MAX_FIND_COUNT = 500;
    private static final int LIST_PAGE_SIZE = 20;

    private final SighRepository sighRepository;
    private final SighLocationGenerator sighLocationGenerator;
    private final SighNicknameGenerator sighNicknameGenerator;

    public SighSaveResult save(UUID requestId, double longitude, double latitude) {
        return save(requestId, longitude, latitude, null);
    }

    public SighSaveResult save(UUID requestId, double longitude, double latitude, String memo) {
        Optional<Sigh> existingSigh = sighRepository.findByRequestId(requestId);

        if (existingSigh.isPresent()) {
            return createSaveResult(existingSigh.get(), false);
        }

        return saveNewSigh(requestId, longitude, latitude, memo);
    }

    public SighResult findById(Long id) {
        return sighRepository.findByIdAndDeletedAtIsNull(id)
                .map(SighResult::from)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
    }

    public SighMapResult findAllWithinBounds(SighSearchBounds bounds) {
        List<SighMapProjection> projections = sighRepository.findAllWithinBounds(
                bounds.minLongitude(),
                bounds.minLatitude(),
                bounds.maxLongitude(),
                bounds.maxLatitude(),
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

    public SighListResult findFirstListPage(SighSearchBounds bounds) {
        Instant snapshotAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        SighListCursor cursor = SighListCursor.initial(bounds, snapshotAt);

        return findList(cursor);
    }

    public SighListResult findNextListPage(String encodedCursor) {
        return findList(SighListCursorCodec.decode(encodedCursor));
    }

    private SighSaveResult createSaveResult(Sigh sigh, boolean created) {
        return SighSaveResult.of(SighResult.from(sigh), created);
    }

    private SighListResult findList(SighListCursor cursor) {
        SighSearchBounds bounds = cursor.bounds();
        List<SighListProjection> projections = sighRepository.findListWithinBounds(
                bounds.minLongitude(),
                bounds.minLatitude(),
                bounds.maxLongitude(),
                bounds.maxLatitude(),
                cursor.snapshotAt(),
                cursor.lastItemCreatedAt(),
                cursor.lastId(),
                MAX_FIND_COUNT,
                LIST_PAGE_SIZE + 1
        );

        boolean hasNext = projections.size() > LIST_PAGE_SIZE;
        if (hasNext) {
            projections = projections.subList(0, LIST_PAGE_SIZE);
        }

        List<SighResult> items = projections.stream()
                .map(projection -> SighResult.of(
                        projection.getId(),
                        projection.getLongitude(),
                        projection.getLatitude(),
                        projection.getCreatedAt(),
                        projection.getMemo(),
                        projection.getNickname()
                ))
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

    private SighSaveResult saveNewSigh(UUID requestId, double longitude, double latitude, String memo) {
        Point location = sighLocationGenerator.generate(longitude, latitude);
        Sigh sigh = Sigh.builder()
                .requestId(requestId)
                .location(location)
                .memo(memo)
                .nickname(sighNicknameGenerator.generate())
                .build();

        try {
            return createSaveResult(sighRepository.saveAndFlush(sigh), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingSigh(requestId, cause);
        }
    }

    private SighSaveResult findExistingSigh(UUID requestId, DataIntegrityViolationException cause) {
        return sighRepository.findByRequestId(requestId)
                .map(sigh -> createSaveResult(sigh, false))
                .orElseThrow(() -> new SighException(SIGH_SAVE_FAILED, cause));
    }
}
