package com.pheeeew.climb.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 한 맵의 접속자 자세를 높이순으로 한 번 정렬해 두고, 받는 사람마다 내 높이 위아래 range 칸 안에서
 * 가까운 순서로 limit 명을 고른다. 정렬 한 번 + 사람마다 이분 탐색이라 접속자가 늘어도 가볍다.
 */
public final class ClimbPresence {

    private final List<ClimbPresent> byHeight;

    public ClimbPresence(Collection<ClimbPresent> everyone) {
        this.byHeight = everyone.stream()
                .sorted(Comparator.comparingDouble((ClimbPresent present) -> present.pose().h())
                        .thenComparingInt(ClimbPresent::n))
                .toList();
    }

    public List<ClimbPresent> visibleTo(int n, double h, double range, int limit) {
        int hi = firstAtOrAbove(h);
        int lo = hi - 1;
        List<ClimbPresent> visible = new ArrayList<>();
        while (visible.size() < limit) {
            double below = lo >= 0 ? h - byHeight.get(lo).pose().h() : Double.POSITIVE_INFINITY;
            double above = hi < byHeight.size() ? byHeight.get(hi).pose().h() - h : Double.POSITIVE_INFINITY;
            if (Math.min(below, above) > range) {
                break;
            }
            ClimbPresent next = below <= above ? byHeight.get(lo--) : byHeight.get(hi++);
            if (next.n() != n) {
                visible.add(next);
            }
        }
        return visible;
    }

    private int firstAtOrAbove(double h) {
        int lo = 0;
        int hi = byHeight.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (byHeight.get(mid).pose().h() < h) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
