package com.pheeeew.collection.domain.repository;

import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.emotion.domain.EmotionState;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionCardRepository extends JpaRepository<CollectionCard, Long> {

    Optional<CollectionCard> findByDeviceIdAndCardDateAndState(Long deviceId, LocalDate cardDate, EmotionState state);

    @Query("""
            SELECT card.combinationIndex FROM CollectionCard card
            WHERE card.deviceId = :deviceId AND card.state = :state AND card.cycle = :cycle
            """)
    List<Integer> findUsedCombinations(@Param("deviceId") Long deviceId, @Param("state") EmotionState state,
            @Param("cycle") long cycle);
}
