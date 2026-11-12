package com.pheeeew.climb.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 순위 규칙: 완주자(걸린 시간 오름차순) > 최고 높이 내림차순 > 최초 도달 시각 오름차순 > 손님 ID. */
public final class ClimbRanking {

    public static final Comparator<ClimbRankEntry> ORDER = Comparator
            .comparing((ClimbRankEntry entry) -> !entry.finished())
            .thenComparing(entry -> entry.finished() ? entry.finishSeconds() : 0)
            .thenComparing(ClimbRankEntry::best, Comparator.reverseOrder())
            .thenComparing(ClimbRankEntry::bestAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(ClimbRankEntry::guestId);

    private ClimbRanking() {
    }

    /** 손님마다 가장 좋은 등반 하나만 남겨 정렬한다. */
    public static List<ClimbRankEntry> sort(Collection<ClimbRankEntry> entries) {
        Map<UUID, ClimbRankEntry> best = new HashMap<>();
        for (ClimbRankEntry entry : entries) {
            best.merge(entry.guestId(), entry, (left, right) -> ORDER.compare(left, right) <= 0 ? left : right);
        }
        List<ClimbRankEntry> sorted = new ArrayList<>(best.values());
        sorted.sort(ORDER);
        return sorted;
    }

    public static ClimbRankView view(List<ClimbRankEntry> sorted, UUID guestId, int topSize) {
        List<ClimbRankView.Ranked> top = new ArrayList<>();
        for (int index = 0; index < Math.min(topSize, sorted.size()); index++) {
            top.add(ClimbRankView.Ranked.of(index + 1, sorted.get(index)));
        }
        int mine = indexOf(sorted, guestId);
        if (mine < 0) {
            return ClimbRankView.of(top, null, List.of());
        }
        List<ClimbRankView.Ranked> near = new ArrayList<>();
        for (int index = Math.max(0, mine - 1); index <= Math.min(sorted.size() - 1, mine + 1); index++) {
            near.add(ClimbRankView.Ranked.of(index + 1, sorted.get(index)));
        }
        return ClimbRankView.of(top, ClimbRankView.Ranked.of(mine + 1, sorted.get(mine)), near);
    }

    private static int indexOf(List<ClimbRankEntry> sorted, UUID guestId) {
        if (guestId == null) {
            return -1;
        }
        for (int index = 0; index < sorted.size(); index++) {
            if (sorted.get(index).guestId().equals(guestId)) {
                return index;
            }
        }
        return -1;
    }
}
