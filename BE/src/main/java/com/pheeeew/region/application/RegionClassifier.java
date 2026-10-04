package com.pheeeew.region.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REGION_DATA_UNAVAILABLE;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.region.domain.repository.RegionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class RegionClassifier {

    private final RegionRepository regionRepository;
    private final Clock clock;

    public Classification classify(Point location) {
        Objects.requireNonNull(location);
        if (location.isEmpty() || location.getSRID() != 4326
                || !Double.isFinite(location.getX()) || location.getX() < -180 || location.getX() > 180
                || !Double.isFinite(location.getY()) || location.getY() < -90 || location.getY() > 90) {
            throw new IllegalArgumentException("분류 위치는 유효한 WGS84 점 좌표여야 합니다.");
        }
        if (!regionRepository.areBoundariesVerified()) {
            throw new EmotionException(EMOTION_REGION_DATA_UNAVAILABLE);
        }
        String code = regionRepository.findEmdCode(location.getX(), location.getY()).orElse(null);
        return Classification.of(code, Instant.now(clock));
    }

    public List<Region> findIntersectingRegions(EmotionSearchBounds bounds, RegionLevel level) {
        if (!regionRepository.areBoundariesVerified()) {
            throw new EmotionException(EMOTION_REGION_DATA_UNAVAILABLE);
        }

        return regionRepository.findIntersectingRegions(bounds, level);
    }

    public record Classification(String regionCode, Instant classifiedAt) {

        public static Classification of(String regionCode, Instant classifiedAt) {
            return new Classification(regionCode, classifiedAt);
        }
    }
}
