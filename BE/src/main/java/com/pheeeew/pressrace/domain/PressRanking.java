package com.pheeeew.pressrace.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 한 DB 관찰 상태의 공통 순위. 인증 식별자는 개인화에만 쓰고 Member로 노출하지 않는다. */
public final class PressRanking {

    private final LocalDate date;
    private final Instant generatedAt;
    private final int activeCount;
    private final List<Member> members;
    private final List<Member> top100;
    private final Map<UUID, Integer> indices;

    private PressRanking(LocalDate date, Instant generatedAt, List<Participant> participants) {
        this.date = date;
        this.generatedAt = generatedAt;
        // UUID.compareTo는 signed 비교다. 표준 문자열 순서는 PostgreSQL의 unsigned UUID 순서와 같다.
        List<Participant> sorted = participants.stream().sorted(Comparator.comparingLong(Participant::score)
                .reversed().thenComparing(participant -> participant.publicId().toString())).toList();
        List<Member> ranked = new ArrayList<>();
        Map<UUID, Integer> positions = new HashMap<>();
        long previousScore = -1;
        long rank = 0;
        int active = 0;
        for (int index = 0; index < sorted.size(); index++) {
            Participant participant = sorted.get(index);
            if (participant.score() != previousScore) {
                rank = index + 1L;
            }
            previousScore = participant.score();
            positions.put(participant.deviceId(), index);
            ranked.add(Member.of(participant.publicId().toString(), participant.nickname(), participant.score(), rank, true));
            if (participant.lastAppliedAt().plusSeconds(5).isAfter(generatedAt)) {
                active++;
            }
        }
        this.activeCount = active;
        this.members = List.copyOf(ranked);
        this.top100 = this.members.subList(0, Math.min(100, this.members.size()));
        this.indices = Map.copyOf(positions);
    }

    public static PressRanking of(LocalDate date, Instant generatedAt, List<Participant> participants) {
        return new PressRanking(date, generatedAt, participants);
    }

    public Screen forDevice(UUID deviceId, PressView view) {
        Integer index = indices.get(deviceId);
        Member me = index == null ? Member.of(null, null, 0, null, false) : members.get(index);
        int start = index == null ? 0 : Math.max(0, Math.min(index - 1, Math.max(members.size() - 3, 0)));
        int length = index == null ? 2 : 3;
        List<Member> nearby = members.subList(start, Math.min(start + length, members.size()));
        return Screen.of(date, activeCount, view, me, nearby, view == PressView.ALL ? top100 : List.of());
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public record Participant(UUID deviceId, UUID publicId, String nickname, long score, Instant lastAppliedAt) {
        public static Participant of(UUID deviceId, UUID publicId, String nickname, long score, Instant lastAppliedAt) {
            return new Participant(deviceId, publicId, nickname, score, lastAppliedAt);
        }
    }

    public record Member(String publicId, String nickname, long score, Long rank, boolean participating) {
        public static Member of(String publicId, String nickname, long score, Long rank, boolean participating) {
            return new Member(publicId, nickname, score, rank, participating);
        }
    }

    /** equals는 번호·생성 시각을 제외한 화면 내용 비교에 사용한다. */
    public record Screen(LocalDate date, int activeCount, PressView view, Member me,
                         List<Member> nearby, List<Member> top100) {
        public static Screen of(LocalDate date, int activeCount, PressView view, Member me,
                List<Member> nearby, List<Member> top100) {
            return new Screen(date, activeCount, view, me, nearby, top100);
        }
    }
}
