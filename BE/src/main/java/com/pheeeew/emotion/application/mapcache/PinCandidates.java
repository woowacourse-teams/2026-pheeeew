package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                contribution.stampId());
    }

    private static Instant earlier(Instant first, Instant second) {
        return first == null || second.isBefore(first) ? second : first;
    }

    static final class Ordered {

        private final List<EmotionMapPin> pins;

        private Ordered(List<EmotionMapPin> pins) {
            this.pins = pins;
        }

        Selection select(EmotionCursor cursor, EmotionMapBlocks blocked, int maximum) {
            List<EmotionMapPin> found = new ArrayList<>();
            int scanned = 0;

            for (EmotionMapPin pin : pins) {
                scanned++;
                if (!pin.createdAt().isAfter(cursor.snapshotAt()) && beforeCursor(pin, cursor)
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
