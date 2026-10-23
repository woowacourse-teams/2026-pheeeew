package com.pheeeew.collection.domain;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.emotion.domain.EmotionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "collection_cards")
@Entity
public class CollectionCard extends BaseEntity {

    private static final int MAX_VERSION_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "card_date", nullable = false, updatable = false)
    private LocalDate cardDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EmotionState state;

    @Column(nullable = false, updatable = false)
    private long cycle;

    @Column(name = "combination_index", nullable = false, updatable = false)
    private int combinationIndex;

    @Column(name = "card_version", nullable = false, updatable = false, length = MAX_VERSION_LENGTH)
    private String cardVersion;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Builder
    private CollectionCard(Long deviceId, LocalDate cardDate, EmotionState state, long cycle, int combinationIndex,
            String cardVersion, Instant claimedAt) {
        if (deviceId == null || deviceId <= 0 || cycle <= 0) {
            throw new IllegalArgumentException("소유 기기와 회차는 양수여야 합니다.");
        }
        if (combinationIndex < 0 || combinationIndex >= CollectionDrawState.COMBINATION_COUNT) {
            throw new IllegalArgumentException("카드 조합은 0부터 255까지입니다.");
        }
        this.publicId = UUID.randomUUID();
        this.deviceId = deviceId;
        this.cardDate = Objects.requireNonNull(cardDate);
        this.state = Objects.requireNonNull(state);
        this.cycle = cycle;
        this.combinationIndex = combinationIndex;
        this.cardVersion = requireVersion(cardVersion);
        this.claimedAt = Objects.requireNonNull(claimedAt);
    }

    private String requireVersion(String version) {
        if (version == null || version.isBlank() || version.length() > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("카드 버전은 1자 이상 64자 이하여야 합니다.");
        }
        return version;
    }
}
