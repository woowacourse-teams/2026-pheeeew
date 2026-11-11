package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.region.domain.RegionLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class PinCandidates {

    private static final Comparator<EmotionMapPin> NEWEST_FIRST = Comparator.comparing(EmotionMapPin::createdAt)
            .thenComparing(EmotionMapPin::id).reversed();

    private final Map<Long, EmotionMapPin> byId;
    private final Instant nextActivation;

    private PinCandidates(Map<Long, EmotionMapPin> byId, Instant nextActivation) {
        this.byId = byId;
        this.nextActivation = nextActivation;
    }

    static PinCandidates of(List<EmotionMapPin> rows, Instant nextActivation) {
        Map<Long, EmotionMapPin> pins = new HashMap<>();
        rows.forEach(pin -> pins.put(pin.id(), pin));

        return new PinCandidates(Map.copyOf(pins), nextActivation);
    }

    /** maximum을 초과하면 새 후보 집합을 보관하지 않고 null을 반환한다. */
    PinCandidates applyChange(EmotionMapContribution before, EmotionMapContribution after, Instant now, int maximum) {
        Map<Long, EmotionMapPin> pins = new HashMap<>(byId);
        if (before != null) {
            pins.remove(before.id());
        }

        Instant nextActivation = this.nextActivation;
        if (after != null && after.contributesAt(now)) {
            pins.put(after.id(), pinFrom(after));
        } else if (after != null && !after.deleted() && after.hasContent() && after.createdAt().isAfter(now)) {
            nextActivation = earlier(nextActivation, after.createdAt());
        }

        if (pins.size() > maximum) {
            return null;
        }

        return new PinCandidates(Map.copyOf(pins), nextActivation);
    }

    Ordered ordered() {
        return new Ordered(byId.values().stream().sorted(NEWEST_FIRST).toList());
    }

    int size() {
        return byId.size();
    }

    Instant nextActivation() {
        return nextActivation;
    }

    private static EmotionMapPin pinFrom(EmotionMapContribution contribution) {
        return EmotionMapPin.of(contribution.id(), contribution.deviceId(), contribution.createdAt(),
                contribution.longitude(), contribution.latitude(), contribution.state(), contribution.rotationDegrees(),
                contribution.stampId(), contribution.audio(), contribution.regionCode());
    }

    private static Instant earlier(Instant first, Instant second) {
        return first == null || second.isBefore(first) ? second : first;
    }

    static final class Ordered {

        private final List<EmotionMapPin> pins;
        /** 작성 기기별 최신순 핀. MY 조회가 전체 후보를 훑지 않게 한다. */
        private final Map<Long, List<EmotionMapPin>> byDevice;

        private Ordered(List<EmotionMapPin> pins) {
            this.pins = pins;
            this.byDevice = pins.stream().filter(pin -> pin.deviceId() != null)
                    .collect(Collectors.groupingBy(EmotionMapPin::deviceId, Collectors.toUnmodifiableList()));
        }

        Selection select(EmotionCursor cursor, Long requester, EmotionMapBlocks blocked, int maximum) {
            List<EmotionMapPin> found = new ArrayList<>();
            EmotionContentFilter contentType = cursor.filter().contentType();
            Instant startInclusive = cursor.filter().startInclusive();
            Instant endExclusive = cursor.filter().endExclusive();
            int scanned = 0;
            List<EmotionMapPin> source = cursor.filter().scope() == EmotionSearchScope.MY
                    ? byDevice.getOrDefault(requester, List.of()) : pins;

            for (EmotionMapPin pin : source) {
                if (startInclusive != null && pin.createdAt().isBefore(startInclusive)) {
                    break;
                }

                scanned++;
                if (!pin.createdAt().isAfter(cursor.snapshotAt()) && beforeCursor(pin, cursor)
                        && (endExclusive == null || pin.createdAt().isBefore(endExclusive))
                        && contentType.includes(pin.audio())
                        && within(pin, cursor.bounds()) && !blocked.emotions().contains(pin.id())
                        && (pin.deviceId() == null || !blocked.authors().contains(pin.deviceId()))) {
                    found.add(pin);
                    if (found.size() == maximum) {
                        break;
                    }
                }
            }

            return Selection.of(found, scanned);
        }

        /** 지역 요약 SQL과 같이 차단은 반영하지 않는다. */
        Map<RegionLevel, Map<String, RegionEmotionSummary>> regionSummaries(Long requester, EmotionSearchFilter filter) {
            Map<String, Map<EmotionState, Long>> counts = new HashMap<>();
            Instant startInclusive = filter.startInclusive();
            Instant endExclusive = filter.endExclusive();

            for (EmotionMapPin pin : byDevice.getOrDefault(requester, List.of())) {
                if (startInclusive != null && pin.createdAt().isBefore(startInclusive)) {
                    break;
                }
                if ((endExclusive == null || pin.createdAt().isBefore(endExclusive))
                        && filter.contentType().includes(pin.audio())) {
                    RegionAggregate.add(counts, pin.regionCode().substring(0, 5), pin.state(), 1);
                }
            }

            return RegionAggregate.summariesOf(counts);
        }

        private boolean beforeCursor(EmotionMapPin pin, EmotionCursor cursor) {
            int compared = pin.createdAt().compareTo(cursor.lastItemCreatedAt());
            return compared < 0 || compared == 0 && pin.id() < cursor.lastId();
        }

        private boolean within(EmotionMapPin pin, EmotionSearchBounds bounds) {
            boolean longitude = bounds.minLongitude() < bounds.maxLongitude()
                    ? pin.longitude() >= bounds.minLongitude() && pin.longitude() <= bounds.maxLongitude()
                    : pin.longitude() >= bounds.minLongitude() || pin.longitude() <= bounds.maxLongitude();

            return longitude && pin.latitude() >= bounds.minLatitude() && pin.latitude() <= bounds.maxLatitude();
        }
    }

    record Selection(List<EmotionMapPin> pins, int scanned) {

        private static Selection of(List<EmotionMapPin> pins, int scanned) {
            return new Selection(List.copyOf(pins), scanned);
        }
    }
}
