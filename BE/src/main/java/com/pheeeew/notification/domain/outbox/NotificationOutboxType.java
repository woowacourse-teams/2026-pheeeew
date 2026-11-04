package com.pheeeew.notification.domain.outbox;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationOutboxType {
    COLLECTION_CLAIMABLE(
            "컬렉션 발급 조건 달성!",
            "오늘의 감정 카드를 받으세요.\n카드 받으러 가기👉",
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))
    ),
    COLLECTION_CLAIM_REMINDER(
            "아직 받지 않은 카드가 있어요",
            "오늘의 카드는 자정이 지나면 받을 수 없어요.\n카드 받으러 가기👉",
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))
    ),
    RANK_DROPPED(
            "앗, 프레스 순위가 내려갔어요👀",
            "오늘 내 순위 %d위 → %d위!\n쌓인 마음으로 순위를 올리러 갈까요?",
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(2))
    );

    private final String title;
    private final String body;
    private final List<Duration> retryDelays;

    public Optional<Duration> retryDelayAfter(int attemptCount) {
        if (attemptCount < 1 || attemptCount > retryDelays.size()) {
            return Optional.empty();
        }
        return Optional.of(retryDelays.get(attemptCount - 1));
    }

    public int maxAttempts() {
        return retryDelays.size() + 1;
    }

    public boolean isCollection() {
        return this == COLLECTION_CLAIMABLE || this == COLLECTION_CLAIM_REMINDER;
    }
}
