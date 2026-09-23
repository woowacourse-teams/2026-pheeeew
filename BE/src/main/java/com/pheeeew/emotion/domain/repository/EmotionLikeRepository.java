package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.EmotionLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmotionLikeRepository extends JpaRepository<EmotionLike, Long> {

    Optional<EmotionLike> findByEmotionIdAndDeviceId(Long emotionId, Long deviceId);
}
