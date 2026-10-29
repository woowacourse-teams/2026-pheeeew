package com.pheeeew.collection.fixture;

import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.collection.application.dto.CollectionCardImages;
import com.pheeeew.collection.domain.CollectionDrawState;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.Instant;
import java.time.LocalDate;

public final class CollectionCardFixture {

    private CollectionCardFixture() {
    }

    public static CollectionCardImages 기본_카드_이미지() {
        return CollectionCardImages.of(
                "https://assets.example/v1/characters/angry/01.png",
                "https://assets.example/v1/stamps-round/angry/01.png",
                "https://assets.example/v1/stamps-straight/angry/01.png");
    }

    public static CollectionCard.CollectionCardBuilder 기본_카드_빌더() {
        return CollectionCard.builder()
                .deviceId(1L)
                .cardDate(LocalDate.of(2026, 10, 22))
                .state(EmotionState.ANGRY)
                .cycle(1)
                .combinationIndex(0)
                .cardVersion("v1")
                .claimedAt(Instant.parse("2026-10-22T03:00:00Z"));
    }

    public static CollectionDrawState.CollectionDrawStateBuilder 기본_추첨_상태_빌더() {
        return CollectionDrawState.builder().deviceId(1L).state(EmotionState.ANGRY);
    }
}
