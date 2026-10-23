package com.pheeeew.collection.domain.repository;

import com.pheeeew.collection.domain.CollectionDrawState;
import com.pheeeew.emotion.domain.EmotionState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CollectionDrawStateRepository extends JpaRepository<CollectionDrawState, Long> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO collection_draw_states (device_id, state, cycle, created_at, updated_at)
            VALUES (:deviceId, :#{#state.name()}, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (device_id, state) DO NOTHING
            """, nativeQuery = true)
    void saveIfAbsent(@Param("deviceId") Long deviceId, @Param("state") EmotionState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CollectionDrawState> findByDeviceIdAndState(Long deviceId, EmotionState state);
}
