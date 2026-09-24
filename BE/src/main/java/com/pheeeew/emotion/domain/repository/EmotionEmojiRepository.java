package com.pheeeew.emotion.domain.repository;

import com.pheeeew.emotion.domain.EmotionEmoji;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmotionEmojiRepository extends JpaRepository<EmotionEmoji, Long> {
}
