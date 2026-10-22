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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "collection_draw_states")
@Entity
public class CollectionDrawState extends BaseEntity {

    public static final int COMBINATION_COUNT = 256;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EmotionState state;

    @Column(nullable = false)
    private long cycle;

    @Column(name = "last_combination_index")
    private Integer lastCombinationIndex;

    @Builder
    private CollectionDrawState(Long deviceId, EmotionState state) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("소유 기기는 필수입니다.");
        }
        this.deviceId = deviceId;
        this.state = Objects.requireNonNull(state);
        this.cycle = 1;
    }

    /** 현재 회차에 저장된 조합을 전달하며, 반환 결과와 변경된 상태는 같은 트랜잭션으로 저장한다. */
    public Draw draw(Set<Integer> usedCombinations, RandomGenerator random) {
        Objects.requireNonNull(random);
        Set<Integer> used = Set.copyOf(usedCombinations);
        if (used.stream().anyMatch(index -> index < 0 || index >= COMBINATION_COUNT)) {
            throw new IllegalArgumentException("사용 조합은 0부터 255까지입니다.");
        }
        boolean newCycle = used.size() == COMBINATION_COUNT;
        long nextCycle = newCycle ? Math.incrementExact(cycle) : cycle;
        List<Integer> candidates = IntStream.range(0, COMBINATION_COUNT)
                .filter(index -> newCycle ? !Objects.equals(index, lastCombinationIndex)
                        : !used.contains(index))
                .boxed()
                .toList();
        int combinationIndex = candidates.get(random.nextInt(candidates.size()));
        cycle = nextCycle;
        lastCombinationIndex = combinationIndex;

        return Draw.of(cycle, combinationIndex);
    }

    public record Draw(long cycle, int combinationIndex) {

        public static Draw of(long cycle, int combinationIndex) {
            return new Draw(cycle, combinationIndex);
        }
    }
}
