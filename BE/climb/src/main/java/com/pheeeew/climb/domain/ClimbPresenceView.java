package com.pheeeew.climb.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 받는 사람 한 명이 지금까지 받은 것. 이번에 보이는 사람들과 비교해 들어옴·나감·새 상태만 골라 낸다. */
public final class ClimbPresenceView {

    private final Map<Integer, Long> sent = new HashMap<>();

    public Delta next(List<ClimbPresent> visible) {
        Set<Integer> now = new HashSet<>();
        visible.forEach(present -> now.add(present.n()));
        List<Integer> left = sent.keySet().stream().filter(n -> !now.contains(n)).sorted().toList();
        left.forEach(sent::remove);
        List<Integer> entered = new ArrayList<>();
        List<ClimbPresent> changed = new ArrayList<>();
        for (ClimbPresent present : visible) {
            Long previous = sent.put(present.n(), present.seq());
            if (previous == null) {
                entered.add(present.n());
            }
            if (previous == null || previous != present.seq()) {
                changed.add(present);
            }
        }
        return new Delta(entered, left, changed);
    }

    public record Delta(List<Integer> entered, List<Integer> left, List<ClimbPresent> changed) {

        public boolean isEmpty() {
            return entered.isEmpty() && left.isEmpty() && changed.isEmpty();
        }
    }
}
