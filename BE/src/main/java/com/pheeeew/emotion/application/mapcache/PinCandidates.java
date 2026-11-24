package com.pheeeew.emotion.application.mapcache;

import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.EmotionMapClusterView;
import com.pheeeew.emotion.application.dto.RegionEmotionSummary;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapBlocks;
import com.pheeeew.emotion.domain.repository.projection.EmotionMapPin;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.region.domain.RegionLevel;
import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class PinCandidates {

    /** 핀마다 보관하는 가장 세밀한 셀 해상도(변 약 0.2km). 묶음은 이 셀의 상위 셀로 만든다. */
    private static final int CELL_RESOLUTION = 9;
    private static final H3Core H3 = h3();
    private static final Comparator<Candidate> NEWEST_FIRST = Comparator
            .comparing((Candidate candidate) -> candidate.pin().createdAt())
            .thenComparing(candidate -> candidate.pin().id()).reversed();

    private final Map<Long, Candidate> byId;
    private final Instant nextActivation;

    private PinCandidates(Map<Long, Candidate> byId, Instant nextActivation) {
        this.byId = byId;
        this.nextActivation = nextActivation;
    }

    static PinCandidates of(List<EmotionMapPin> rows, Instant nextActivation) {
        Map<Long, Candidate> pins = new HashMap<>();
        rows.forEach(pin -> pins.put(pin.id(), Candidate.of(pin)));

        return new PinCandidates(Map.copyOf(pins), nextActivation);
    }

    /** maximum을 초과하면 새 후보 집합을 보관하지 않고 null을 반환한다. */
    PinCandidates applyChange(EmotionMapContribution before, EmotionMapContribution after, Instant now, int maximum) {
        Map<Long, Candidate> pins = new HashMap<>(byId);
        if (before != null) {
            pins.remove(before.id());
        }

        Instant nextActivation = this.nextActivation;
        if (after != null && after.contributesAt(now)) {
            pins.put(after.id(), Candidate.of(pinFrom(after)));
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

    private static H3Core h3() {
        try {
            return H3Core.newInstance();
        } catch (IOException failure) {
            throw new UncheckedIOException("H3 네이티브 라이브러리를 불러오지 못했습니다.", failure);
        }
    }

    /**
     * 좌표를 셀로 바꾸는 비용(10만 개에 약 34ms)을 조회마다 내지 않도록 핀이 들어올 때 한 번만 계산해 둔다.
     */
    private record Candidate(EmotionMapPin pin, long cell) {

        private static Candidate of(EmotionMapPin pin) {
            return new Candidate(pin, H3.latLngToCell(pin.latitude(), pin.longitude(), CELL_RESOLUTION));
        }
    }

    static final class Ordered {

        private final List<Candidate> pins;
        /** 작성 기기별 최신순 핀. MY 조회가 전체 후보를 훑지 않게 한다. */
        private final Map<Long, List<Candidate>> byDevice;

        private Ordered(List<Candidate> pins) {
            this.pins = pins;
            this.byDevice = pins.stream().filter(candidate -> candidate.pin().deviceId() != null)
                    .collect(Collectors.groupingBy(candidate -> candidate.pin().deviceId(),
                            Collectors.toUnmodifiableList()));
        }

        Selection select(EmotionCursor cursor, Long requester, EmotionMapBlocks blocked, int maximum) {
            List<EmotionMapPin> found = new ArrayList<>();
            Instant startInclusive = cursor.filter().startInclusive();
            int scanned = 0;

            for (Candidate candidate : source(cursor, requester)) {
                EmotionMapPin pin = candidate.pin();
                if (startInclusive != null && pin.createdAt().isBefore(startInclusive)) {
                    break;
                }

                scanned++;
                if (visible(pin, cursor, blocked) && beforeCursor(pin, cursor)) {
                    found.add(pin);
                    if (found.size() == maximum) {
                        break;
                    }
                }
            }

            return Selection.of(found, scanned);
        }

        /** 화면 안 핀을 요청한 해상도의 H3 셀로 묶는다. 선택 조건은 핀 첫 페이지 조회와 같다. */
        List<EmotionMapClusterView> clusters(EmotionCursor cursor, Long requester, EmotionMapBlocks blocked,
                int resolution) {
            if (resolution < 0 || resolution > CELL_RESOLUTION) {
                throw new IllegalArgumentException("묶음 해상도는 0 이상 " + CELL_RESOLUTION + " 이하여야 합니다.");
            }

            Map<Long, Map<EmotionState, Long>> counts = new HashMap<>();
            Instant startInclusive = cursor.filter().startInclusive();

            for (Candidate candidate : source(cursor, requester)) {
                EmotionMapPin pin = candidate.pin();
                if (startInclusive != null && pin.createdAt().isBefore(startInclusive)) {
                    break;
                }

                if (visible(pin, cursor, blocked)) {
                    // 육각형은 상위 셀에 정확히 포개지지 않는다. 좌표를 다시 변환하지 않고 상위 셀로 올려야
                    // 해상도가 달라져도 개수가 맞는다.
                    counts.computeIfAbsent(H3.cellToParent(candidate.cell(), resolution), ignored -> new HashMap<>())
                            .merge(pin.state(), 1L, Long::sum);
                }
            }

            return counts.entrySet().stream()
                    .map(entry -> clusterOf(entry.getKey(), RegionAggregate.summaryOf(entry.getValue())))
                    .sorted(Comparator.comparingLong(EmotionMapClusterView::count).reversed()
                            .thenComparing(EmotionMapClusterView::cell)).toList();
        }

        private EmotionMapClusterView clusterOf(long cell, RegionEmotionSummary summary) {
            LatLng center = H3.cellToLatLng(cell);

            return EmotionMapClusterView.of(H3.h3ToString(cell), center.lng, center.lat, summary.totalCount(),
                    summary.representativeState());
        }

        /** 지역 요약 SQL과 같이 차단은 반영하지 않는다. */
        Map<RegionLevel, Map<String, RegionEmotionSummary>> regionSummaries(Long requester, EmotionSearchFilter filter) {
            Map<String, Map<EmotionState, Long>> counts = new HashMap<>();
            Instant startInclusive = filter.startInclusive();
            Instant endExclusive = filter.endExclusive();

            for (Candidate candidate : byDevice.getOrDefault(requester, List.of())) {
                EmotionMapPin pin = candidate.pin();
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

        private List<Candidate> source(EmotionCursor cursor, Long requester) {
            return cursor.filter().scope() == EmotionSearchScope.MY ? byDevice.getOrDefault(requester, List.of()) : pins;
        }

        /** 기간 하한과 페이지 커서를 뺀 선택 조건. 핀 조회와 묶음이 같은 핀을 대상으로 삼게 한다. */
        private boolean visible(EmotionMapPin pin, EmotionCursor cursor, EmotionMapBlocks blocked) {
            EmotionContentFilter contentType = cursor.filter().contentType();
            Instant endExclusive = cursor.filter().endExclusive();

            return !pin.createdAt().isAfter(cursor.snapshotAt())
                    && (endExclusive == null || pin.createdAt().isBefore(endExclusive))
                    && contentType.includes(pin.audio())
                    && within(pin, cursor.bounds()) && !blocked.emotions().contains(pin.id())
                    && (pin.deviceId() == null || !blocked.authors().contains(pin.deviceId()));
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
